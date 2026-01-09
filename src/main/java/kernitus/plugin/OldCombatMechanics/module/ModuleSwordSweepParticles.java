/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.Messenger;
import org.bukkit.plugin.Plugin;

import java.util.Locale;

/**
 * A module to disable the sweep attack.
 */
public class ModuleSwordSweepParticles extends OCMModule {

    private final ProtocolManager protocolManager = plugin.getProtocolManager();
    private final ParticleListener particleListener = new ParticleListener(plugin);

    public ModuleSwordSweepParticles(OCMMain plugin) {
        super(plugin, "disable-sword-sweep-particles");

        reload();
    }

    @Override
    public void reload() {
        if (isEnabled())
            protocolManager.addPacketListener(particleListener);
        else
            protocolManager.removePacketListener(particleListener);
    }

    /**
     * Hides sweep particles.
     */
    private class ParticleListener extends PacketAdapter {

        private boolean disabledDueToError;

        public ParticleListener(Plugin plugin) {
            super(plugin, PacketType.Play.Server.WORLD_PARTICLES);
        }

        @Override
        public void onPacketSending(PacketEvent packetEvent) {
            if (disabledDueToError || !isEnabled(packetEvent.getPlayer().getWorld()))
                return;

            try {
                final PacketContainer packetContainer = packetEvent.getPacket();
                String particleName = null;
                try {
                    com.comphenix.protocol.wrappers.WrappedParticle<?> newParticle =
                            packetContainer.getNewParticles().read(0);
                    if (newParticle != null && newParticle.getParticle() != null) {
                        particleName = newParticle.getParticle().name();
                    }
                } catch (Exception exception) { // fall back for legacy packets / wrappers
                    com.comphenix.protocol.wrappers.EnumWrappers.Particle legacyParticle =
                            packetContainer.getParticles().read(0); // for pre 1.13
                    if (legacyParticle != null) {
                        particleName = legacyParticle.name();
                    }
                }

                if (particleName == null)
                    return; // unknown particle; do not disable the listener

                if (particleName.toUpperCase(Locale.ROOT).contains("SWEEP")) {
                    packetEvent.setCancelled(true);
                    debug("Cancelled sweep particles", packetEvent.getPlayer());
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
