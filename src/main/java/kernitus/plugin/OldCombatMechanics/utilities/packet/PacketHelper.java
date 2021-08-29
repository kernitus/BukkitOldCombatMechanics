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
     * Some plugins send their own packets for various reasons. While this might cause incompatibilties with a variety
     * of other plugins including OCM, we can't change what other people do. The plugins whitelisted here intentionally
     * send such packets and we should forward them. Any issues that might arise from it are out of scope of OCM support.
     *
     * @param object the packet object to check
     * @return true if the packet is allowed to proceed even if it is not an NMS packet
     */
    public static boolean isWhitelistedNonNmsPacket(Object object){
        if(object == null){
            return false;
        }
        return object.getClass().getName().startsWith("org.mcnative.runtime.api.protocol.packet.type");
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
