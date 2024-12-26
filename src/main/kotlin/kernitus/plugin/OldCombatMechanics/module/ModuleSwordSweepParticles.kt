/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module

import com.comphenix.protocol.PacketType
import com.comphenix.protocol.events.PacketAdapter
import com.comphenix.protocol.events.PacketEvent
import kernitus.plugin.OldCombatMechanics.OCMMain
import kernitus.plugin.OldCombatMechanics.utilities.Messenger.warn
import org.bukkit.plugin.Plugin

/**
 * A module to disable the sweep attack.
 */
class ModuleSwordSweepParticles(plugin: OCMMain) : OCMModule(plugin, "disable-sword-sweep-particles") {
    private val protocolManager = plugin.protocolManager
    private val particleListener = ParticleListener(plugin)

    init {
        reload()
    }

    override fun reload() {
        if (isEnabled()) protocolManager?.addPacketListener(particleListener)
        else protocolManager?.removePacketListener(particleListener)
    }

    /**
     * Hides sweep particles.
     */
    private inner class ParticleListener(plugin: Plugin?) :
        PacketAdapter(plugin, PacketType.Play.Server.WORLD_PARTICLES) {
        private var disabledDueToError = false

        override fun onPacketSending(packetEvent: PacketEvent) {
            if (disabledDueToError || !isEnabled(packetEvent.player.world)) return

            try {
                val packetContainer = packetEvent.packet
                val particleName = try {
                    packetContainer.newParticles.read(0).particle.name
                } catch (exception: Exception) {
                    packetContainer.particles.read(0).name // for pre 1.13
                }

                if (particleName.uppercase().contains("SWEEP")) {
                    packetEvent.isCancelled = true
                    debug("Cancelled sweep particles", packetEvent.player)
                }
            } catch (e: Exception) {
                disabledDueToError = true
                warn(
                    e,
                    "Error detecting sweep packets. Please report it along with the following exception " +
                            "on github." +
                            "Sweep cancellation should still work, but particles might show up."
                )
            } catch (e: ExceptionInInitializerError) {
                disabledDueToError = true
                warn(
                    e,
                    "Error detecting sweep packets. Please report it along with the following exception " +
                            "on github." +
                            "Sweep cancellation should still work, but particles might show up."
                )
            }
        }
    }
}
