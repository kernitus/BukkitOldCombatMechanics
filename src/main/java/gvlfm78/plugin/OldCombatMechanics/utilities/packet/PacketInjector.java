package gvlfm78.plugin.OldCombatMechanics.utilities.packet;

import gvlfm78.plugin.OldCombatMechanics.utilities.reflection.Reflector;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.bukkit.entity.Player;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A simple packet injector, to modify the packets sent and received
 */
class PacketInjector extends ChannelDuplexHandler {

    private static final Logger LOGGER = Logger.getLogger("PacketInjector");

    private boolean isClosed;
    private Channel channel;
    private List<PacketListener> packetListeners = new ArrayList<>();
    private WeakReference<Player> playerWeakReference;

    /**
     * Must be detached manually!
     *
     * @param player The player to attach into
     */
    PacketInjector(Player player) {
        try {
            attach(player);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        playerWeakReference = new WeakReference<>(player);
    }

    /**
     * Attaches to a player
     *
     * @param player The player to attach to
     */
    private void attach(Player player) throws Exception {

        // Lengthy way of doing: ( (CraftPlayer) handle
        // ).getHandle().playerConnection.networkManager.channel
        Object playerConnection = PacketSender.getInstance().getConnection(player);


        Object manager = Reflector.getFieldValue(playerConnection, "networkManager");

        channel = (Channel) Reflector.getFieldValue(manager, "channel");

        // remove old listener, if it wasn't properly cleared up
        if (channel.pipeline().get("ocm_handler") != null) {
            // remove old
            channel.pipeline().remove("ocm_handler");
        }

        channel.pipeline().addBefore("packet_handler", "ocm_handler", this);
    }

    /**
     * Removes this handler
     */
    void detach() {
        if (isClosed || !channel.isOpen()) {
            return;
        }
        isClosed = true;
        channel.eventLoop().submit(() -> channel.pipeline().remove(this));

        // clear references. Probably not needed, but I am not sure about the
        // channel.
        playerWeakReference.clear();
        packetListeners.clear();
        channel = null;
    }

    /**
     * Checks if this handler is closed
     *
     * @return True if the handler is closed
     */
    private boolean isClosed() {
        return isClosed;
    }

    /**
     * Adds a {@link PacketListener}
     *
     * @param packetListener The {@link PacketListener} to add
     * @throws IllegalStateException if the channel is already closed
     */
    void addPacketListener(PacketListener packetListener) {
        Objects.requireNonNull(packetListener, "packetListener can not be null");
        if (isClosed()) {
            throw new IllegalStateException("Channel already closed. Adding of listener invalid");
        }
        packetListeners.add(packetListener);
    }

    /**
     * Removes a {@link PacketListener}
     *
     * @param packetListener The {@link PacketListener} to remove
     */
    void removePacketListener(PacketListener packetListener) {
        packetListeners.remove(packetListener);
    }

    /**
     * Returns the amount of listeners
     *
     * @return The amount of listeners
     */
    int getListenerAmount() {
        return packetListeners.size();
    }

    @Override
    public void write(ChannelHandlerContext channelHandlerContext, Object packet, ChannelPromise channelPromise)
            throws Exception {
        PacketEvent event = new PacketEvent(
                packet,
                PacketEvent.ConnectionDirection.TO_CLIENT,
                playerWeakReference.get()
        );

        for (PacketListener packetListener : packetListeners) {
            try {
                packetListener.onPacketSend(event);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING,
                        "Error in a Packet Listener (send). Nag the author of that plugin!", e);
            }
        }

        // let it through
        if (!event.isCancelled()) {
            super.write(channelHandlerContext, packet, channelPromise);
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext channelHandlerContext, Object packet) throws Exception {
        PacketEvent event = new PacketEvent(
                packet,
                PacketEvent.ConnectionDirection.TO_SERVER,
                playerWeakReference.get()
        );

        for (PacketListener packetListener : packetListeners) {
            try {
                packetListener.onPacketReceived(event);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING,
                        "Error in a Packet Listener (receive). Nag the author of that plugin!", e);
            }
        }

        // let it through
        if (!event.isCancelled()) {
            super.channelRead(channelHandlerContext, packet);
        }
    }
}
