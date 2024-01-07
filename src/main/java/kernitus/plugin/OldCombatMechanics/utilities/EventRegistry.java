/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities;

import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * A simple utility class to ensure that a Listener is not registered more than once.
 */
public class EventRegistry {
    private final Plugin plugin;
    private final List<Listener> listeners = new ArrayList<>();

    public EventRegistry(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Registers a listener and returns <code>true</code> if the listener was not already registered.
     *
     * @param listener The {@link Listener} to register.
     * @return Whether the listener was successfully registered.
     */
    public boolean registerListener(Listener listener) {
        if (listeners.contains(listener)) return false;

        listeners.add(listener);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        return true;
    }

    /**
     * Unregisters a listener and returns <code>true</code> if the listener was already registered.
     *
     * @param listener The {@link Listener} to register.
     * @return Whether the listener was successfully unregistered.
     */
    public boolean unregisterListener(Listener listener) {
        if (!listeners.contains(listener)) return false;

        listeners.remove(listener);
        HandlerList.unregisterAll(listener);
        return true;
    }
}
