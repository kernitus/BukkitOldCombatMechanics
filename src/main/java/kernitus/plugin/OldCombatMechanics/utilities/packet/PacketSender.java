package kernitus.plugin.OldCombatMechanics.utilities.packet;

import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.type.ClassType;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * A Packet sender
 */
public class PacketSender {
    private static final PacketSender instance = new PacketSender();

    private static final Method GET_HANDLE;
    private static final Method SEND_PACKET;
    private static final Field PLAYER_CONNECTION_FIELD;

    static{
        Class<?> craftPlayer = Reflector.getClass(ClassType.CRAFTBUKKIT, "entity.CraftPlayer");
        Class<?> playerConnection = Reflector.getClass(ClassType.NMS, "server.network.PlayerConnection");
        Class<?> entityPlayer = Reflector.getClass(ClassType.NMS, "server.level.EntityPlayer");

        GET_HANDLE = Reflector.getMethod(craftPlayer, "getHandle");
        SEND_PACKET = Reflector.getMethod(playerConnection, "sendPacket");
        PLAYER_CONNECTION_FIELD = Reflector.getFieldByType(entityPlayer, "PlayerConnection");
    }

    private PacketSender(){
    }

    /**
     * @return The Instance of the PacketSender
     */
    public static PacketSender getInstance(){
        return instance;
    }

    /**
     * Sends a packet to a Player
     *
     * @param packet The {@link ImmutablePacket} to send
     * @param player The Player to send it to
     */
    public void sendPacket(ImmutablePacket packet, Player player){
        Reflector.invokeMethod(SEND_PACKET, getConnection(player), packet.getNmsPacket());
    }

    /**
     * Returns the Player's PlayerConnection
     *
     * @param player The Player to get the Connection for
     * @return The Player's connection
     */
    public Object getConnection(Player player){
        Object handle = Reflector.invokeMethod(GET_HANDLE, player);

        return Reflector.getFieldValue(PLAYER_CONNECTION_FIELD, handle);
    }
}
