/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics.tester;

import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.chat.IChatBaseComponent;
import net.minecraft.network.protocol.EnumProtocolDirection;
import net.minecraft.network.protocol.game.PacketPlayOutAnimation;
import net.minecraft.network.protocol.game.PacketPlayOutEntityDestroy;
import net.minecraft.network.protocol.game.PacketPlayOutNamedEntitySpawn;
import net.minecraft.network.protocol.game.PacketPlayOutPlayerInfo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkProviderServer;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.server.level.WorldServer;
import net.minecraft.server.network.PlayerConnection;
import net.minecraft.server.players.PlayerList;
import net.minecraft.stats.StatisticList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.EnumGamemode;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_19_R1.CraftServer;
import org.bukkit.craftbukkit.v1_19_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_19_R1.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_19_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_19_R1.util.CraftChatMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;
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

        EntityPlayer entityPlayer = createEntityPlayer(uuid, name, worldServer);

        this.entityPlayer = entityPlayer;

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

        /*DataWatcher data = entityPlayer.getDataWatcher();
        data.set(DataWatcherRegistry.a.a(16), (byte) 127);*/

        entityPlayer.spawnIn(worldServer);
        // entityPlayer.playerInteractManager.changeGameModeForPlayer(GameType.SURVIVAL);
        entityPlayer.d.a(EnumGamemode.a);

        worldServer.a(entityPlayer); // worldServer.addPlayerJoin(entityPlayer);
        final PlayerList playerList = mcServer.ac(); // mcServer.getPlayerList()
        playerList.k.add(entityPlayer); // playerList().players.add(entityPlayer);

        // Get private playerByUUID Map from PlayerList class and add player to it
        final Field playerByUUIDField = Reflector.getInaccessibleField(PlayerList.class, "l");
        final Map<UUID, EntityPlayer> playerByUUID = (Map<UUID, EntityPlayer>) Reflector.getFieldValue(playerByUUIDField, playerList);
        playerByUUID.put(uuid, entityPlayer);

        final Player foundPlayer = Bukkit.getPlayer(uuid);
        final String joinMessage = "§e" + entityPlayer.displayName + " joined the game";
        final PlayerJoinEvent playerJoinEvent = new PlayerJoinEvent(foundPlayer, CraftChatMessage.fromComponent(IChatBaseComponent.a(joinMessage)));

        Bukkit.getPluginManager().callEvent(playerJoinEvent);

        for (Player player : Bukkit.getOnlinePlayers()) {
            //PlayerConnection connection = ((CraftPlayer) player).getHandle().playerConnection;
            PlayerConnection connection = ((CraftPlayer) player).getHandle().b;
            // connection.sendPacket(new PacketPlayOutPlayerInfo(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.ADD_PLAYER, entityPlayer));
            connection.a(new PacketPlayOutPlayerInfo(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.a, entityPlayer));
            // Spawn the player for the client
            connection.a(new PacketPlayOutNamedEntitySpawn(entityPlayer));
            // Remove player name from tablist
            connection.a(new PacketPlayOutPlayerInfo(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.e));
        }

        // ServerPlayer.doTick() a.k.a. EntityPlayer.playerTick()
        // todo should probably cancel this task when we remove the player
        Bukkit.getScheduler().scheduleSyncRepeatingTask(OCMMain.getInstance(), entityPlayer::k, 1, 1);
    }

    private static EntityPlayer createEntityPlayer(UUID uuid, String name, WorldServer worldServer) {
        final MinecraftServer mcServer = ((CraftServer) Bukkit.getServer()).getServer();
        final GameProfile gameProfile = new GameProfile(uuid, name);

        //return new EntityPlayer(mcServer, worldServer, gameProfile, new PlayerInteractManager(worldServer));
        return new EntityPlayer(mcServer, worldServer, gameProfile, null);
    }

    public void removePlayer() {
        final MinecraftServer mcServer = ((CraftServer) Bukkit.getServer()).getServer();
        final WorldServer worldServer = entityPlayer.x(); // entityPlayer.getWorld().getWorld().getHandle();

        entityPlayer.a(StatisticList.j); // entityPlayer.a(StatisticList.LEAVE_GAME);

        final Player foundPlayer = Bukkit.getPlayer(uuid);
        final PlayerQuitEvent playerQuitEvent = new PlayerQuitEvent(foundPlayer, "§e" + entityPlayer.displayName + " left the game");

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

        for (Player p : Bukkit.getOnlinePlayers()) {
            final PlayerConnection connection = ((CraftPlayer) p).getHandle().b; //.playerConnection;
            // connection.sendPacket(new PacketPlayOutEntityDestroy(entityPlayer.getId()));
            connection.a(new PacketPlayOutEntityDestroy(uuid.hashCode()));
            //connection.sendPacket(new PacketPlayOutPlayerInfo(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.REMOVE_PLAYER, entityPlayer));
            connection.a(new PacketPlayOutPlayerInfo(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.e, entityPlayer));
        }

        // PlayerList.save(EntityPlayer)
        Method saveMethod = Reflector.getMethod(PlayerList.class, "b", "EntityPlayer");
        saveMethod.setAccessible(true);
        Reflector.invokeMethod(saveMethod, playerList, entityPlayer);
    }

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
}
