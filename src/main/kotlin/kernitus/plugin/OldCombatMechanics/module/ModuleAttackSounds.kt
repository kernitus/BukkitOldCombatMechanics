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
 * A module to disable the new attack sounds.
 */
class ModuleAttackSounds(plugin: OCMMain) : OCMModule(plugin, "disable-attack-sounds") {
    private val protocolManager = plugin.protocolManager
    private val soundListener = SoundListener(plugin)
    private val blockedSounds: MutableSet<String> = HashSet(getBlockedSounds())

    init {
        reload()
    }

    override fun reload() {
        blockedSounds.clear()
        blockedSounds.addAll(getBlockedSounds())

        if (isEnabled()) protocolManager!!.addPacketListener(soundListener)
        else protocolManager!!.removePacketListener(soundListener)
    }

    private fun getBlockedSounds(): Collection<String> {
        return module()!!.getStringList("blocked-sound-names")
    }

    /**
     * Disables attack sounds.
     */
    private inner class SoundListener(plugin: Plugin?) :
        PacketAdapter(plugin, PacketType.Play.Server.NAMED_SOUND_EFFECT) {
        private var disabledDueToError = false

        override fun onPacketSending(packetEvent: PacketEvent) {
            if (disabledDueToError || !isEnabled(packetEvent.player)) return

            try {
                val packetContainer = packetEvent.packet
                val sound = packetContainer.soundEffects.read(0) ?: return


                //fix NullpointerException when sending a custom sound 

                val soundName = sound.toString() // Works for both string and namespaced key

                if (blockedSounds.contains(soundName)) {
                    packetEvent.isCancelled = true
                    debug("Blocked sound $soundName", packetEvent.player)
                }
            } catch (e: Exception) {
                disabledDueToError = true
                warn(
                    e,
                    "Error detecting sound packets. Please report it along with the following exception " +
                            "on github."
                )
            } catch (e: ExceptionInInitializerError) {
                disabledDueToError = true
                warn(
                    e,
                    "Error detecting sound packets. Please report it along with the following exception " +
                            "on github."
                )
            }
        }
    }
}
