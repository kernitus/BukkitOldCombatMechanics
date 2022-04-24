/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
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

    private static abstract class ScoreboardTeamMethods {
        protected static final Class<?> SCOREBOARD_TEAM_CLASS = Reflector.getClass(ClassType.NMS, "world.scores.ScoreboardTeam");
        private static final ScoreboardTeamMethods INSTANCE = selectInstance();

        protected Method setDisplayName;
        protected Method setAllowFriendlyFire;
        protected Method setCanSeeFriendlyInvisibles;
        protected Method setNameTagVisibility;
        protected Method setCollisionRule;
        protected Method setColor;
        protected Method setPrefix;
        protected Method setSuffix;

        public static ScoreboardTeamMethods getInstance(){
            return INSTANCE;
        }

        private static ScoreboardTeamMethods selectInstance(){
            if(Reflector.versionIsNewerOrEqualAs(1, 18, 0)){
                return new ScoreboardTeamMethodsV18();
            }
            return new ScoreboardTeamMethodsV17();
        }
    }

    private static class ScoreboardTeamMethodsV17 extends ScoreboardTeamMethods {

        public ScoreboardTeamMethodsV17(){
            setDisplayName = Reflector.getMethod(SCOREBOARD_TEAM_CLASS, "setDisplayName");
            setAllowFriendlyFire = Reflector.getMethod(SCOREBOARD_TEAM_CLASS, "setAllowFriendlyFire");
            setCanSeeFriendlyInvisibles = Reflector.getMethod(SCOREBOARD_TEAM_CLASS, "setCanSeeFriendlyInvisibles");
            setNameTagVisibility = Reflector.getMethod(SCOREBOARD_TEAM_CLASS, "setNameTagVisibility");
            setCollisionRule = Reflector.getMethod(SCOREBOARD_TEAM_CLASS, "setCollisionRule");
            setColor = Reflector.getMethod(SCOREBOARD_TEAM_CLASS, "setColor");
            setPrefix = Reflector.getMethod(SCOREBOARD_TEAM_CLASS, "setPrefix");
            setSuffix = Reflector.getMethod(SCOREBOARD_TEAM_CLASS, "setSuffix");
        }
    }

    private static class ScoreboardTeamMethodsV18 extends ScoreboardTeamMethods {

        public ScoreboardTeamMethodsV18(){
            setDisplayName = Reflector.getMethod(SCOREBOARD_TEAM_CLASS, "a", "IChatBaseComponent");
            setAllowFriendlyFire = Reflector.getMethod(SCOREBOARD_TEAM_CLASS, "a", "boolean");
            setCanSeeFriendlyInvisibles = Reflector.getMethod(SCOREBOARD_TEAM_CLASS, "b", "boolean");
            setNameTagVisibility = Reflector.getMethod(SCOREBOARD_TEAM_CLASS, "a", "EnumNameTagVisibility");
            setCollisionRule = Reflector.getMethod(SCOREBOARD_TEAM_CLASS, "a", "EnumTeamPush");
            setColor = Reflector.getMethod(SCOREBOARD_TEAM_CLASS, "a", "EnumChatFormat");
            setPrefix = Reflector.getMethod(SCOREBOARD_TEAM_CLASS, "b", "IChatBaseComponent");
            setSuffix = Reflector.getMethod(SCOREBOARD_TEAM_CLASS, "c", "IChatBaseComponent");
        }
    }

    private static class ScoreboardTeamWither {
        private final Object displayName;
        private final int packOptionData;
        private final String nameTagVisibility;
        private String collisionRule;
        private final Object color;
        private final Object prefix;
        private final Object suffix;

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
            Reflector.invokeMethod(ScoreboardTeamMethods.getInstance().setDisplayName, team, displayName);
            Reflector.invokeMethod(
                    ScoreboardTeamMethods.getInstance().setAllowFriendlyFire,
                    team,
                    (packOptionData & 0b1) != 0
            );
            Reflector.invokeMethod(
                    ScoreboardTeamMethods.getInstance().setCanSeeFriendlyInvisibles,
                    team,
                    (packOptionData & 0b10) != 0
            );
            Reflector.invokeMethod(
                    ScoreboardTeamMethods.getInstance().setNameTagVisibility,
                    team,
                    OptionalDataClassHelper.parseNameTagVisibility(nameTagVisibility)
            );
            Reflector.invokeMethod(
                    ScoreboardTeamMethods.getInstance().setCollisionRule,
                    team,
                    OptionalDataClassHelper.parseTeamPush(collisionRule)
            );
            Reflector.invokeMethod(ScoreboardTeamMethods.getInstance().setColor, team, color);
            Reflector.invokeMethod(ScoreboardTeamMethods.getInstance().setPrefix, team, prefix);
            Reflector.invokeMethod(ScoreboardTeamMethods.getInstance().setSuffix, team, suffix);
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
