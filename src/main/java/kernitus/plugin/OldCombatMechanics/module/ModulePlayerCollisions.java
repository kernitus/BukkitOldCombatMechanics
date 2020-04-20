package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.Messenger;
import kernitus.plugin.OldCombatMechanics.utilities.packet.PacketAdapter;
import kernitus.plugin.OldCombatMechanics.utilities.packet.PacketEvent;
import kernitus.plugin.OldCombatMechanics.utilities.packet.PacketManager;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.type.ClassType;
import kernitus.plugin.OldCombatMechanics.utilities.teams.CollisionRule;
import kernitus.plugin.OldCombatMechanics.utilities.teams.TeamAction;
import kernitus.plugin.OldCombatMechanics.utilities.teams.TeamPacket;
import kernitus.plugin.OldCombatMechanics.utilities.teams.TeamUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Disables player collisions.
 */
public class ModulePlayerCollisions extends Module {

    private CollisionPacketListener collisionPacketListener = new CollisionPacketListener();
    private Map<Player, TeamPacket> playerTeamMap = Collections.synchronizedMap(new WeakHashMap<>());


    public ModulePlayerCollisions(OCMMain plugin){
        super(plugin, "disable-player-collisions");

        // inject all players at startup, so the plugin still works properly after a reload
        OCMMain.getInstance().addEnableListener(() -> {
            for(Player player : Bukkit.getOnlinePlayers()){
                PacketManager.getInstance().addListener(collisionPacketListener, player);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerLogin(PlayerJoinEvent e){
        // always attach the listener, it checks internally
        PacketManager.getInstance().addListener(collisionPacketListener, e.getPlayer());

        createOrUpdateTeam(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangeWorld(PlayerChangedWorldEvent e){
        createOrUpdateTeam(e.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        PacketManager.getInstance().removeListener(collisionPacketListener, e.getPlayer());
        playerTeamMap.remove(e.getPlayer());
    }

    /**
     * Creates a team or updates it by sending the correct packet to the player.
     *
     * @param player the player to send it to
     */
    private void createOrUpdateTeam(Player player){
        CollisionRule collisionRule = isEnabled(player.getWorld())
                ? CollisionRule.NEVER
                : CollisionRule.ALWAYS;

        if(playerTeamMap.containsKey(player)){
            TeamPacket teamPacket = playerTeamMap.get(player);
            teamPacket.setTeamAction(TeamAction.UPDATE);
            teamPacket.setCollisionRule(collisionRule);
            teamPacket.send(player);
        } else {
            debug("Fake collision team created for you.", player);
            createAndSendNewTeam(player, collisionRule);
        }
    }

    /**
     * Creates a new {@link TeamPacket} stores it in the cache map and sends it to the player.
     *
     * @param player        the player to send it to
     * @param collisionRule the {@link CollisionRule} to use
     */
    private void createAndSendNewTeam(Player player, CollisionRule collisionRule){
        TeamPacket newTeamPacket = TeamUtils.craftTeamCreatePacket(player, collisionRule);
        playerTeamMap.put(player, newTeamPacket);

        newTeamPacket.send(player);
    }


    @Override
    public void reload(){
        super.reload();

        for(Player player : Bukkit.getOnlinePlayers()){
            createOrUpdateTeam(player);
        }
    }

    private class CollisionPacketListener extends PacketAdapter {

        private final Class<?> targetClass = Reflector
                .getClass(ClassType.NMS, "PacketPlayOutScoreboardTeam");

        @Override
        public void onPacketSend(PacketEvent packetEvent){
            if(packetEvent.getPacket().getPacketClass() != targetClass){
                return;
            }

            Object nmsPacket = packetEvent.getPacket().getNMSPacket();

            CollisionRule collisionRule = isEnabled(packetEvent.getPlayer().getWorld())
                    ? CollisionRule.NEVER
                    : CollisionRule.ALWAYS;

            updateToPacket(
                    packetEvent.getPlayer(),
                    playerTeamMap.computeIfAbsent(packetEvent.getPlayer(), player -> new TeamPacket()),
                    nmsPacket
            );

            // Also update the team when a player was added/removed
            if(TeamUtils.targetsPlayer(nmsPacket, packetEvent.getPlayer())){
                updateToPacket(
                        packetEvent.getPlayer(), playerTeamMap.get(packetEvent.getPlayer()), nmsPacket
                );
            }

            // always update, only react when enabled
            if(!isEnabled(packetEvent.getPlayer().getWorld())){
                return;
            }

            Messenger.debug(
                    "Collision rule set to %s for action %s in world %s.",
                    collisionRule,
                    TeamUtils.getPacketAction(nmsPacket),
                    packetEvent.getPlayer().getWorld().getName()
            );

            TeamUtils.setCollisionRule(nmsPacket, collisionRule);

            // Reinstate if it was disbanded to have the correct rule
            TeamPacket teamPacket = playerTeamMap.get(packetEvent.getPlayer());
            if(teamPacket == null || !teamPacket.teamExists()){
                createAndSendNewTeam(packetEvent.getPlayer(), collisionRule);
            }
        }

        /**
         * Updates the given {@link TeamPacket} to the NMS packet and removes it from the cache, if it was disbanded.
         */
        private void updateToPacket(Player player, TeamPacket teamPacket, Object nmsPacket){
            teamPacket.adjustToUpdate(nmsPacket);

            if(!teamPacket.teamExists()){
                playerTeamMap.remove(player);
                debug("Your team was disbanded.", player);
            }
        }
    }
}
