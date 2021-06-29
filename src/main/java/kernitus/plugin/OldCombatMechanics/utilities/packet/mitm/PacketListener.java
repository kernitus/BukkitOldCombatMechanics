package kernitus.plugin.OldCombatMechanics.utilities.packet.mitm;

/**
 * Listens for a Packet
 */
public interface PacketListener {

    /**
     * Called when a packet is received
     *
     * @param packetEvent The {@link PacketEvent}
     */
    void onPacketReceived(PacketEvent packetEvent);

    /**
     * Called when a packet is send
     *
     * @param packetEvent The {@link PacketEvent}
     */
    void onPacketSend(PacketEvent packetEvent);
}
