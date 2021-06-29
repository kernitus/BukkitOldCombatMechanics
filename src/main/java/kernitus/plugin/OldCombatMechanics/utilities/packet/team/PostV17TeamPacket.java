package kernitus.plugin.OldCombatMechanics.utilities.packet.team;

import kernitus.plugin.OldCombatMechanics.utilities.packet.PacketHelper;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.type.ClassType;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.type.PacketType;
import kernitus.plugin.OldCombatMechanics.utilities.teams.CollisionRule;
import kernitus.plugin.OldCombatMechanics.utilities.teams.TeamAction;
import net.minecraft.EnumChatFormat;
import net.minecraft.network.chat.IChatBaseComponent;
import net.minecraft.network.protocol.game.PacketPlayOutScoreboardTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.ScoreboardTeam;
import net.minecraft.world.scores.ScoreboardTeamBase;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.stream.Collectors;

public class PostV17TeamPacket extends TeamPacket {

    private static final PacketAccess PACKET_ACCESS = new PacketAccess();

    protected PostV17TeamPacket(Object nmsPacket){
        super(nmsPacket);
    }

    @Override
    public TeamPacket withCollisionRule(CollisionRule collisionRule){
        return new PostV17TeamPacket(
                PACKET_ACCESS.setCollisionRule(getNmsPacket(), collisionRule)
        );
    }

    @Override
    public Collection<String> getPlayerNames(){
        return PACKET_ACCESS.getPlayerNames(getNmsPacket());
    }

    @Override
    public TeamPacket withAction(TeamAction action){
        return new PostV17TeamPacket(
                PACKET_ACCESS.setAction(getNmsPacket(), action)
        );
    }

    @Override
    public TeamAction getAction(){
        return PACKET_ACCESS.getAction(getNmsPacket());
    }

    @Override
    public String getName(){
        return PACKET_ACCESS.getName(getNmsPacket());
    }

    public static TeamPacket create(TeamAction action, CollisionRule collisionRule, String name, Collection<Player> players){
        return new PostV17TeamPacket(PACKET_ACCESS.createTeamPacket(action, collisionRule, name, players));
    }

    private static class PacketAccess {
        private final Field fieldPlayerNames;
        private final Field fieldAction;
        private final Field fieldName;
        private final Field fieldDataOptional;
        private final Constructor<?> constructorTeamPacket;
        private final Constructor<?> constructorDataClass;
        private final Constructor<?> constructorScoreboard;
        private final Constructor<?> constructorScoreboardTeam;

        private PacketAccess(){
            Class<?> packetClass = PacketHelper.getPacketClass(PacketType.PlayOut, "ScoreboardTeam");
            Class<?> dataClass = Reflector.getClass(packetClass.getName() + "$b");

            this.fieldPlayerNames = Reflector.getInaccessibleField(packetClass, "j");
            this.fieldAction = Reflector.getInaccessibleField(packetClass, "h");
            this.fieldName = Reflector.getInaccessibleField(packetClass, "i");
            this.fieldDataOptional = Reflector.getFieldByType(packetClass, "Optional");

            this.constructorTeamPacket = Reflector.getConstructor(packetClass, 4);

            Class<?> scoreboardTeamClass = Reflector.getClass(ClassType.NMS, "world.scores.ScoreboardTeam");
            Class<?> scoreboardClass = Reflector.getClass(ClassType.NMS, "world.scores.Scoreboard");

            constructorDataClass = Reflector.getConstructor(dataClass, scoreboardTeamClass.getSimpleName());
            constructorScoreboard = Reflector.getConstructor(scoreboardClass, 0);
            constructorScoreboardTeam = Reflector.getConstructor(scoreboardTeamClass, 2);
        }

        public TeamAction getAction(Object packet){
            return Reflector.getUnchecked(() -> TeamAction.fromId((Integer) fieldAction.get(packet)));
        }

        public Object setAction(Object packet, TeamAction action){
            return Reflector.getUnchecked(() -> constructorTeamPacket.newInstance(
                    getName(packet),
                    action.getMinecraftId(),
                    fieldDataOptional.get(packet),
                    getPlayerNames(packet)
            ));
        }

        public String getName(Object packet){
            return Reflector.getUnchecked(() -> (String) fieldName.get(packet));
        }

        public Collection<String> getPlayerNames(Object packet){
            @SuppressWarnings("unchecked")
            Collection<String> identifiers = Reflector.getUnchecked(() -> (Collection<String>) fieldPlayerNames.get(packet));

            if(identifiers == null){
                return Collections.emptyList();
            }

            return identifiers;
        }

        public Object setCollisionRule(Object packet, CollisionRule rule){
            return Reflector.getUnchecked(() -> {
                Object data;

                Optional<?> structOptional = (Optional<?>) fieldDataOptional.get(packet);
                if(structOptional.isPresent()){
                    data = constructorDataClass.newInstance(
                            ScoreboardTeamWither.from((PacketPlayOutScoreboardTeam.b) structOptional.get())
                                    .withCollisionRule(rule)
                                    .build()
                    );
                } else {
                    Object scoreboard = constructorScoreboard.newInstance();
                    Object scoreboardTeam = constructorScoreboardTeam.newInstance(scoreboard, getName(packet));
                    data = constructorDataClass.newInstance(
                            ScoreboardTeamWither.from(
                                    (PacketPlayOutScoreboardTeam.b) constructorDataClass.newInstance(scoreboardTeam)
                            )
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

        public Object createTeamPacket(TeamAction action, CollisionRule collisionRule, String name,
                                       Collection<Player> players){
            Object packet = Reflector.getUnchecked(() -> constructorTeamPacket.newInstance(
                    name,
                    action.getMinecraftId(),
                    Optional.empty(),
                    players.stream().map(Player::getName).collect(Collectors.toList())
            ));

            return setCollisionRule(packet, collisionRule);
        }
    }

    private static class ScoreboardTeamWither {
        private IChatBaseComponent displayName;
        private int packOptionData;
        private String nameTagVisibility;
        private String collisionRule;
        private EnumChatFormat color;
        private IChatBaseComponent prefix;
        private IChatBaseComponent suffix;

        public ScoreboardTeamWither(IChatBaseComponent displayName, int packOptionData, String nameTagVisibility,
                                    String collisionRule, EnumChatFormat color, IChatBaseComponent prefix,
                                    IChatBaseComponent suffix){
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

        public ScoreboardTeam build(){
            ScoreboardTeam team = new ScoreboardTeam(new Scoreboard(), "foo");
            team.setDisplayName(displayName);
            team.setAllowFriendlyFire((packOptionData & 0b1) != 0);
            team.setCanSeeFriendlyInvisibles((packOptionData & 0b10) != 0);
            team.setNameTagVisibility(ScoreboardTeamBase.EnumNameTagVisibility.a(nameTagVisibility));
            team.setCollisionRule(ScoreboardTeamBase.EnumTeamPush.a(collisionRule));
            team.setColor(color);
            team.setPrefix(prefix);
            team.setSuffix(suffix);
            return team;
        }

        public static ScoreboardTeamWither from(PacketPlayOutScoreboardTeam.b b){
            return new ScoreboardTeamWither(
                    b.a(),
                    b.b(),
                    b.d(),
                    b.e(),
                    b.c(),
                    b.f(),
                    b.g()
            );
        }
    }
}
