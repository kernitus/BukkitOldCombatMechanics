package kernitus.plugin.OldCombatMechanics.utilities.packet;

import org.bukkit.entity.Player;

public interface ImmutablePacket {

    /**
     * Sends this packet to the given players.
     *
     * @param players the players to send it to
     */
    default void send(Player... players) {
        for(Player player : players){
            PacketSender.getInstance().sendPacket(this, player);
        }
    }

    /**
     * Returns the underlying packet object. The packet object is immutable in newer versions. This method is provided
     * as an escape hatch.
     *
     * @return the underlying nms packet
     */
    Object getNmsPacket();

    /**
     * @implNote this method return {@code getNmsPacket().getClass()}
     * @return the class of the nms packet
     */
    default Class<?> getPacketClass() {
        return getNmsPacket().getClass();
    }
}
