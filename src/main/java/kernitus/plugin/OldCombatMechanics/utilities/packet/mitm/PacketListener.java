/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
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
