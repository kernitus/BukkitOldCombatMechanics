package kernitus.plugin.OldCombatMechanics.utilities.packet;

import org.bukkit.entity.Player;

public interface ImmutablePacket {

    /**
     * Sends this packet to the given players.
     *
     * @param players the players to send it to
     */
    void send(Player... players);

    /**
     * Returns the underlying packet object. The packet object is immutable in newer versions. This method is provided
     * as an escape hatch.
     *
     * @return the underlying nms packet
     */
    Object getNmsPacket();
}
