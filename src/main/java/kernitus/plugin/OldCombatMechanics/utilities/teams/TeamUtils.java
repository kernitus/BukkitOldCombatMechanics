package kernitus.plugin.OldCombatMechanics.utilities.teams;

import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.type.ClassType;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.type.PacketType;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Contains useful methods when dealing with minecraft scoreboard teams.
 */
public class TeamUtils {

    private static final AtomicInteger TEAM_NAME_COUNTER = new AtomicInteger();
    private static PacketAccess PACKET_ACCESS;

    private static boolean setup;

    static{
        try{
            PACKET_ACCESS = new Postv17PacketAccess();
            setup = true;
        } catch(Exception e){
            e.printStackTrace();
        }
    }

    public static boolean isSetup(){
        return setup;
    }

    private static void checkSetup(){
        if(!setup){
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
            PACKET_ACCESS.setCollisionRule(packet, collisionRule);
        } catch(ReflectiveOperationException e){
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
            return PACKET_ACCESS.getAction(packet);
        } catch(ReflectiveOperationException e){
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
            PACKET_ACCESS.setAction(packet, action);
        } catch(ReflectiveOperationException e){
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

        Object teamPacket = createTeamNMSPacket(
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
        Object nms = createTeamNMSPacket(
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
            return PACKET_ACCESS.getName(packet);
        } catch(ReflectiveOperationException e){
            throw new RuntimeException("Error getting team name", e);
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
            return PACKET_ACCESS.getPlayerNames(packet);
        } catch(ReflectiveOperationException e){
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
        try{
            return PACKET_ACCESS.createTeamPacket(action, collisionRule, name, players);
        } catch(ReflectiveOperationException e){
            throw new RuntimeException("Couldn't create team packet");
        }
    }

    public interface PacketAccess {
        void setCollisionRule(Object packet, CollisionRule rule) throws ReflectiveOperationException;

        TeamAction getAction(Object packet) throws ReflectiveOperationException;

        void setAction(Object packet, TeamAction action) throws ReflectiveOperationException;

        String getName(Object packet) throws ReflectiveOperationException;

        Collection<String> getPlayerNames(Object packet) throws ReflectiveOperationException;

        Object createTeamPacket(TeamAction action, CollisionRule collisionRule, String name,
                                Collection<Player> players) throws ReflectiveOperationException;
    }

    private static class Prev17PacketAccess implements PacketAccess {
        private final Field fieldCollisionRule;
        private final Field fieldPlayerNames;
        private final Field fieldAction;
        private final Field fieldName;
        private final Constructor<?> constructorTeamPacket;

        private Prev17PacketAccess(){
            Class<?> packetClass = Reflector.Packets.getPacket(PacketType.PlayOut, "ScoreboardTeam");
            this.fieldCollisionRule = Reflector.getInaccessibleField(packetClass, "f");
            this.fieldPlayerNames = Reflector.getInaccessibleField(packetClass, "h");
            this.fieldAction = Reflector.getInaccessibleField(packetClass, "i");
            this.fieldName = Reflector.getInaccessibleField(packetClass, "a");

            this.constructorTeamPacket = Reflector.getConstructor(packetClass, 0);
        }

        @Override
        public void setCollisionRule(Object packet, CollisionRule rule) throws ReflectiveOperationException{
            fieldCollisionRule.set(packet, rule.getName());
        }

        @Override
        public TeamAction getAction(Object packet) throws ReflectiveOperationException{
            return TeamAction.fromId((Integer) fieldAction.get(packet));
        }

        @Override
        public void setAction(Object packet, TeamAction action) throws ReflectiveOperationException{
            fieldAction.set(packet, action.getMinecraftId());
        }

        @Override
        public String getName(Object packet) throws ReflectiveOperationException{
            return (String) fieldName.get(packet);
        }

        @Override
        public Collection<String> getPlayerNames(Object packet) throws ReflectiveOperationException{
            @SuppressWarnings("unchecked")
            Collection<String> identifiers = (Collection<String>) fieldPlayerNames.get(packet);

            if(identifiers == null){
                return Collections.emptyList();
            }

            return identifiers;
        }

        @Override
        public Object createTeamPacket(TeamAction action, CollisionRule collisionRule, String name,
                                       Collection<Player> players) throws ReflectiveOperationException{
            Object packet = constructorTeamPacket.newInstance();

            setCollisionRule(packet, collisionRule);
            setAction(packet, action);

            fieldPlayerNames.set(packet, players.stream().map(Player::getName).collect(Collectors.toList()));
            fieldName.set(packet, name);

            return packet;
        }
    }

    private static class Postv17PacketAccess implements PacketAccess {
        private final Field fieldPlayerNames;
        private final Field fieldAction;
        private final Field fieldName;
        private final Field fieldDataOptional;
        private final Field fieldDataCollisionRule;
        private final Constructor<?> constructorTeamPacket;
        private final Constructor<?> constructorDataClass;
        private final Constructor<?> constructorScoreboard;
        private final Constructor<?> constructorScoreboardTeam;

        private Postv17PacketAccess(){
            Class<?> packetClass = Reflector.Packets.getPacket(PacketType.PlayOut, "ScoreboardTeam");
            Class<?> dataClass = Reflector.getClass(packetClass.getName() + ".b");

            this.fieldPlayerNames = Reflector.getInaccessibleField(packetClass, "j");
            this.fieldAction = Reflector.getInaccessibleField(packetClass, "h");
            this.fieldName = Reflector.getInaccessibleField(packetClass, "i");
            this.fieldDataOptional = Reflector.getFieldByType(packetClass, "Optional");
            this.fieldDataCollisionRule = Reflector.getInaccessibleField(dataClass, "e");

            this.constructorTeamPacket = Reflector.getConstructor(packetClass, 4);

            Class<?> scoreboardTeamClass = Reflector.getClass(ClassType.NMS, "world.scores.ScoreboardTeam");
            Class<?> scoreboardClass = Reflector.getClass(ClassType.NMS, "world.scores.Scoreboard");

            constructorDataClass = Reflector.getConstructor(dataClass, scoreboardTeamClass.getSimpleName());
            constructorScoreboard = Reflector.getConstructor(scoreboardClass, 0);
            constructorScoreboardTeam = Reflector.getConstructor(scoreboardTeamClass, 2);
        }

        @Override
        public TeamAction getAction(Object packet) throws ReflectiveOperationException{
            return TeamAction.fromId((Integer) fieldAction.get(packet));
        }

        @Override
        public void setAction(Object packet, TeamAction action) throws ReflectiveOperationException{
            fieldAction.set(packet, action.getMinecraftId());
        }

        @Override
        public String getName(Object packet) throws ReflectiveOperationException{
            return (String) fieldName.get(packet);
        }

        @Override
        public Collection<String> getPlayerNames(Object packet) throws ReflectiveOperationException{
            @SuppressWarnings("unchecked")
            Collection<String> identifiers = (Collection<String>) fieldPlayerNames.get(packet);

            if(identifiers == null){
                return Collections.emptyList();
            }

            return identifiers;
        }

        @Override
        public void setCollisionRule(Object packet, CollisionRule rule) throws ReflectiveOperationException{
            Optional<?> structOptional = (Optional<?>) fieldDataOptional.get(packet);
            if(structOptional != null && structOptional.isPresent()){
                fieldDataCollisionRule.set(packet, rule.getName());
                return;
            }
            Object scoreboard = constructorScoreboard.newInstance();
            Object scoreboardTeam = constructorScoreboardTeam.newInstance(scoreboard, getTeamName(packet));
            Object dataClass = constructorDataClass.newInstance(scoreboardTeam);
            fieldDataCollisionRule.set(dataClass, rule.getName());
        }

        @Override
        public Object createTeamPacket(TeamAction action, CollisionRule collisionRule, String name,
                                       Collection<Player> players) throws ReflectiveOperationException{
            Object packet = constructorTeamPacket.newInstance(
                    name,
                    action.getMinecraftId(),
                    Optional.empty(),
                    players.stream().map(Player::getName).collect(Collectors.toList())
            );

            setCollisionRule(packet, collisionRule);

            return packet;
        }
    }
}
