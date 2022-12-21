/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics.tester;

import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Pair;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.chat.IChatBaseComponent;
import net.minecraft.network.protocol.EnumProtocolDirection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.PacketPlayOutEntityDestroy;
import net.minecraft.network.protocol.game.PacketPlayOutEntityEquipment;
import net.minecraft.network.protocol.game.PacketPlayOutNamedEntitySpawn;
import net.minecraft.network.protocol.game.PacketPlayOutPlayerInfo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.server.level.WorldServer;
import net.minecraft.server.network.PlayerConnection;
import net.minecraft.server.players.PlayerList;
import net.minecraft.stats.StatisticList;
import net.minecraft.world.EnumHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.entity.EnumItemSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EnumGamemode;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_19_R1.CraftServer;
import org.bukkit.craftbukkit.v1_19_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_19_R1.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.v1_19_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_19_R1.inventory.CraftItemStack;
import org.bukkit.craftbukkit.v1_19_R1.util.CraftChatMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/*
    Resources

    - FakePlayers plugin (1.19 version, had some wrong method names to fix and some things not re-implemented):
    https://github.com/gabrianrchua/Fake-Players-Spigot-Plugin/blob/b88b4c33dcf73d9a9a36f6a202b5273712055751/src/main/java/me/KP56/FakePlayers/MultiVersion/v1_19_R1.java

    - Guide on making NMS bots, for a few small tweaks
    https://www.spigotmc.org/threads/nms-serverplayer-entityplayer-for-the-1-17-1-18-mojang-mappings-with-fall-damage-and-knockback.551281/

    - NMS mappings for checking Mojang / Spigot / Obfuscated class, field, and method names
    https://nms.screamingsandals.org/
 */

public class FakePlayer {

    private final UUID uuid;
    private final String name;
    private EntityPlayer entityPlayer;
    private Player bukkitPlayer;

    public FakePlayer() {
        uuid = UUID.randomUUID();
        name = uuid.toString().substring(0, 16);
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public EntityPlayer getEntityPlayer() {
        return entityPlayer;
    }

    public void spawn(Location location) {
        WorldServer worldServer = ((CraftWorld) location.getWorld()).getHandle();

        MinecraftServer mcServer = ((CraftServer) Bukkit.getServer()).getServer();

        final GameProfile gameProfile = new GameProfile(uuid, name);
        this.entityPlayer = new EntityPlayer(mcServer, worldServer, gameProfile, null);

        // entityPlayer.playerConnection
        entityPlayer.b = new PlayerConnection(mcServer, new NetworkManager(EnumProtocolDirection.a), entityPlayer); // should be EnumProtocolDirection.CLIENTBOUND

        // entityPlayer.playerConnection.networkManager.channel
        entityPlayer.b.b.m = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        entityPlayer.b.b.m.close();

        try {
            AsyncPlayerPreLoginEvent asyncPreLoginEvent = new AsyncPlayerPreLoginEvent(name, InetAddress.getByName("127.0.0.1"), uuid);
            new Thread(() -> Bukkit.getPluginManager().callEvent(asyncPreLoginEvent)).start();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }

        mcServer.ac().a(entityPlayer); // mcServer.getPlayerList().a(entityPlayer);

        entityPlayer.e(location.getX(), location.getY(), location.getZ()); // Entity.setLocation
        entityPlayer.p(0); // Entity.setXRot()
        entityPlayer.o(0); // Entity.setYRot()

        entityPlayer.spawnIn(worldServer);
        // entityPlayer.playerInteractManager.changeGameModeForPlayer(GameType.SURVIVAL);
        entityPlayer.d.a(EnumGamemode.a);

        worldServer.c(entityPlayer); // worldServer.addNewPlayer(entityPlayer);
        final PlayerList playerList = mcServer.ac(); // mcServer.getPlayerList()
        playerList.k.add(entityPlayer); // playerList().players.add(entityPlayer);

        // Get private playerByUUID Map from PlayerList class and add player to it
        final Field playerByUUIDField = Reflector.getInaccessibleField(PlayerList.class, "l");
        final Map<UUID, EntityPlayer> playerByUUID = (Map<UUID, EntityPlayer>) Reflector.getFieldValue(playerByUUIDField, playerList);
        playerByUUID.put(uuid, entityPlayer);

        bukkitPlayer = Bukkit.getPlayer(uuid);
        final String joinMessage = "§e" + entityPlayer.displayName + " joined the game";
        final PlayerJoinEvent playerJoinEvent = new PlayerJoinEvent(bukkitPlayer, CraftChatMessage.fromComponent(IChatBaseComponent.a(joinMessage)));

        Bukkit.getPluginManager().callEvent(playerJoinEvent);

        // connection.sendPacket(new PacketPlayOutPlayerInfo(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.ADD_PLAYER, entityPlayer));
        sendPacket(new PacketPlayOutPlayerInfo(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.a, entityPlayer));
        // Spawn the player for the client
        sendPacket(new PacketPlayOutNamedEntitySpawn(entityPlayer));
        // Remove player name from tablist
        sendPacket(new PacketPlayOutPlayerInfo(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.e));

        // todo should probably cancel this task when we remove the player
        // ServerPlayer.doTick() a.k.a. EntityPlayer.playerTick()
        Bukkit.getScheduler().scheduleSyncRepeatingTask(OCMMain.getInstance(), entityPlayer::k, 1, 1);
    }

    public void removePlayer() {
        final MinecraftServer mcServer = ((CraftServer) Bukkit.getServer()).getServer();
        final WorldServer worldServer = entityPlayer.x(); // entityPlayer.getWorld().getWorld().getHandle();

        entityPlayer.a(StatisticList.j); // entityPlayer.a(StatisticList.LEAVE_GAME);

        final PlayerQuitEvent playerQuitEvent = new PlayerQuitEvent(bukkitPlayer, "§e" + entityPlayer.displayName + " left the game");

        Bukkit.getPluginManager().callEvent(playerQuitEvent);

        entityPlayer.getBukkitEntity().disconnect(playerQuitEvent.getQuitMessage());

        if (!mcServer.at()) { // MinecraftServer.isNotMainThread()
            // ServerPlayer.doTick() a.k.a. EntityPlayer.playerTick()
            entityPlayer.l();
        }

        worldServer.a(entityPlayer, Entity.RemovalReason.a); //worldServer.removePlayer(entityPlayer);
        entityPlayer.M().a(); //entityPlayer.getAdvancementData().a();

        final PlayerList playerList = mcServer.ac(); // mcServer.getPlayerList()
        playerList.k.remove(entityPlayer); // playerList().players.add(entityPlayer);

        // Get private playerByUUID Map from PlayerList class and remove player from it
        final Field playerByUUIDField = Reflector.getInaccessibleField(PlayerList.class, "l");
        final Map<UUID, EntityPlayer> playerByUUID = (Map<UUID, EntityPlayer>) Reflector.getFieldValue(playerByUUIDField, playerList);
        playerByUUID.remove(uuid);

        // connection.sendPacket(new PacketPlayOutEntityDestroy(entityPlayer.getId()));
        sendPacket(new PacketPlayOutEntityDestroy(uuid.hashCode()));
        //connection.sendPacket(new PacketPlayOutPlayerInfo(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.REMOVE_PLAYER, entityPlayer));
        sendPacket(new PacketPlayOutPlayerInfo(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.e, entityPlayer));

        // PlayerList.save(EntityPlayer)
        Method saveMethod = Reflector.getMethod(PlayerList.class, "b", "EntityPlayer");
        saveMethod.setAccessible(true);
        Reflector.invokeMethod(saveMethod, playerList, entityPlayer);
    }

    public void attack(org.bukkit.entity.Entity bukkitEntity) {
        bukkitPlayer.attack(bukkitEntity);
    }

    //a(EnumItemSlot.Function.a, 0, 0, "mainhand"),
    //b(EnumItemSlot.Function.a, 1, 5, "offhand"),
    //c(EnumItemSlot.Function.b, 0, 1, "feet"),
    //d(EnumItemSlot.Function.b, 1, 2, "legs"),
    //e(EnumItemSlot.Function.b, 2, 3, "chest"),
    //f(EnumItemSlot.Function.b, 3, 4, "head");

    public void updateEquipment(EnumItemSlot slot, org.bukkit.inventory.ItemStack item) {
        //todo try directly accessing the player inventory
        // otherwise just set the attack value attribute instead
        // could check Citizen's code to see how they give weapons
        // might also just need to wait a tick

        //final WorldServer worldServer = entityPlayer.x(); // entityPlayer.getWorld().getWorld().getHandle();
        // worldServer.broadcastEntityEvent(Entity, byte)

        //Defining the list of Pairs with EnumItemSlot and (NMS) ItemStack
        final List<Pair<EnumItemSlot, ItemStack>> equipmentList = new ArrayList<>();
        equipmentList.add(new Pair<>(slot, CraftItemStack.asNMSCopy(item)));

        //Creating the packet
        final PacketPlayOutEntityEquipment entityEquipment = new PacketPlayOutEntityEquipment(bukkitPlayer.getEntityId(), equipmentList);
        sendPacket(entityEquipment);
        //         ((WorldServer) this.level).getChunkSource().broadcast(this, new PacketPlayOutEntityEquipment(this.getId(), list));
    }

    private void sendPacket(Packet packet) {
        Bukkit.getOnlinePlayers().stream()
                .map(p -> ((CraftPlayer) p).getHandle().b)
                .forEach(connection -> connection.a(packet));
    }

    /**
     * Make this player block with a shield
     */
    public void doBlocking() {
        bukkitPlayer.getInventory().setItemInMainHand(new org.bukkit.inventory.ItemStack(Material.SHIELD));

        final EntityLiving entityLiving = ((CraftLivingEntity) bukkitPlayer).getHandle();
        // We can call EntityLiving.startUsingItem(InteractionHand arg0) which is c()
        entityLiving.c(EnumHand.a); // a in main hand, b is off hand
        // For isBlocking to be true, useDuration - getUseItemRemainingTicks() must be >= 5
        // Which means we have to wait at least 5 ticks before user is actually blocking
        // Here we just set it manually
        Field useItemRemainingField = Reflector.getField(EntityLiving.class, "bA"); // int useItemRemaining
        Reflector.setFieldValue(useItemRemainingField, entityLiving, 10);
    }

    /*
        Example of using DataWatcher
        final DataWatcher dw = entityLiving.ai(); // getEntityData() is ai()
        // Field aB in EntityHuman is EntityLiving's DataWatcherObject<Byte> DATA_LIVING_ENTITY_FLAGS
        //final DataWatcherObject<Byte> dwo = (DataWatcherObject) Reflector.getFieldValue(Reflector.getField(EntityLiving.class, "aB"), entityLiving);
        final DataWatcherObject<Byte> dwo = new DataWatcherObject<>(8, DataWatcherRegistry.a); // a is for BYTE
        //final DataWatcher holdingShieldData = new DataWatcher(((CraftPlayer) bukkitPlayer).getHandle());
        //final DataWatcherObject dataWatcherObject = new DataWatcherObject<>(6, DataWatcherRegistry.a);
        //holdingShieldData.a(dataWatcherObject, 0); // a is define()

        //byte handStateBitmask = 0x01; // Main hand is active

        //dw.b(dwo, handStateBitmask); // b is set()

        //final PacketPlayOutEntityMetadata metadataPacket = new PacketPlayOutEntityMetadata(bukkitPlayer.getEntityId(), dw, true);
        //final PacketPlayOutAnimation packetPlayOutAnimation = new PacketPlayOutAnimation(entityPlayer, 0);
        //sendPacket(metadataPacket);
        //sendPacket(packetPlayOutAnimation);
     */

    /*
    public void attack(org.bukkit.entity.Entity bukkitEntity) {
        final CraftEntity craftEntity = ((CraftEntity) bukkitEntity);
        final Entity nmsEntity = craftEntity.getHandle();
        attack(nmsEntity);
    }

    public void attack(Entity entity) {
        // ServerPlayer.attack(nmsEntity)
        entityPlayer.d(entity);

        final PacketPlayOutAnimation packetPlayOutAnimation = new PacketPlayOutAnimation(entityPlayer, 0);

        // entityPlayer.getLevel()
        final WorldServer worldServer = entityPlayer.x();
        // worldServer.getChunkSource()
        final ChunkProviderServer chunkProviderServer = worldServer.k();
        // chunkProviderServer.broadcastAndSend(Entity, Packet)
        chunkProviderServer.a(entityPlayer, packetPlayOutAnimation);
    }
     */
}
