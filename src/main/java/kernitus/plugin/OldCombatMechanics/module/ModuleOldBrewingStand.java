/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.BrewingStand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Makes brewing stands not require fuel.
 */
public class ModuleOldBrewingStand extends Module {

    public ModuleOldBrewingStand(OCMMain plugin) {
        super(plugin, "old-brewing-stand");
    }

    @EventHandler
    public void onBrew(BrewEvent e) {
        final Block block = e.getBlock();

        if (!isEnabled(block.getWorld())) return;

        if (Reflector.versionIsNewerOrEqualAs(1, 17, 0)) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    refuel(block.getState());
                }
            }.runTaskLater(plugin, 1L);
        } else refuel(block.getState());
    }

    private void refuel(BlockState blockState) {
        if (!(blockState instanceof BrewingStand)) return;

        final BrewingStand brewingStand = (BrewingStand) blockState;

        brewingStand.setFuelLevel(20);
        brewingStand.update();
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent e) {
        if (!isEnabled(e.getPlayer().getWorld())) return;

        final Inventory inventory = e.getInventory();
        Location location = inventory.getLocation();
        if (location == null) return;

        final Block block = location.getBlock();

        refuel(block.getState());
    }
}