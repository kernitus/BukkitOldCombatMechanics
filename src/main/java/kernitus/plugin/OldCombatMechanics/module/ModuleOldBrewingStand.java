/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.BrewingStand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;

/**
 * Makes brewing stands not require fuel.
 */
public class ModuleOldBrewingStand extends OCMModule {

    public ModuleOldBrewingStand(OCMMain plugin) {
        super(plugin, "old-brewing-stand");
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent e) {
        // Set max fuel when they open brewing stand
        // If they run out, they can just close and open it again
        if (!isEnabled(e.getPlayer())) return;

        final Inventory inventory = e.getInventory();
        final Location location = inventory.getLocation();
        if (location == null) return;

        final Block block = location.getBlock();
        final BlockState blockState = block.getState();

        if (!(blockState instanceof BrewingStand)) return;

        final BrewingStand brewingStand = (BrewingStand) blockState;

        brewingStand.setFuelLevel(20);
        brewingStand.update();
    }
}