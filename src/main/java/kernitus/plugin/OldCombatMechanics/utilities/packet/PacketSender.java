package kernitus.plugin.OldCombatMechanics.utilities.packet;

import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.type.ClassType;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Objects;

/**
 * A Packet sender
 */
class PacketSender {

    private static Class<?> CRAFT_PLAYER;
    private static Class<?> PLAYER_CONNECTION;
    private static Class<?> ENTITY_PLAYER;

    private static Method GET_HANDLE;
    private static Method SEND_PACKET;
    private static Field PLAYER_CONNECTION_FIELD;

    private static boolean isSetup = false;

    private static final PacketSender instance = new PacketSender();

    static{
        try{
            CRAFT_PLAYER = Objects.requireNonNull(Reflector.getClass(ClassType.CRAFTBUKKIT, "entity.CraftPlayer"));
            PLAYER_CONNECTION = Objects.requireNonNull(Reflector.getClass(ClassType.NMS, "PlayerConnection"));
            ENTITY_PLAYER = Objects.requireNonNull(Reflector.getClass(ClassType.NMS, "EntityPlayer"));

            GET_HANDLE = Reflector.getMethod(CRAFT_PLAYER, "getHandle");
            SEND_PACKET = Reflector.getMethod(PLAYER_CONNECTION, "sendPacket");
            PLAYER_CONNECTION_FIELD = Reflector.getField(ENTITY_PLAYER, "playerConnection");

            isSetup = true;
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

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
        if(!isSetup) return;
        sendPacket(packet.getNMSPacket(), getConnection(player));
    }

    private void sendPacket(Object nmsPacket, Object playerConnection){
        if(!isSetup) return;

        Reflector.invokeMethod(SEND_PACKET, playerConnection, nmsPacket);
    }

    /**
     * Returns the Player's PlayerConnection
     *
     * @param player The Player to get the Connection for
     * @return The Player's connection
     */
    Object getConnection(Player player){
        if(!isSetup) return null;

        Object handle = Reflector.invokeMethod(GET_HANDLE, player);

        return Reflector.getFieldValue(PLAYER_CONNECTION_FIELD, handle);
    }
}
