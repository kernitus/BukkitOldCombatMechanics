package kernitus.plugin.OldCombatMechanics.utilities.reflection.sweep;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.packet.Packet;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.type.PacketType;
import org.bukkit.Bukkit;

import java.lang.reflect.Field;
import java.util.logging.Level;

abstract class AbstractSweepPacketDetector implements SweepPacketDetector {

    static final Class<?> PACKET_CLASS = Reflector.Packets.getPacket(PacketType.PlayOut, "WorldParticles");

    /**
     * Checks if the type of the packet is correct.
     *
     * @param packet the packet
     * @return true if it is of the correct type
     */
    boolean isWrongPacketType(Packet packet){
        return packet.getPacketClass() != PACKET_CLASS;
    }

    /**
     * Returns the value of a given field, but does not set {@link Field#setAccessible(boolean)}.
     *
     * @param field  the field to get the value for
     * @param handle the handle
     * @return null if an error occurred, the result otherwise
     */
    Object tryGetField(Field field, Object handle){
        try{
            return field.get(handle);
        } catch(IllegalAccessException e){
            OCMMain.getInstance().getLogger().log(Level.INFO, "Error getting field " + field, e);
        }
        return null;
    }

    /**
     * Throws an exception indicating that the element wasn't found.
     *
     * @param name the name of the element
     */
    void throwNewElementNotFoundException(String name){
        throw new IllegalStateException(
                "Couldn't find " + name + ". Please report this on github. I am running server version "
                        + Bukkit.getServer().getVersion()
        );
    }
}
