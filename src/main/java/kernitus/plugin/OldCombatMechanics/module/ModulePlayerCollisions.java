package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.Messenger;
import kernitus.plugin.OldCombatMechanics.utilities.packet.mitm.PacketAdapter;
import kernitus.plugin.OldCombatMechanics.utilities.packet.mitm.PacketEvent;
import kernitus.plugin.OldCombatMechanics.utilities.packet.PacketHelper;
import kernitus.plugin.OldCombatMechanics.utilities.packet.PacketHelper.PacketType;
import kernitus.plugin.OldCombatMechanics.utilities.packet.mitm.PacketManager;
import kernitus.plugin.OldCombatMechanics.utilities.packet.team.TeamPacket;
import kernitus.plugin.OldCombatMechanics.utilities.teams.CollisionRule;
import kernitus.plugin.OldCombatMechanics.utilities.teams.TeamAction;
import kernitus.plugin.OldCombatMechanics.utilities.teams.TeamUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Disables player collisions.
 */
public class ModulePlayerCollisions extends Module {

    private final CollisionPacketListener collisionPacketListener;
    private final Map<Player, TeamPacket> playerTeamMap;

    public ModulePlayerCollisions(OCMMain plugin){
        super(plugin, "disable-player-collisions");

        // inject all players at startup, so the plugin still works properly after a reload
        collisionPacketListener = new CollisionPacketListener();
        playerTeamMap = new WeakHashMap<>();

        // Disband our OCM teams in onDisable so they can be reused
        OCMMain.getInstance().addDisableListener(() -> {
            synchronized(playerTeamMap){
                for(Map.Entry<Player, TeamPacket> entry : playerTeamMap.entrySet()){
                    if(TeamUtils.isOcmTeam(entry.getValue())){
                        TeamUtils.disband(entry.getValue().getName(), entry.getKey());
                    }
                }
            }
        });
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

    /**
     * Creates a team or updates it by sending the correct packet to the player.
     *
     * @param player the player to send it to
     */
    private void createOrUpdateTeam(Player player){
        CollisionRule collisionRule = isEnabled(player.getWorld())
                ? CollisionRule.NEVER
                : CollisionRule.ALWAYS;

        synchronized(playerTeamMap){
            if(playerTeamMap.containsKey(player)){
                TeamPacket teamPacket = playerTeamMap.get(player);
                teamPacket = teamPacket.withAction(TeamAction.UPDATE);
                teamPacket = teamPacket.withCollisionRule(collisionRule);

                playerTeamMap.put(player, teamPacket);

                teamPacket.send(player);
            } else {
                debug("Fake collision team created for you.", player);
                createAndSendNewTeam(player, collisionRule);
            }
        }
    }

    /**
     * Creates a new {@link TeamPacket} stores it in the cache map and sends it to the player.
     *
     * @param player        the player to send it to
     * @param collisionRule the {@link CollisionRule} to use
     */
    private void createAndSendNewTeam(Player player, CollisionRule collisionRule){
        synchronized(playerTeamMap){
            TeamPacket newTeamPacket = TeamUtils.craftTeamCreatePacket(player, collisionRule);
            playerTeamMap.put(player, newTeamPacket);
            newTeamPacket.send(player);
        }
    }


    @Override
    public void reload(){
        synchronized(playerTeamMap){
            for(Player player : Bukkit.getOnlinePlayers()){
                createOrUpdateTeam(player);
            }
        }
    }

    private class CollisionPacketListener extends PacketAdapter {

        private final Class<?> targetClass = PacketHelper.getPacketClass(PacketType.PlayOut, "ScoreboardTeam");

        @Override
        public void onPacketSend(PacketEvent packetEvent){
            if(packetEvent.getPacket().getPacketClass() != targetClass){
                return;
            }

            synchronized(playerTeamMap){
                handlePacket(packetEvent);
            }
        }

        private void handlePacket(PacketEvent packetEvent){
            Object nmsPacket = packetEvent.getPacket().getNmsPacket();
            TeamPacket incomingTeamPacket = TeamPacket.from(nmsPacket);

            CollisionRule collisionRule = isEnabled(packetEvent.getPlayer().getWorld())
                    ? CollisionRule.NEVER
                    : CollisionRule.ALWAYS;


            if(interestingForPlayer(incomingTeamPacket, packetEvent.getPlayer())){
                updateToPacket(packetEvent.getPlayer(), incomingTeamPacket);
            }

            // always update, only react when enabled
            if(!isEnabled(packetEvent.getPlayer().getWorld())){
                return;
            }

            Messenger.debug(
                    "[%s-%s] Collision rule set to %s for action %s in world %s.",
                    incomingTeamPacket.getName(),
                    Optional.ofNullable(playerTeamMap.get(packetEvent.getPlayer())).map(TeamPacket::getName),
                    collisionRule,
                    incomingTeamPacket.getAction(),
                    packetEvent.getPlayer().getWorld().getName()
            );

            incomingTeamPacket = incomingTeamPacket.withCollisionRule(collisionRule);
            packetEvent.setPacket(PacketHelper.wrap(incomingTeamPacket.getNmsPacket()));

            // Reinstate if it was disbanded to have the correct rule
            if(!playerTeamMap.containsKey(packetEvent.getPlayer())){
                createAndSendNewTeam(packetEvent.getPlayer(), collisionRule);
            }
        }

        private boolean interestingForPlayer(TeamPacket packet, Player player){
            if(TeamUtils.targetsPlayer(packet, player)){
                return true;
            }
            TeamPacket storedTeam = playerTeamMap.get(player);
            return storedTeam != null && storedTeam.getName().equals(packet.getName());
        }

        /**
         * Updates the given {@link TeamPacket} to the NMS packet and removes it from the cache, if it was disbanded.
         */
        private void updateToPacket(Player player, TeamPacket incomingPacket){
            Optional<TeamPacket> current = Optional.ofNullable(playerTeamMap.get(player));

            // Only we disband these teams and we do not need to create a new team in response.
            // So just ignore those disband packets. The player team map was already updated when the
            // disband packet was sent
            if(incomingPacket.getAction() == TeamAction.DISBAND && TeamUtils.isOcmTeam(incomingPacket)){
                return;
            }

            boolean currentIsOcmTeam = current.isPresent() && TeamUtils.isOcmTeam(current.get());
            // We already have an OCM team!
            if(incomingPacket.getAction() == TeamAction.DISBAND && currentIsOcmTeam){
                return;
            }

            // We got a new team (i.e. not an update)
            if(!current.isPresent() || !incomingPacket.getName().equals(current.get().getName())){
                // The old team is ours -> Disband it
                if(currentIsOcmTeam){
                    TeamUtils.disband(current.get().getName(), player);
                }
                current = Optional.of(incomingPacket);
            }

            Optional<TeamPacket> newPacket = current.get().adjustedTo(incomingPacket, player);

            if(newPacket.isPresent()){
                playerTeamMap.put(player, newPacket.get());
            } else {
                playerTeamMap.remove(player);
                debug("Your team was disbanded.", player);
            }
        }
    }
}
