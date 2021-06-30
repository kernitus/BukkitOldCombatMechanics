package kernitus.plugin.OldCombatMechanics.utilities.packet.team;

import kernitus.plugin.OldCombatMechanics.utilities.packet.PacketHelper;
import kernitus.plugin.OldCombatMechanics.utilities.packet.PacketHelper.PacketType;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.type.ClassType;
import kernitus.plugin.OldCombatMechanics.utilities.teams.CollisionRule;
import kernitus.plugin.OldCombatMechanics.utilities.teams.TeamAction;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.stream.Collectors;

public class V17TeamPacket extends TeamPacket {

    protected V17TeamPacket(Object nmsPacket){
        super(nmsPacket);
    }

    @Override
    public TeamPacket withCollisionRule(CollisionRule collisionRule){
        return new V17TeamPacket(PacketAccess.setCollisionRule(getNmsPacket(), collisionRule));
    }

    @Override
    public Collection<String> getPlayerNames(){
        return PacketAccess.getPlayerNames(getNmsPacket());
    }

    @Override
    public TeamPacket withAction(TeamAction action){
        return new V17TeamPacket(PacketAccess.setAction(getNmsPacket(), action));
    }

    @Override
    public TeamAction getAction(){
        return PacketAccess.getAction(getNmsPacket());
    }

    @Override
    public String getName(){
        return PacketAccess.getName(getNmsPacket());
    }

    public static TeamPacket create(TeamAction action, CollisionRule collisionRule, String name, Collection<Player> players){
        return new V17TeamPacket(PacketAccess.createTeamPacket(action, collisionRule, name, players));
    }

    private static class PacketAccess {
        private static final Field fieldPlayerNames;
        private static final Field fieldAction;
        private static final Field fieldName;
        private static final Field fieldDataOptional;
        private static final Constructor<?> constructorTeamPacket;
        private static final Constructor<?> constructorDataClass;
        private static final Constructor<?> constructorScoreboard;
        private static final Constructor<?> constructorScoreboardTeam;

        static{
            Class<?> packetClass = PacketHelper.getPacketClass(PacketType.PlayOut, "ScoreboardTeam");
            Class<?> dataClass = Reflector.getClass(packetClass.getName() + "$b");

            fieldPlayerNames = Reflector.getInaccessibleField(packetClass, "j");
            fieldAction = Reflector.getInaccessibleField(packetClass, "h");
            fieldName = Reflector.getInaccessibleField(packetClass, "i");
            fieldDataOptional = Reflector.getFieldByType(packetClass, "Optional");

            constructorTeamPacket = Reflector.getConstructor(packetClass, 4);

            Class<?> scoreboardTeamClass = Reflector.getClass(ClassType.NMS, "world.scores.ScoreboardTeam");
            Class<?> scoreboardClass = Reflector.getClass(ClassType.NMS, "world.scores.Scoreboard");

            constructorDataClass = Reflector.getConstructor(dataClass, scoreboardTeamClass.getSimpleName());
            constructorScoreboard = Reflector.getConstructor(scoreboardClass, 0);
            constructorScoreboardTeam = Reflector.getConstructor(scoreboardTeamClass, 2);
        }

        public static TeamAction getAction(Object packet){
            return Reflector.getUnchecked(() -> TeamAction.fromId((Integer) fieldAction.get(packet)));
        }

        public static Object setAction(Object packet, TeamAction action){
            return Reflector.getUnchecked(() -> constructorTeamPacket.newInstance(
                    getName(packet),
                    action.getMinecraftId(),
                    fieldDataOptional.get(packet),
                    getPlayerNames(packet)
            ));
        }

        public static String getName(Object packet){
            return Reflector.getUnchecked(() -> (String) fieldName.get(packet));
        }

        public static Collection<String> getPlayerNames(Object packet){
            @SuppressWarnings("unchecked")
            Collection<String> identifiers = Reflector.getUnchecked(() -> (Collection<String>) fieldPlayerNames.get(packet));

            if(identifiers == null){
                return Collections.emptyList();
            }

            return identifiers;
        }

        public static Object setCollisionRule(Object packet, CollisionRule rule){
            return Reflector.getUnchecked(() -> {
                Object data;

                Optional<?> structOptional = (Optional<?>) fieldDataOptional.get(packet);
                if(structOptional.isPresent()){
                    data = constructorDataClass.newInstance(
                            ScoreboardTeamWither.from(structOptional.get())
                                    .withCollisionRule(rule)
                                    .build()
                    );
                } else {
                    Object scoreboardTeam = createScoreboardTeam(getName(packet));
                    data = constructorDataClass.newInstance(
                            ScoreboardTeamWither.from(constructorDataClass.newInstance(scoreboardTeam))
                                    .withCollisionRule(rule)
                                    .build()
                    );
                }


                return constructorTeamPacket.newInstance(
                        getName(packet),
                        getAction(packet).getMinecraftId(),
                        Optional.of(data),
                        getPlayerNames(packet)
                );
            });
        }

        public static Object createTeamPacket(TeamAction action, CollisionRule collisionRule, String name,
                                              Collection<Player> players){
            Object packet = Reflector.getUnchecked(() -> constructorTeamPacket.newInstance(
                    name,
                    action.getMinecraftId(),
                    Optional.empty(),
                    players.stream().map(Player::getName).collect(Collectors.toList())
            ));

            return setCollisionRule(packet, collisionRule);
        }

        static Object createScoreboardTeam(String name){
            return Reflector.getUnchecked(() -> {
                Object scoreboard = constructorScoreboard.newInstance();
                return constructorScoreboardTeam.newInstance(scoreboard, name);
            });
        }
    }

    private static class ScoreboardTeamWither {
        private static final Class<?> SCOREBOARD_TEAM_CLASS = Reflector.getClass(ClassType.NMS, "world.scores.ScoreboardTeam");
        private static final Method setDisplayName = Reflector.getMethod(SCOREBOARD_TEAM_CLASS, "setDisplayName");
        private static final Method setAllowFriendlyFire = Reflector.getMethod(SCOREBOARD_TEAM_CLASS, "setAllowFriendlyFire");
        private static final Method setCanSeeFriendlyInvisibles = Reflector.getMethod(SCOREBOARD_TEAM_CLASS, "setCanSeeFriendlyInvisibles");
        private static final Method setNameTagVisibility = Reflector.getMethod(SCOREBOARD_TEAM_CLASS, "setNameTagVisibility");
        private static final Method setCollisionRule = Reflector.getMethod(SCOREBOARD_TEAM_CLASS, "setCollisionRule");
        private static final Method setColor = Reflector.getMethod(SCOREBOARD_TEAM_CLASS, "setColor");
        private static final Method setPrefix = Reflector.getMethod(SCOREBOARD_TEAM_CLASS, "setPrefix");
        private static final Method setSuffix = Reflector.getMethod(SCOREBOARD_TEAM_CLASS, "setSuffix");

        private Object displayName;
        private int packOptionData;
        private String nameTagVisibility;
        private String collisionRule;
        private Object color;
        private Object prefix;
        private Object suffix;

        public ScoreboardTeamWither(Object displayName, int packOptionData, String nameTagVisibility,
                                    String collisionRule, Object color, Object prefix, Object suffix){
            this.displayName = displayName;
            this.packOptionData = packOptionData;
            this.nameTagVisibility = nameTagVisibility;
            this.collisionRule = collisionRule;
            this.color = color;
            this.prefix = prefix;
            this.suffix = suffix;
        }

        public ScoreboardTeamWither withCollisionRule(CollisionRule collisionRule){
            this.collisionRule = collisionRule.getName();
            return this;
        }

        public Object build(){
            Object team = PacketAccess.createScoreboardTeam("ocm-dummy");
            Reflector.invokeMethod(setDisplayName, team, displayName);
            Reflector.invokeMethod(setAllowFriendlyFire, team, (packOptionData & 0b1) != 0);
            Reflector.invokeMethod(setCanSeeFriendlyInvisibles, team, (packOptionData & 0b10) != 0);
            Reflector.invokeMethod(setNameTagVisibility, team, OptionalDataClassHelper.parseNameTagVisibility(nameTagVisibility));
            Reflector.invokeMethod(setCollisionRule, team, OptionalDataClassHelper.parseTeamPush(collisionRule));
            Reflector.invokeMethod(setColor, team, color);
            Reflector.invokeMethod(setPrefix, team, prefix);
            Reflector.invokeMethod(setSuffix, team, suffix);
            return team;
        }

        public static ScoreboardTeamWither from(Object dataClass){
            return new ScoreboardTeamWither(
                    OptionalDataClassHelper.getDisplayName(dataClass),
                    OptionalDataClassHelper.getPackOptionData(dataClass),
                    OptionalDataClassHelper.getNameTagVisibility(dataClass),
                    OptionalDataClassHelper.getCollisionRule(dataClass),
                    OptionalDataClassHelper.getColor(dataClass),
                    OptionalDataClassHelper.getPrefix(dataClass),
                    OptionalDataClassHelper.getSuffix(dataClass)
            );
        }
    }

    private static class OptionalDataClassHelper {
        private static final Class<?> PACKET_CLASS = PacketHelper.getPacketClass(PacketType.PlayOut, "ScoreboardTeam");
        private static final Class<?> DATA_CLASS = Reflector.getClass(PACKET_CLASS.getName() + "$b");
        private static final Class<?> NAME_TAG_VISIBILITY_CLASS = Reflector.getClass(
                ClassType.NMS,
                "world.scores.ScoreboardTeamBase$EnumNameTagVisibility"
        );
        private static final Class<?> TEAM_PUSH_CLASS = Reflector.getClass(
                ClassType.NMS,
                "world.scores.ScoreboardTeamBase$EnumTeamPush"
        );

        private static final Method getDisplayName = Reflector.getMethod(DATA_CLASS, "a", 0);
        private static final Method getPackOptionData = Reflector.getMethod(DATA_CLASS, "b", 0);
        private static final Method getNameTagVisibility = Reflector.getMethod(DATA_CLASS, "d", 0);
        private static final Method getCollisionRule = Reflector.getMethod(DATA_CLASS, "e", 0);
        private static final Method getColor = Reflector.getMethod(DATA_CLASS, "c", 0);
        private static final Method getPrefix = Reflector.getMethod(DATA_CLASS, "f", 0);
        private static final Method getSuffix = Reflector.getMethod(DATA_CLASS, "g", 0);

        private static final Method nameTagVisibilityValueOf = Reflector.getMethod(NAME_TAG_VISIBILITY_CLASS, "a", 1);
        private static final Method teamPushValueOf = Reflector.getMethod(TEAM_PUSH_CLASS, "a", 1);

        static Object getDisplayName(Object dataClass){
            return Reflector.invokeMethod(getDisplayName, dataClass);
        }

        static int getPackOptionData(Object dataClass){
            return Reflector.invokeMethod(getPackOptionData, dataClass);
        }

        static String getNameTagVisibility(Object dataClass){
            return Reflector.invokeMethod(getNameTagVisibility, dataClass);
        }

        static String getCollisionRule(Object dataClass){
            return Reflector.invokeMethod(getCollisionRule, dataClass);
        }

        static Object getColor(Object dataClass){
            return Reflector.invokeMethod(getColor, dataClass);
        }

        static Object getPrefix(Object dataClass){
            return Reflector.invokeMethod(getPrefix, dataClass);
        }

        static Object getSuffix(Object dataClass){
            return Reflector.invokeMethod(getSuffix, dataClass);
        }

        static Object parseNameTagVisibility(String asString){
            return Reflector.invokeMethod(nameTagVisibilityValueOf, null, asString);
        }

        static Object parseTeamPush(String asString){
            return Reflector.invokeMethod(teamPushValueOf, null, asString);
        }
    }
}
