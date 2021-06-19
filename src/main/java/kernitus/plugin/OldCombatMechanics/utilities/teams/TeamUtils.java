package kernitus.plugin.OldCombatMechanics.utilities.teams;

import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.type.PacketType;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Contains useful methods when dealing with minecraft scoreboard teams.
 */
public class TeamUtils {

    private static final AtomicInteger TEAM_NAME_COUNTER = new AtomicInteger();
    private static VersionData VERSION_DATA;

    private static boolean setup;

    static{
        try{
            VERSION_DATA = new VersionData();
            setup = true;
        } catch(Exception e){
            e.printStackTrace();
        }
    }

    public static boolean isSetup(){
        return setup;
    }

    private static void checkSetup() {
        if(!setup) {
            throw new IllegalStateException("TeamUtils failed to set up!");
        }
    }

    /**
     * Sets the collision rule of the packet.
     *
     * @param packet        the packet to set it for
     * @param collisionRule the collision rule
     */
    public static void setCollisionRule(Object packet, CollisionRule collisionRule){
        checkSetup();
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
        checkSetup();
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
        checkSetup();
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
        return getTeamMembers(packet).contains(player.getName());
    }

    /**
     * Creates a new team packet and sends it to the player. The team will have a random name.
     *
     * @param player        the player to send it to
     * @param collisionRule the collision rule to use
     */
    public static TeamPacket craftTeamCreatePacket(Player player, CollisionRule collisionRule){
        checkSetup();

        // Has the right size (10) and is unique
        String teamName = "OCM-" + TEAM_NAME_COUNTER.getAndIncrement() + "";

        Object teamPacket = VERSION_DATA.createTeamPacket(
                TeamAction.CREATE,
                collisionRule,
                teamName,
                Collections.singletonList(player)
        );

        return new TeamPacket(teamPacket);
    }

    /**
     * @param name the name of the team
     * @return true if the team was created by OCM
     */
    public static boolean isOcmTeam(String name){
        return name.startsWith("OCM-");
    }

    /**
     * Disbands a team.
     *
     * @param teamName the name of the team to disband
     * @param player   the player to disband it for
     */
    public static void disband(String teamName, Player player){
        checkSetup();
        Object nms = VERSION_DATA.createTeamPacket(
                TeamAction.DISBAND,
                CollisionRule.NEVER,
                teamName,
                Collections.singletonList(player)
        );
        new TeamPacket(nms).send(player);
    }

    /**
     * @param packet the packet to get it for
     * @return the team name
     */
    public static String getTeamName(Object packet){
        checkSetup();
        try{
            return (String) VERSION_DATA.getFieldName().get(packet);
        } catch(IllegalAccessException e){
            throw new RuntimeException("Error setting members", e);
        }
    }

    /**
     * Returns all team members in the given packet.
     *
     * @param packet the packet
     * @return returns all team members
     */
    public static Collection<String> getTeamMembers(Object packet){
        checkSetup();
        try{
            @SuppressWarnings("unchecked")
            Collection<String> identifiers = (Collection<String>) VERSION_DATA.getFieldPlayerNames().get(packet);

            if(identifiers == null){
                return Collections.emptyList();
            }

            return identifiers;
        } catch(IllegalAccessException e){
            // should not happen if the getting works
            throw new RuntimeException("Error getting players", e);
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
        checkSetup();
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
            Class<?> packetClass = Reflector.Packets.getPacket(PacketType.PlayOut, "ScoreboardTeam");
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

        Field getFieldName(){
            return fieldName;
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

    private enum FieldNames {
        Before_V17("f", "h", "i", "a"),
        // FIXME: This actually needs to unwrap the optional and look at "e". Additionally, if we want to set it
        V17("k.e", "j", "h", "i");

        private final String fieldCollisionRule;
        private final String fieldPlayerNames;
        private final String fieldAction;
        private final String fieldName;

        FieldNames(String fieldCollisionRule, String fieldPlayerNames, String fieldAction, String fieldName){
            this.fieldCollisionRule = fieldCollisionRule;
            this.fieldPlayerNames = fieldPlayerNames;
            this.fieldAction = fieldAction;
            this.fieldName = fieldName;
        }

        String getFieldCollisionRule(){
            return fieldCollisionRule;
        }
        String getFieldPlayerNames(){
            return fieldPlayerNames;
        }
        String getFieldAction(){
            return fieldAction;
        }
        String getFieldName(){
            return fieldName;
        }
    }
}
