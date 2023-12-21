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
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundAddPlayerPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
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
    private ServerPlayer entityPlayer;
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

    public ServerPlayer getServerPlayer() {
        return entityPlayer;
    }

    public void spawn(Location location) {
        ServerLevel worldServer = ((CraftWorld) location.getWorld()).getHandle();

        MinecraftServer mcServer = ((CraftServer) Bukkit.getServer()).getServer();

        final GameProfile gameProfile = new GameProfile(uuid, name);
        this.entityPlayer = new ServerPlayer(mcServer, worldServer, gameProfile, null);

        // entityPlayer.playerConnection
        entityPlayer.connection = new ServerGamePacketListenerImpl(mcServer, new Connection(PacketFlow.CLIENTBOUND), entityPlayer);

        // entityPlayer.playerConnection.networkManager.channel
        entityPlayer.connection.connection.channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        entityPlayer.connection.connection.channel.close();

        try {
            AsyncPlayerPreLoginEvent asyncPreLoginEvent = new AsyncPlayerPreLoginEvent(name, InetAddress.getByName("127.0.0.1"), uuid);
            new Thread(() -> Bukkit.getPluginManager().callEvent(asyncPreLoginEvent)).start();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }

        mcServer.getPlayerList().load(entityPlayer);

        entityPlayer.setPos(location.getX(), location.getY(), location.getZ()); // Entity.setLocation
        entityPlayer.setXRot(0); // Entity.setXRot()
        entityPlayer.setYRot(0); // Entity.setYRot()

        entityPlayer.spawnIn(worldServer);
        // entityPlayer.playerInteractManager.changeGameModeForPlayer(GameType.SURVIVAL);
        entityPlayer.setGameMode(GameType.SURVIVAL);

        worldServer.addNewPlayer(entityPlayer);
        final PlayerList playerList = mcServer.getPlayerList();
        playerList.players.add(entityPlayer);

        // Get private playerByUUID Map from PlayerList class and add player to it
        final Field playerByUUIDField = Reflector.getInaccessibleField(PlayerList.class, "l");
        final Map<UUID, ServerPlayer> playerByUUID = (Map<UUID, ServerPlayer>) Reflector.getFieldValue(playerByUUIDField, playerList);
        playerByUUID.put(uuid, entityPlayer);

        bukkitPlayer = Bukkit.getPlayer(uuid);
        final String joinMessage = "§e" + entityPlayer.displayName + " joined the game";
        final PlayerJoinEvent playerJoinEvent = new PlayerJoinEvent(bukkitPlayer, CraftChatMessage.fromComponent(Component.literal(joinMessage)));

        Bukkit.getPluginManager().callEvent(playerJoinEvent);

        // connection.sendPacket(new ClientboundPlayerInfoPacket(ClientboundPlayerInfoPacket.EnumPlayerInfoAction.ADD_PLAYER, entityPlayer));
        sendPacket(new ClientboundPlayerInfoPacket(ClientboundPlayerInfoPacket.Action.ADD_PLAYER, entityPlayer));
        // Spawn the player for the client
        sendPacket(new ClientboundAddPlayerPacket(entityPlayer));
        // Remove player name from tablist
        sendPacket(new ClientboundPlayerInfoPacket(ClientboundPlayerInfoPacket.Action.REMOVE_PLAYER));

        // todo should probably cancel this task when we remove the player
        // ServerPlayer.doTick() a.k.a. ServerPlayer.playerTick()
        Bukkit.getScheduler().scheduleSyncRepeatingTask(OCMMain.getInstance(), entityPlayer::tick, 1, 1);
    }

    public void removePlayer() {
        final MinecraftServer mcServer = ((CraftServer) Bukkit.getServer()).getServer();

        // TODO this message never shows - does the event trigger?
        final net.kyori.adventure.text.Component quitMessage = net.kyori.adventure.text.Component.text("§e" + entityPlayer.displayName + " left the game");
        final PlayerQuitEvent playerQuitEvent = new PlayerQuitEvent(bukkitPlayer, quitMessage, PlayerQuitEvent.QuitReason.DISCONNECTED);

        Bukkit.getPluginManager().callEvent(playerQuitEvent);

        entityPlayer.getBukkitEntity().disconnect(quitMessage.toString());

        // TODO isSameThread might not be correct method - is this even necessary?
        if (!mcServer.isSameThread()) { // t() MinecraftServer.isNotMainThread()
            entityPlayer.doTick();
        }

        final PlayerList playerList = mcServer.getPlayerList();
        playerList.remove(entityPlayer);
    }

    public void attack(org.bukkit.entity.Entity bukkitEntity) {
        bukkitPlayer.attack(bukkitEntity);
    }

    public void updateEquipment(EquipmentSlot slot, org.bukkit.inventory.ItemStack item) {
        //todo try directly accessing the player inventory
        // otherwise just set the attack value attribute instead
        // could check Citizen's code to see how they give weapons
        // might also just need to wait a tick

        //final ServerLevel worldServer = entityPlayer.x(); // entityPlayer.getWorld().getWorld().getHandle();
        // worldServer.broadcastEntityEvent(Entity, byte)

        //Defining the list of Pairs with EquipmentSlot and (NMS) ItemStack
        final List<Pair<EquipmentSlot, ItemStack>> equipmentList = new ArrayList<>();
        equipmentList.add(new Pair<>(slot, CraftItemStack.asNMSCopy(item)));

        //Creating the packet
        final ClientboundSetEquipmentPacket entityEquipment = new ClientboundSetEquipmentPacket(bukkitPlayer.getEntityId(), equipmentList);
        sendPacket(entityEquipment);
        //         ((ServerLevel) this.level).getChunkSource().broadcast(this, new PacketPlayOutEntityEquipment(this.getId(), list));
    }

    private void sendPacket(Packet packet) {
        Bukkit.getOnlinePlayers().stream()
                .map(p -> ((CraftPlayer) p).getHandle().connection)
                .forEach(connection -> connection.send(packet));
    }

    /**
     * Make this player block with a shield
     */
    public void doBlocking() {
        bukkitPlayer.getInventory().setItemInMainHand(new org.bukkit.inventory.ItemStack(Material.SHIELD));

        final LivingEntity entityLiving = ((CraftLivingEntity) bukkitPlayer).getHandle();
        entityLiving.startUsingItem(InteractionHand.MAIN_HAND);
        // getUseDuration of SHIELD is 72000
        // this.useItemRemaining = itemstack.getUseDuration();

        // For isBlocking to be true, useDuration - getUseItemRemainingTicks() must be >= 5
        // EFFECTIVE_BLOCK_DELAY=5 in ItemShield
        // Which means we have to wait at least 5 ticks before user is actually blocking
        // Here we just set it manually
        Field useItemRemainingField = Reflector.getField(LivingEntity.class, "bA"); // int useItemRemaining
        Reflector.setFieldValue(useItemRemainingField, entityLiving, 10);
    }
}
