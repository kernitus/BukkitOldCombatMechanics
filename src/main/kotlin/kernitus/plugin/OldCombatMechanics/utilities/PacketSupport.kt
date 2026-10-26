/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.PacketEventsAPI
import com.github.retrooper.packetevents.event.PacketListenerAbstract
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder
import org.bukkit.plugin.java.JavaPlugin

/** Packet suppression has no equivalent Bukkit API. Keep the shaded backend behind one lifecycle. */
class PacketSupport(
    plugin: JavaPlugin,
) {
    private val lifecycle =
        PacketLifecycle(
            object : PacketLifecycle.Backend<PacketListenerAbstract> {
                private lateinit var api: PacketEventsAPI<*>

                override fun acquire() {
                    api = SpigotPacketEventsBuilder.build(plugin)
                }

                override fun load() {
                    PacketEvents.setAPI(api)
                    api.load()
                }

                override fun initialise() {
                    api.init()
                }

                override fun register(listener: PacketListenerAbstract) {
                    api.eventManager.registerListener(listener)
                }

                override fun unregister(listener: PacketListenerAbstract) {
                    api.eventManager.unregisterListener(listener)
                }

                override fun terminate() {
                    api.terminate()
                }
            },
            { plugin.logger.warning(it) },
        )
    val isReady: Boolean get() = lifecycle.isReady

    fun isActive(listener: PacketListenerAbstract): Boolean = lifecycle.isActive(listener)

    fun load() = lifecycle.load()

    fun initialise() = lifecycle.initialise()

    fun update(
        feature: String,
        listener: PacketListenerAbstract,
        enabled: Boolean,
    ) = lifecycle.update(feature, listener, enabled)

    fun close() = lifecycle.close()
}
