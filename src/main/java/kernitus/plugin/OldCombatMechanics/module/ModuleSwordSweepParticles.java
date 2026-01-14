/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.particle.Particle;
import com.github.retrooper.packetevents.protocol.particle.type.ParticleTypes;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerParticle;
import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.Messenger;
import org.bukkit.entity.Player;

/**
 * A module to disable the sweep attack.
 */
public class ModuleSwordSweepParticles extends OCMModule {

    private final ParticleListener particleListener = new ParticleListener();

    public ModuleSwordSweepParticles(OCMMain plugin) {
        super(plugin, "disable-sword-sweep-particles");

        reload();
    }

    @Override
    public void reload() {
        if (isEnabled())
            PacketEvents.getAPI().getEventManager().registerListener(particleListener);
        else
            PacketEvents.getAPI().getEventManager().unregisterListener(particleListener);
    }

    /**
     * Hides sweep particles.
     */
    private class ParticleListener extends PacketListenerAbstract {

        private boolean disabledDueToError;

        @Override
        public void onPacketSend(PacketSendEvent packetEvent) {
            if (disabledDueToError || packetEvent.isCancelled())
                return;

            try {
                if (!PacketType.Play.Server.PARTICLE.equals(packetEvent.getPacketType()))
                    return;

                final Object playerObject = packetEvent.getPlayer();
                if (!(playerObject instanceof Player))
                    return;

                final Player player = (Player) playerObject;
                if (!isEnabled(player))
                    return;

                WrapperPlayServerParticle wrapper = new WrapperPlayServerParticle(packetEvent);
                Particle<?> particle = wrapper.getParticle();
                if (particle == null || particle.getType() == null)
                    return;

                if (particle.getType() == ParticleTypes.SWEEP_ATTACK) {
                    packetEvent.setCancelled(true);
                    debug("Cancelled sweep particles", player);
                }
            } catch (Exception | ExceptionInInitializerError e) {
                disabledDueToError = true;
                Messenger.warn(
                        e,
                        "Error detecting sweep packets. Please report it along with the following exception " +
                                "on github." +
                                "Sweep cancellation should still work, but particles might show up."
                );
            }
        }
    }
}
