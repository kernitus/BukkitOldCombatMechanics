package kernitus.plugin.OldCombatMechanics.utilities.teams;

import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.type.ClassType;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Contains useful methods when dealing with minecraft scoreboard teams.
 */
public class TeamUtils {

    private static final VersionData VERSION_DATA = new VersionData();

    /**
     * Sets the collision rule of the packet.
     *
     * @param packet        the packet to set it for
     * @param collisionRule the collision rule
     */
    public static void setCollisionRule(Object packet, CollisionRule collisionRule){
        try{
            VERSION_DATA.getFieldCollisionRule().set(packet, collisionRule.getName());
        } catch(IllegalAccessException e){
            // should not happen if the getting works
            throw new RuntimeException("Error setting collision field", e);
        }
    }

    /**
     * Returns the action of a given packet.
     *
     * @param packet the packet
     * @return the action for the packet
     */
    public static TeamAction getPacketAction(Object packet){
        try{
            return TeamAction.fromId((Integer) VERSION_DATA.getFieldAction().get(packet));
        } catch(IllegalAccessException e){
            // should not happen if the getting works
            throw new RuntimeException("Error getting action field", e);
        }
    }

    /**
     * Sets the action of a given packet.
     *
     * @param packet the packet
     * @param action the new action
     */
    public static void setPacketAction(Object packet, TeamAction action){
        try{
            VERSION_DATA.getFieldAction().set(packet, action.getMinecraftId());
        } catch(IllegalAccessException e){
            // should not happen if the getting works
            throw new RuntimeException("Error setting action field", e);
        }
    }

    /**
     * Checks if a team packet targets a player.
     *
     * @param packet the packet in question
     * @param player the player to check for
     * @return true if the packet targets the player
     */
    public static boolean targetsPlayer(Object packet, Player player){
        Collection<String> teamMembers = getTeamMembers(packet);
        return teamMembers != null && teamMembers.contains(player.getName());
    }

    /**
     * Creates a new team packet and sends it to the player. The team will have a random name.
     *
     * @param player        the player to send it to
     * @param collisionRule the collision rule to use
     */
    public static TeamPacket craftTeamCreatePacket(Player player, CollisionRule collisionRule){
        String teamName = UUID.randomUUID().toString().substring(0, 15);

        Object teamPacket = VERSION_DATA.createTeamPacket(
                TeamAction.CREATE,
                collisionRule,
                teamName,
                Collections.singletonList(player)
        );

        return new TeamPacket(teamPacket);
    }

    /**
     * Returns all team members in the given packet.
     *
     * @param packet the packet
     * @return returns all team members
     */
    static Collection<String> getTeamMembers(Object packet){
        try{
            @SuppressWarnings("unchecked")
            Collection<String> identifiers = (Collection<String>) VERSION_DATA.getFieldPlayerNames().get(packet);

            if (identifiers == null) {
                return Collections.emptyList();
            }

            return identifiers;
        } catch(IllegalAccessException e){
            // should not happen if the getting works
            throw new RuntimeException("Error getting players", e);
        }
    }

    /**
     * Sets the team members.
     *
     * @param packet     the packet
     * @param newMembers the new team members
     */
    static void setTeamMembers(Object packet, Collection<?> newMembers){
        try{
            VERSION_DATA.getFieldPlayerNames().set(packet, newMembers);
        } catch(IllegalAccessException e){
            throw new RuntimeException("Error setting members", e);
        }
    }

    /**
     * Creates a new team packet with the given data
     *
     * @param action        the action to use for the new packet
     * @param collisionRule the collision rule to use
     * @param name          the team name
     * @param players       the players onm the team
     */
    static Object createTeamNMSPacket(TeamAction action, CollisionRule collisionRule, String name,
                                      Collection<Player> players){
        return VERSION_DATA.createTeamPacket(action, collisionRule, name, players);
    }

    /**
     * Contains version specific data. Currently it is just static because the structure of the packet hasn't
     * changed yet.
     */
    private static class VersionData {
        private final Field fieldCollisionRule;
        private final Field fieldPlayerNames;
        private final Field fieldAction;
        private final Field fieldName;
        private final Constructor<?> constructorTeamPacket;

        VersionData(){
            Class<?> packetClass = Reflector.getClass(ClassType.NMS, "PacketPlayOutScoreboardTeam");
            this.fieldCollisionRule = Reflector.getInaccessibleField(packetClass, "f");
            this.fieldPlayerNames = Reflector.getInaccessibleField(packetClass, "h");
            this.fieldAction = Reflector.getInaccessibleField(packetClass, "i");
            this.fieldName = Reflector.getInaccessibleField(packetClass, "a");

            this.constructorTeamPacket = Reflector.getConstructor(packetClass, 0);
        }

        Field getFieldCollisionRule(){
            return fieldCollisionRule;
        }

        Field getFieldPlayerNames(){
            return fieldPlayerNames;
        }

        Field getFieldAction(){
            return fieldAction;
        }

        Object createTeamPacket(TeamAction action, CollisionRule collisionRule, String name,
                                Collection<Player> players){
            try{
                Object packet = constructorTeamPacket.newInstance();

                getFieldPlayerNames().set(packet, players.stream().map(Player::getName).collect(Collectors.toList()));
                getFieldCollisionRule().set(packet, collisionRule.getName());
                getFieldAction().set(packet, action.getMinecraftId());
                fieldName.set(packet, name);

                return packet;
            } catch(ReflectiveOperationException e){
                throw new RuntimeException("Error creating team packet", e);
            }
        }
    }
}