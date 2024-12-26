/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.tester

import com.mojang.authlib.GameProfile
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector
import org.bukkit.Location
import org.bukkit.entity.Entity
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
    val uuid: UUID = UUID.randomUUID()
    val name: String = uuid.toString().substring(0, 16)
    private var entityPlayer: ServerPlayer? = null
    private var bukkitPlayer: Player? = null
    private var tickTaskId: Int? = null

    val serverPlayer: ServerPlayer?
        get() = entityPlayer

    fun spawn(location: Location) {
        val worldServer: ServerLevel = (location.world as CraftWorld).getHandle()
        val mcServer: MinecraftServer = (Bukkit.getServer() as CraftServer).getServer()

        val gameProfile: GameProfile = GameProfile(uuid, name)
        this.entityPlayer = ServerPlayer(mcServer, worldServer, gameProfile, null)

        entityPlayer.connection =
            ServerGamePacketListenerImpl(mcServer, Connection(PacketFlow.CLIENTBOUND), entityPlayer)
        entityPlayer.connection.connection.channel = EmbeddedChannel(ChannelInboundHandlerAdapter())
        entityPlayer.connection.connection.channel.close()

        entityPlayer.setGameMode(GameType.SURVIVAL)

        entityPlayer.setPos(location.x, location.y, location.z)
        entityPlayer.setXRot(0)
        entityPlayer.setYRot(0)

        try {
            val ipAddress = InetAddress.getByName("127.0.0.1")
            val asyncPreLoginEvent: AsyncPlayerPreLoginEvent = AsyncPlayerPreLoginEvent(name, ipAddress, uuid)
            Thread { Bukkit.getPluginManager().callEvent(asyncPreLoginEvent) }.start()
        } catch (e: UnknownHostException) {
            e.printStackTrace()
        }

        // TODO playerloginevent might need to get called separately
        //PlayerLoginEvent playerLoginEvent = new PlayerLoginEvent((Player) entityPlayer, "hostname", ipAddress, ipAddress);
        val playerList: PlayerList = mcServer.getPlayerList()
        playerList.load(entityPlayer)
        entityPlayer.spawnIn(worldServer)
        playerList.getPlayers().add(entityPlayer)

        // Get private playerByUUID Map from PlayerList class and add player to it
        // private final Map<UUID, EntityPlayer> playersByUUID = Maps.newHashMap();
        val playersByUUIDField = Reflector.getMapFieldWithTypes(
            PlayerList::class.java,
            UUID::class.java,
            ServerPlayer::class.java
        )
        val playerByUUID: MutableMap<UUID, ServerPlayer?> =
            Reflector.getFieldValue(playersByUUIDField, playerList) as MutableMap<UUID, ServerPlayer?>
        playerByUUID[uuid] = entityPlayer

        bukkitPlayer = Bukkit.getPlayer(uuid)

        if (bukkitPlayer == null) throw RuntimeException("Bukkit player with UUID $uuid not found!")

        val joinMessage = "§e" + entityPlayer.displayName + " joined the game"
        val playerJoinEvent: PlayerJoinEvent =
            PlayerJoinEvent(bukkitPlayer, net.kyori.adventure.text.Component.text(joinMessage))
        Bukkit.getPluginManager().callEvent(playerJoinEvent)

        // Let other client know player is there
        sendPacket(ClientboundPlayerInfoPacket(ClientboundPlayerInfoPacket.Action.ADD_PLAYER, entityPlayer))

        worldServer.addNewPlayer(entityPlayer)

        // Send world info to player client
        playerList.sendLevelInfo(entityPlayer, worldServer)
        entityPlayer.sendServerStatus(playerList.getServer().getStatus())

        // Spawn the player for the client
        sendPacket(ClientboundAddPlayerPacket(entityPlayer))

        tickTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(OCMMain.getInstance(), entityPlayer::tick, 1, 1)
    }

    fun removePlayer() {
        if (tickTaskId != null) Bukkit.getScheduler().cancelTask(tickTaskId)
        tickTaskId = null

        val mcServer: MinecraftServer = (Bukkit.getServer() as CraftServer).getServer()

        val quitMessage: net.kyori.adventure.text.Component =
            net.kyori.adventure.text.Component.text("§e" + entityPlayer.displayName + " left the game")
        val playerQuitEvent: PlayerQuitEvent =
            PlayerQuitEvent(bukkitPlayer, quitMessage, PlayerQuitEvent.QuitReason.DISCONNECTED)
        Bukkit.getPluginManager().callEvent(playerQuitEvent)

        entityPlayer.getBukkitEntity().disconnect(quitMessage.toString())

        val playerList: PlayerList = mcServer.getPlayerList()
        playerList.remove(entityPlayer)
    }

    fun attack(bukkitEntity: Entity) {
        bukkitPlayer.attack(bukkitEntity)
    }

    fun updateEquipment(slot: EquipmentSlot?, item: ItemStack?) {
        //todo try directly accessing the player inventory
        // otherwise just set the attack value attribute instead
        // could check Citizen's code to see how they give weapons
        // might also just need to wait a tick

        //final ServerLevel worldServer = entityPlayer.x(); // entityPlayer.getWorld().getWorld().getHandle();
        // worldServer.broadcastEntityEvent(Entity, byte)

        //Defining the list of Pairs with EquipmentSlot and (NMS) ItemStack

        val equipmentList: MutableList<Pair<EquipmentSlot, ItemStack>> = ArrayList<Pair<EquipmentSlot, ItemStack>>()
        equipmentList.add(Pair(slot, CraftItemStack.asNMSCopy(item)))

        //Creating the packet
        val entityEquipment: ClientboundSetEquipmentPacket =
            ClientboundSetEquipmentPacket(bukkitPlayer.getEntityId(), equipmentList)
        sendPacket(entityEquipment)
        //         ((ServerLevel) this.level).getChunkSource().broadcast(this, new PacketPlayOutEntityEquipment(this.getId(), list));
    }

    private fun sendPacket(packet: Packet) {
        Bukkit.getOnlinePlayers().stream()
            .map<Any> { p: Player? -> (p as CraftPlayer).getHandle().connection }
            .forEach { connection: Any -> connection.send(packet) }
    }

    /**
     * Make this player block with a shield
     */
    fun doBlocking() {
        bukkitPlayer.getInventory().setItemInMainHand(ItemStack(Material.SHIELD))

        val entityLiving: LivingEntity = (bukkitPlayer as CraftLivingEntity).getHandle()
        entityLiving.startUsingItem(InteractionHand.MAIN_HAND)
        // getUseDuration of SHIELD is 72000
        // For isBlocking to be true, useDuration - getUseItemRemainingTicks() must be >= 5
        // Which means we have to wait at least 5 ticks before user is actually blocking
        // Here we just set it manually
        val reflectionRemapper: ReflectionRemapper = ReflectionRemapper.forReobfMappingsInPaperJar()
        val remapped: String = reflectionRemapper.remapFieldName(LivingEntity::class.java, "useItemRemaining")
        val useItemRemainingField = Reflector.getField(
            LivingEntity::class.java, remapped
        )
        Reflector.setFieldValue(useItemRemainingField, entityLiving, 200)
    }
}
