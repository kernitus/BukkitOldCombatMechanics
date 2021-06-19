package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.Messenger;
import kernitus.plugin.OldCombatMechanics.utilities.packet.PacketAdapter;
import kernitus.plugin.OldCombatMechanics.utilities.packet.PacketEvent;
import kernitus.plugin.OldCombatMechanics.utilities.packet.PacketManager;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.type.ClassType;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.type.PacketType;
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
            if (!TeamUtils.isSetup()) return;

            synchronized(playerTeamMap){
                for(Map.Entry<Player, TeamPacket> entry : playerTeamMap.entrySet()){
                    if(TeamUtils.isOcmTeam(entry.getValue().getTeamName())){
                        TeamUtils.disband(entry.getValue().getTeamName(), entry.getKey());
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
        if(!TeamUtils.isSetup()) return;

        CollisionRule collisionRule = isEnabled(player.getWorld())
                ? CollisionRule.NEVER
                : CollisionRule.ALWAYS;

        synchronized(playerTeamMap){
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
    }

    /**
     * Creates a new {@link TeamPacket} stores it in the cache map and sends it to the player.
     *
     * @param player        the player to send it to
     * @param collisionRule the {@link CollisionRule} to use
     */
    private void createAndSendNewTeam(Player player, CollisionRule collisionRule){
        if (!TeamUtils.isSetup()) return;

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

        private final Class<?> targetClass = Reflector.Packets.getPacket(PacketType.PlayOut, "ScoreboardTeam");

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
            Object nmsPacket = packetEvent.getPacket().getNMSPacket();

            CollisionRule collisionRule = isEnabled(packetEvent.getPlayer().getWorld())
                    ? CollisionRule.NEVER
                    : CollisionRule.ALWAYS;


            if(interestingForPlayer(nmsPacket, packetEvent.getPlayer())){
                updateToPacket(packetEvent.getPlayer(), nmsPacket);
            }

            // always update, only react when enabled
            if(!isEnabled(packetEvent.getPlayer().getWorld())){
                return;
            }

            Messenger.debug(
                    "[%s-%s] Collision rule set to %s for action %s in world %s.",
                    TeamUtils.getTeamName(nmsPacket),
                    Optional.ofNullable(playerTeamMap.get(packetEvent.getPlayer())).map(TeamPacket::getTeamName),
                    collisionRule,
                    TeamUtils.getPacketAction(nmsPacket),
                    packetEvent.getPlayer().getWorld().getName()
            );

            TeamUtils.setCollisionRule(nmsPacket, collisionRule);

            // Reinstate if it was disbanded to have the correct rule
            if(!playerTeamMap.containsKey(packetEvent.getPlayer())){
                createAndSendNewTeam(packetEvent.getPlayer(), collisionRule);
            }
        }

        private boolean interestingForPlayer(Object packet, Player player){
            if(TeamUtils.targetsPlayer(packet, player)){
                return true;
            }
            TeamPacket storedTeam = playerTeamMap.get(player);
            return storedTeam != null && storedTeam.getTeamName().equals(TeamUtils.getTeamName(packet));
        }

        /**
         * Updates the given {@link TeamPacket} to the NMS packet and removes it from the cache, if it was disbanded.
         */
        private void updateToPacket(Player player, Object nmsPacket){
            String nmsPacketTeamName = TeamUtils.getTeamName(nmsPacket);
            TeamPacket current = playerTeamMap.computeIfAbsent(player, __ -> new TeamPacket());

            // Only we disband these teams and we do not need to create a new team in response.
            // So just ignore those disband packets. The player team map was already updated when the
            // disband packet was sent
            if(TeamUtils.getPacketAction(nmsPacket) == TeamAction.DISBAND && TeamUtils.isOcmTeam(nmsPacketTeamName)){
                return;
            }

            // We already have an OCM team!
            if(TeamUtils.getPacketAction(nmsPacket) == TeamAction.DISBAND && TeamUtils.isOcmTeam(current.getTeamName())){
                return;
            }

            // We got a new team (i.e. not an update)
            if(!nmsPacketTeamName.equals(current.getTeamName())){
                // The old team is ours -> Disband it
                if(TeamUtils.isOcmTeam(current.getTeamName())){
                    TeamUtils.disband(current.getTeamName(), player);
                }
                current = new TeamPacket(nmsPacket);
            }

            Optional<TeamPacket> newPacket = current.adjustToUpdate(nmsPacket, player);

            if(newPacket.isPresent()){
                playerTeamMap.put(player, newPacket.get());
            } else {
                playerTeamMap.remove(player);
                debug("Your team was disbanded.", player);
            }
        }
    }
}
