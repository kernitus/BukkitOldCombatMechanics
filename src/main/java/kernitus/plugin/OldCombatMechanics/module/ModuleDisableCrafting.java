/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.ConfigUtils;
import kernitus.plugin.OldCombatMechanics.utilities.Messenger;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Makes the specified materials uncraftable.
 */
public class ModuleDisableCrafting extends OCMModule {

    private List<Material> denied;
    private String message;

    public ModuleDisableCrafting(OCMMain plugin) {
        super(plugin, "disable-crafting");
        reload();
    }

    @Override
    public void reload() {
        denied = ConfigUtils.loadMaterialList(module(), "denied");
        message = module().getBoolean("showMessage") ? module().getString("message") : null;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareItemCraft(PrepareItemCraftEvent e) {
        final List<HumanEntity> viewers = e.getViewers();
        if (viewers.size() == 0) return;

        if (!isEnabled(viewers.get(0))) return;

        final CraftingInventory inv = e.getInventory();
        final ItemStack result = inv.getResult();

        if (result != null && denied.contains(result.getType())) {
            inv.setResult(null);
            if (message != null) viewers.forEach(viewer -> Messenger.send(viewer, message));
        }
    }
}