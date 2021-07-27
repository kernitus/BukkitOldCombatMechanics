package kernitus.plugin.OldCombatMechanics.utilities.packet.mitm;

import kernitus.plugin.OldCombatMechanics.utilities.packet.ImmutablePacket;
import org.bukkit.entity.Player;

/**
 * A packet event
 */
public class PacketEvent {

    private ImmutablePacket packet;
    private final Player player;
    private boolean cancelled;
    private final ConnectionDirection direction;

    /**
     * @param packet    The packet
     * @param direction The direction the packet is travelling
     * @param player    The involved Player
     */
    protected PacketEvent(ImmutablePacket packet, ConnectionDirection direction, Player player){
        this.packet = packet;
        this.direction = direction;
        this.player = player;

        this.cancelled = false;
    }

    /**
     * Returns the packet
     *
     * @return The Packet
     */
    public ImmutablePacket getPacket(){
        return packet;
    }

    /**
     * Sets the new packet
     *
     * @param packet The new packet
     */
    public void setPacket(ImmutablePacket packet){
        this.packet = packet;
    }

    /**
     * Checks if the event is cancelled
     *
     * @return True if the event is cancelled
     */
    @SuppressWarnings("WeakerAccess")
    public boolean isCancelled(){
        return cancelled;
    }

    /**
     * Sets the events cancelled status.
     *
     * @param cancelled if true, the event will be cancelled.
     */
    public void setCancelled(boolean cancelled){
        this.cancelled = cancelled;
    }

    /**
     * Returns the connection direction
     *
     * @return The Direction the packet was travelling
     */
    @SuppressWarnings("unused")
    public ConnectionDirection getDirection(){
        return direction;
    }

    /**
     * Returns the involved Player
     *
     * @return The player that is involved.
     */
    @SuppressWarnings("unused")
    public Player getPlayer(){
        return player;
    }

    /**
     * The direction the packet was travelling
     */
    public enum ConnectionDirection {
        TO_CLIENT,
        TO_SERVER
    }
}
