/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.versions.materials.MaterialRegistry;
import kernitus.plugin.OldCombatMechanics.versions.materials.VersionedMaterial;
import org.bukkit.block.Block;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.EnchantingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.Permissible;

/**
 * Allows enchanting without needing lapis.
 */
public class ModuleNoLapisEnchantments extends Module {

    private VersionedMaterial lapisLazuli;

    public ModuleNoLapisEnchantments(OCMMain plugin){
        super(plugin, "no-lapis-enchantments");

        lapisLazuli = MaterialRegistry.LAPIS_LAZULI;
    }

    @EventHandler
    public void onEnchant(EnchantItemEvent e){
        Block block = e.getEnchantBlock();
        if(!isEnabled(block.getWorld())) return;

        if(hasNoPermission(e.getEnchanter())) return;

        EnchantingInventory ei = (EnchantingInventory) e.getInventory(); //Not checking here because how else would event be fired?
        ei.setSecondary(getLapis());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e){
        if(!isEnabled(e.getWhoClicked().getWorld())) return;

        if(e.getInventory().getType() != InventoryType.ENCHANTING) return;

        if(hasNoPermission(e.getWhoClicked())) return;

        ItemStack item = e.getCurrentItem();

        if(item == null){
            return;
        }

        // prevent taking it out
        if(lapisLazuli.isSame(item) && e.getRawSlot() == 1){
            e.setCancelled(true);
        } else if(e.getCursor() != null && lapisLazuli.isSame(e.getCursor()) && e.getClick() == ClickType.DOUBLE_CLICK){
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e){
        if(!isEnabled(e.getPlayer().getWorld())) return;

        Inventory inventory = e.getInventory();
        if(inventory == null || inventory.getType() != InventoryType.ENCHANTING) return;

        // always clear it, so nothing is left over in the table
        ((EnchantingInventory) inventory).setSecondary(null);
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent e){
        fillUpEnchantingTable(e.getPlayer(), e.getInventory());
    }

    private void fillUpEnchantingTable(HumanEntity player, Inventory inventory){
        if(!isEnabled(player.getWorld())) return;

        if(inventory == null || inventory.getType() != InventoryType.ENCHANTING || hasNoPermission(player)) return;
        ((EnchantingInventory) inventory).setSecondary(getLapis());
    }

    private ItemStack getLapis(){
        ItemStack lapis = lapisLazuli.newInstance();
        lapis.setAmount(64);
        return lapis;
    }

    private boolean hasNoPermission(Permissible player){
        return isSettingEnabled("usePermission") && !player.hasPermission("oldcombatmechanics.nolapis");
    }
}
