package gvlfm78.plugin.OldCombatMechanics.utilities.packet;

import gvlfm78.plugin.OldCombatMechanics.utilities.reflection.Reflector;
import gvlfm78.plugin.OldCombatMechanics.utilities.reflection.type.ClassType;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Objects;

/**
 * A Packet sender
 */
class PacketSender {

    private static final Class<?> CRAFT_PLAYER =
            Objects.requireNonNull(Reflector.getClass(ClassType.CRAFTBUKKIT, "entity.CraftPlayer"));
    private static final Class<?> PLAYER_CONNECTION =
            Objects.requireNonNull(Reflector.getClass(ClassType.NMS, "PlayerConnection"));
    private static final Class<?> ENTITY_PLAYER =
            Objects.requireNonNull(Reflector.getClass(ClassType.NMS, "EntityPlayer"));


    private static final Method GET_HANDLE = Reflector.getMethod(
            CRAFT_PLAYER, "getHandle"
    );
    private static final Method SEND_PACKET = Reflector.getMethod(
            PLAYER_CONNECTION, "sendPacket"
    );
    private static final Field PLAYER_CONNECTION_FIELD = Reflector.getField(
            ENTITY_PLAYER, "playerConnection"
    );

    private static final PacketSender instance = new PacketSender();

    private PacketSender(){
    }

    /**
     * @return The Instance of the PacketSender
     */
    static PacketSender getInstance(){
        return instance;
    }

    /**
     * Sends a packet to a Player
     *
     * @param packet The {@link Packet} to send
     * @param player The Player to send it to
     */
    void sendPacket(Packet packet, Player player){
        sendPacket(packet.getNMSPacket(), getConnection(player));
    }

    private void sendPacket(Object nmsPacket, Object playerConnection){
        Reflector.invokeMethod(SEND_PACKET, playerConnection, nmsPacket);
    }

    /**
     * Returns the Player's PlayerConnection
     *
     * @param player The Player to get the Connection for
     * @return The Player's connection
     */
    Object getConnection(Player player){
        Object handle = Reflector.invokeMethod(GET_HANDLE, player);

        return Reflector.getFieldValue(PLAYER_CONNECTION_FIELD, handle);
    }
}
