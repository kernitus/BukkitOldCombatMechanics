package kernitus.plugin.OldCombatMechanics.utilities.teams;

import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class TeamPacket {
    private Object packetShallowClone;

    TeamPacket(Object packet){
        this.packetShallowClone = packet;
    }

    public TeamPacket(){
        this(TeamUtils.createTeamNMSPacket(
                TeamAction.CREATE, CollisionRule.NEVER, "placeholder", Collections.emptyList()
        ));
    }

    /**
     * Adjusts this team packet
     *
     * @param updatePacket the sent team packet
     */
    public void adjustToUpdate(Object updatePacket){
        switch(TeamUtils.getPacketAction(updatePacket)){
            case CREATE:
                cloneFrom(updatePacket);
                break;
            case DISBAND:
                packetShallowClone = null;
                break;
            case UPDATE:
                cloneFrom(updatePacket);
                break;
            case ADD_PLAYER:
                adjustToPlayerAdded(updatePacket);
                break;
            case REMOVE_PLAYER:
                adjustToPlayerRemoved(updatePacket);
                break;
        }
    }

    /**
     * Checks if this team still exists.
     *
     * @return true if this team still exists
     */
    public boolean teamExists(){
        return packetShallowClone != null;
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
     * Sends the packet to the given player.
     *
     * @param player the player to send it to
     */
    public void send(Player player){
        Reflector.Packets.sendPacket(player, packetShallowClone);
    }

    private void adjustToPlayerAdded(Object updatePacket){
        Set<String> newMembers = new HashSet<>(TeamUtils.getTeamMembers(packetShallowClone));

        newMembers.addAll(TeamUtils.getTeamMembers(updatePacket));

        TeamUtils.setTeamMembers(packetShallowClone, newMembers);
    }

    private void adjustToPlayerRemoved(Object updatePacket){
        Set<String> newMembers = new HashSet<>(TeamUtils.getTeamMembers(packetShallowClone));
        newMembers.removeAll(TeamUtils.getTeamMembers(updatePacket));

        TeamUtils.setTeamMembers(packetShallowClone, newMembers);
    }

    private void cloneFrom(Object packet){
        try{
            for(Field declaredField : packet.getClass().getDeclaredFields()){
                declaredField.setAccessible(true);
                declaredField.set(packetShallowClone, declaredField.get(packet));
            }
        } catch(IllegalAccessException e){
            throw new RuntimeException("Error cloning packet", e);
        }
    }
}
