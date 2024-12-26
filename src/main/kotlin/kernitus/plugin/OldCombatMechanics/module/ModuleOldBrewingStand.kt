/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module

import kernitus.plugin.OldCombatMechanics.OCMMain
import org.bukkit.block.BrewingStand
import org.bukkit.event.EventHandler
import org.bukkit.event.inventory.InventoryOpenEvent

/**
 * Makes brewing stands not require fuel.
 */
class ModuleOldBrewingStand(plugin: OCMMain) : OCMModule(plugin, "old-brewing-stand") {
    @EventHandler
    fun onInventoryOpen(e: InventoryOpenEvent) {
        // Set max fuel when they open brewing stand
        // If they run out, they can just close and open it again
        if (!isEnabled(e.player)) return

        val inventory = e.inventory
        val location = inventory.location ?: return

        val block = location.block
        val blockState = block.state as? BrewingStand ?: return

        blockState.fuelLevel = 20
        blockState.update()
    }
}