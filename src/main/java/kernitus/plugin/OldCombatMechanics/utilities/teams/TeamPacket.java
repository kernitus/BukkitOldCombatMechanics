package kernitus.plugin.OldCombatMechanics.utilities.teams;

import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;

public class TeamPacket {
    private final Object packetShallowClone;

    public TeamPacket(Object packet){
        this.packetShallowClone = Objects.requireNonNull(packet, "packet can not be null!");
    }

    public TeamPacket(){
        this(TeamUtils.createTeamNMSPacket(
                TeamAction.CREATE, CollisionRule.NEVER, "placeholder", Collections.emptyList()
        ));
    }

    /**
     * Adjusts this team packet to a given new update packet.
     *
     * @param updatePacket the sent team packet
     * @param player       the player to check it for
     * @return the new team packet or an empty optional if the team was disbanded
     */
    public Optional<TeamPacket> adjustToUpdate(Object updatePacket, Player player){
        switch(TeamUtils.getPacketAction(updatePacket)){
            case REMOVE_PLAYER:{
                Collection<String> removedPlayers = TeamUtils.getTeamMembers(updatePacket);
                if(removedPlayers.contains(player.getName())){
                    return Optional.empty();
                }
                return Optional.of(new TeamPacket(updatePacket));
            }
            case UPDATE:
            case ADD_PLAYER:
            case CREATE:
                return Optional.of(new TeamPacket(updatePacket));
            case DISBAND:
                return Optional.empty();
        }
        throw new IllegalArgumentException("Unknown team action");
    }

    /**
     * Sets the collision rule for this packet.
     *
     * @param collisionRule the collision rule
     */
    public void setCollisionRule(CollisionRule collisionRule){
        TeamUtils.setCollisionRule(packetShallowClone, collisionRule);
    }

    /**
     * Sets the team action for this packet.
     *
     * @param teamAction the team action
     */
    public void setTeamAction(TeamAction teamAction){
        TeamUtils.setPacketAction(packetShallowClone, teamAction);
    }

    /**
     * @return the team id or null if the team was disbanded
     */
    public String getTeamName(){
        if(packetShallowClone == null){
            return null;
        }
        return TeamUtils.getTeamName(packetShallowClone);
    }

    /**
     * Sends the packet to the given player.
     *
     * @param player the player to send it to
     */
    public void send(Player player){
        Reflector.Packets.sendPacket(player, packetShallowClone);
    }
}
