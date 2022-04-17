package kernitus.plugin.OldCombatMechanics.utilities.packet;

import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.type.ClassType;

public class PacketHelper {
    private static final Class<?> NMS_PACKET_CLASS = Reflector.getClass(ClassType.NMS, "network.protocol.Packet");

    public static Class<?> getPacketClass(PacketType type, String name){
        return Reflector.getClass(ClassType.NMS, "network.protocol.game.Packet" + type.prefix + name);
    }

    /**
     * Checks if a given object is an NMS packet.
     *
     * @param object the object to check
     * @return true if it is an HMS packet
     */
    public static boolean isNmsPacket(Object object){
        if(object == null){
            return false;
        }
        return Reflector.inheritsFrom(object.getClass(), NMS_PACKET_CLASS);
    }

    /**
     * Wraps a nms packet in a trivial {@link ImmutablePacket}.
     *
     * @param nmsPacket the nms packet to wrap
     * @return the wrapping {@link ImmutablePacket}
     */
    public static ImmutablePacket wrap(Object nmsPacket){
        return () -> nmsPacket;
    }

    /**
     * The type of a packet (in / out).
     */
    public enum PacketType {
        PlayOut("PlayOut"), PlayIn("PlayIn");

        public String prefix;

        PacketType(String prefix){
            this.prefix = prefix;
        }
    }
}
