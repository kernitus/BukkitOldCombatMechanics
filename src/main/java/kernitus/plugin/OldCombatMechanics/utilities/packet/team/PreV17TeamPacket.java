package kernitus.plugin.OldCombatMechanics.utilities.packet.team;

import kernitus.plugin.OldCombatMechanics.utilities.packet.PacketHelper;
import kernitus.plugin.OldCombatMechanics.utilities.packet.PacketHelper.PacketType;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector;
import kernitus.plugin.OldCombatMechanics.utilities.teams.CollisionRule;
import kernitus.plugin.OldCombatMechanics.utilities.teams.TeamAction;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

public class PreV17TeamPacket extends TeamPacket {

    private static final PacketAccess PACKET_ACCESS = new PacketAccess();

    protected PreV17TeamPacket(Object nmsPacket){
        super(nmsPacket);
    }

    @Override
    public TeamPacket withCollisionRule(CollisionRule collisionRule){
        PACKET_ACCESS.setCollisionRule(getNmsPacket(), collisionRule);
        return this;
    }

    @Override
    public Collection<String> getPlayerNames(){
        return PACKET_ACCESS.getPlayerNames(getNmsPacket());
    }

    @Override
    public TeamPacket withAction(TeamAction action){
        PACKET_ACCESS.setAction(getNmsPacket(), action);
        return this;
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
        return new PreV17TeamPacket(PACKET_ACCESS.createTeamPacket(action, collisionRule, name, players));
    }

    private static class PacketAccess {
        private final Field fieldCollisionRule;
        private final Field fieldPlayerNames;
        private final Field fieldAction;
        private final Field fieldName;
        private final Constructor<?> constructorTeamPacket;

        private PacketAccess(){
            Class<?> packetClass = PacketHelper.getPacketClass(PacketType.PlayOut, "ScoreboardTeam");
            this.fieldCollisionRule = Reflector.getInaccessibleField(packetClass, "f");
            this.fieldPlayerNames = Reflector.getInaccessibleField(packetClass, "h");
            this.fieldAction = Reflector.getInaccessibleField(packetClass, "i");
            this.fieldName = Reflector.getInaccessibleField(packetClass, "a");

            this.constructorTeamPacket = Reflector.getConstructor(packetClass, 0);
        }

        public void setCollisionRule(Object packet, CollisionRule rule){
            Reflector.doUnchecked(() -> fieldCollisionRule.set(packet, rule.getName()));
        }

        public TeamAction getAction(Object packet){
            return Reflector.getUnchecked(() -> TeamAction.fromId((Integer) fieldAction.get(packet)));
        }

        public void setAction(Object packet, TeamAction action){
            Reflector.doUnchecked(() -> fieldAction.set(packet, action.getMinecraftId()));
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

        public Object createTeamPacket(TeamAction action, CollisionRule collisionRule, String name,
                                       Collection<Player> players){
            Object packet = Reflector.getUnchecked(constructorTeamPacket::newInstance);

            setCollisionRule(packet, collisionRule);
            setAction(packet, action);

            Reflector.doUnchecked(() -> fieldPlayerNames.set(
                    packet, players.stream()
                            .map(Player::getName)
                            .collect(Collectors.toList())
            ));
            Reflector.doUnchecked(() -> fieldName.set(packet, name));

            return packet;
        }
    }

}
