/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities

import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.plugin.Plugin

/**
 * A simple utility class to ensure that a Listener is not registered more than once.
 */
class EventRegistry(private val plugin: Plugin) {
    private val listeners: MutableList<Listener> = mutableListOf()

    /**
     * Registers a listener and returns `true` if the listener was not already registered.
     *
     * @param listener The [Listener] to register.
     * @return Whether the listener was successfully registered.
     */
    fun registerListener(listener: Listener): Boolean {
        if (listeners.contains(listener)) return false

        listeners.add(listener)
        plugin.server.pluginManager.registerEvents(listener, plugin)
        return true
    }

    /**
     * Unregisters a listener and returns `true` if the listener was already registered.
     *
     * @param listener The [Listener] to register.
     * @return Whether the listener was successfully unregistered.
     */
    fun unregisterListener(listener: Listener): Boolean {
        if (!listeners.contains(listener)) return false

        listeners.remove(listener)
        HandlerList.unregisterAll(listener)
        return true
    }
}
