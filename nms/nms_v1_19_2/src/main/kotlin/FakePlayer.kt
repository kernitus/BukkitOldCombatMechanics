package kernitus.plugin.OldCombatMechanics.tester

import com.mojang.authlib.GameProfile
import com.mojang.datafixers.util.Pair
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.embedded.EmbeddedChannel
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector
import net.minecraft.network.Connection
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.PacketFlow
import net.minecraft.network.protocol.game.ClientboundAddPlayerPacket
import net.minecraft.network.protocol.game.ClientboundPlayerInfoPacket
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.ServerGamePacketListenerImpl
import net.minecraft.server.players.PlayerList
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.GameType
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.craftbukkit.v1_19_R1.CraftServer
import org.bukkit.craftbukkit.v1_19_R1.CraftWorld
import org.bukkit.craftbukkit.v1_19_R1.entity.CraftLivingEntity
import org.bukkit.craftbukkit.v1_19_R1.entity.CraftPlayer
import org.bukkit.craftbukkit.v1_19_R1.inventory.CraftItemStack
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.event.player.AsyncPlayerPreLoginEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import xyz.jpenilla.reflectionremapper.ReflectionRemapper
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.*

/*
    Resources

    - FakePlayers plugin (1.19 version, had some wrong method names to fix and some things not re-implemented):
    https://github.com/gabrianrchua/Fake-Players-Spigot-Plugin/blob/b88b4c33dcf73d9a9a36f6a202b5273712055751/src/main/java/me/KP56/FakePlayers/MultiVersion/v1_19_R1.java

    - Guide on making NMS bots, for a few small tweaks
    https://www.spigotmc.org/threads/nms-serverplayer-entityplayer-for-the-1-17-1-18-mojang-mappings-with-fall-damage-and-knockback.551281/

    - NMS mappings for checking Mojang / Spigot / Obfuscated class, field, and method names
    https://mappings.cephx.dev/
 */
class FakePlayer {
    val uuid: java.util.UUID = java.util.UUID.randomUUID()
    val name: String = uuid.toString().substring(0, 16)
    var serverPlayer: ServerPlayer? = null
        private set
    private var bukkitPlayer: Player? = null
    private var tickTaskId: Int? = null

    fun spawn(location: Location) {
        val worldServer = (location.world as CraftWorld).handle
        val mcServer: MinecraftServer = (Bukkit.getServer() as CraftServer).server

        val gameProfile = GameProfile(uuid, name)
        this.serverPlayer = ServerPlayer(mcServer, worldServer, gameProfile, null)

        serverPlayer!!.connection = ServerGamePacketListenerImpl(
            mcServer, Connection(PacketFlow.CLIENTBOUND),
            serverPlayer
        )
        serverPlayer!!.connection.connection.channel = EmbeddedChannel(ChannelInboundHandlerAdapter())
        serverPlayer!!.connection.connection.channel.close()

        serverPlayer!!.setGameMode(GameType.SURVIVAL)

        serverPlayer!!.setPos(location.x, location.y, location.z)
        serverPlayer!!.xRot = 0f
        serverPlayer!!.yRot = 0f

        try {
            val ipAddress = InetAddress.getByName("127.0.0.1")
            val asyncPreLoginEvent = AsyncPlayerPreLoginEvent(name, ipAddress, uuid)
            Thread { Bukkit.getPluginManager().callEvent(asyncPreLoginEvent) }.start()
        } catch (e: UnknownHostException) {
            e.printStackTrace()
        }

        // TODO playerloginevent might need to get called separately
        //PlayerLoginEvent playerLoginEvent = new PlayerLoginEvent((Player) entityPlayer, "hostname", ipAddress, ipAddress);
        val playerList = mcServer.playerList
        playerList.load(serverPlayer)
        serverPlayer!!.spawnIn(worldServer)
        playerList.getPlayers().add(serverPlayer)

        // Get private playerByUUID Map from PlayerList class and add player to it
        // private final Map<UUID, EntityPlayer> playersByUUID = Maps.newHashMap();
        val playersByUUIDField = Reflector.getMapFieldWithTypes(
            PlayerList::class.java,
            java.util.UUID::class.java,
            ServerPlayer::class.java
        )
        val playerByUUID = Reflector.getFieldValue(playersByUUIDField, playerList) as MutableMap<UUID, ServerPlayer>
        playerByUUID[uuid] = serverPlayer!!

        bukkitPlayer = Bukkit.getPlayer(uuid)

        if (bukkitPlayer == null) throw RuntimeException("Bukkit player with UUID $uuid not found!")

        val joinMessage = "§e" + serverPlayer!!.displayName + " joined the game"
        val playerJoinEvent = PlayerJoinEvent(
            bukkitPlayer!!, net.kyori.adventure.text.Component.text(joinMessage)
        )
        Bukkit.getPluginManager().callEvent(playerJoinEvent)

        // Let other client know player is there
        sendPacket(ClientboundPlayerInfoPacket(ClientboundPlayerInfoPacket.Action.ADD_PLAYER, serverPlayer))

        worldServer.addNewPlayer(serverPlayer)

        // Send world info to player client
        playerList.sendLevelInfo(serverPlayer, worldServer)
        serverPlayer!!.sendServerStatus(playerList.server.status)

        // Spawn the player for the client
        sendPacket(ClientboundAddPlayerPacket(serverPlayer))

        //TODO reenable
        /*
        tickTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(
            OCMMain.instance,
            { serverPlayer!!.tick() }, 1, 1
        )

         */
    }

    fun removePlayer() {
        if (tickTaskId != null) Bukkit.getScheduler().cancelTask(tickTaskId!!)
        tickTaskId = null

        val mcServer: MinecraftServer = (Bukkit.getServer() as CraftServer).server

        val quitMessage: net.kyori.adventure.text.Component =
            net.kyori.adventure.text.Component.text("§e" + serverPlayer!!.displayName + " left the game")
        val playerQuitEvent = PlayerQuitEvent(
            bukkitPlayer!!, quitMessage, PlayerQuitEvent.QuitReason.DISCONNECTED
        )
        Bukkit.getPluginManager().callEvent(playerQuitEvent)

        serverPlayer!!.bukkitEntity.disconnect(quitMessage.toString())

        val playerList = mcServer.playerList
        playerList.remove(serverPlayer)
    }

    fun attack(bukkitEntity: Entity) {
        bukkitPlayer!!.attack(bukkitEntity)
    }

    fun updateEquipment(slot: EquipmentSlot, item: org.bukkit.inventory.ItemStack?) {
        //todo try directly accessing the player inventory
        // otherwise just set the attack value attribute instead
        // could check Citizen's code to see how they give weapons
        // might also just need to wait a tick

        //final ServerLevel worldServer = entityPlayer.x(); // entityPlayer.getWorld().getWorld().getHandle();
        // worldServer.broadcastEntityEvent(Entity, byte)

        //Defining the list of Pairs with EquipmentSlot and (NMS) ItemStack

        val equipmentList: MutableList<Pair<EquipmentSlot, ItemStack>> = arrayListOf()
        equipmentList.add(Pair(slot, CraftItemStack.asNMSCopy(item)))

        //Creating the packet
        val entityEquipment = ClientboundSetEquipmentPacket(bukkitPlayer!!.entityId, equipmentList)
        sendPacket(entityEquipment)
        //         ((ServerLevel) this.level).getChunkSource().broadcast(this, new PacketPlayOutEntityEquipment(this.getId(), list));
    }

    private fun sendPacket(packet: Packet<*>) {
        Bukkit.getOnlinePlayers().stream()
            .map { p: Player? -> (p as CraftPlayer).handle.connection }
            .forEach { connection: ServerGamePacketListenerImpl -> connection.send(packet) }
    }

    /**
     * Make this player block with a shield
     */
    fun doBlocking() {
        bukkitPlayer!!.inventory.setItemInMainHand(org.bukkit.inventory.ItemStack(Material.SHIELD))

        val entityLiving = (bukkitPlayer as CraftLivingEntity).handle
        entityLiving.startUsingItem(InteractionHand.MAIN_HAND)
        // getUseDuration of SHIELD is 72000
        // For isBlocking to be true, useDuration - getUseItemRemainingTicks() must be >= 5
        // Which means we have to wait at least 5 ticks before user is actually blocking
        // Here we just set it manually
        val reflectionRemapper = ReflectionRemapper.forReobfMappingsInPaperJar()
        val remapped = reflectionRemapper.remapFieldName(LivingEntity::class.java, "useItemRemaining")
        val useItemRemainingField = Reflector.getField(
            LivingEntity::class.java, remapped
        )
        Reflector.setFieldValue(useItemRemainingField, entityLiving, 200)
    }
}