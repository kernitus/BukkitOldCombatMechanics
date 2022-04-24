/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.Config;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Prevents players from equipping an elytra.
 */
public class ModuleDisableElytra extends Module {

    private static final int CHEST_PIECE = 38;

    public ModuleDisableElytra(OCMMain plugin){
        super(plugin, "disable-elytra");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent e){
        if(!(e.getWhoClicked() instanceof Player))
            return;

        Player player = (Player) e.getWhoClicked();
        if(!isEnabled(player.getWorld()))
            return;
        if(player.getGameMode() == GameMode.CREATIVE)
            return;

        InventoryType type = e.getInventory().getType(); // Only if they're in their inventory, not chests etc.
        if(type != InventoryType.CRAFTING && type != InventoryType.PLAYER)
            return;

        ItemStack cursor = e.getCursor();
        ItemStack currentItem = e.getCurrentItem();

        // We allow the action if the user swapped with the hotbar...
        if(e.getAction() == InventoryAction.HOTBAR_SWAP){
            // ...and the item we swapped with is no elytra
            if(!isElytra(player.getInventory().getItem(e.getHotbarButton()))) return;
            // ...and they are not swapping into the chest slot
            if(e.getSlot() != CHEST_PIECE) return;
        }

        // Allow shift clicking it out of the slot, but not in
        if(isElytra(currentItem) && e.getSlot() != CHEST_PIECE)
            if((e.getClick() == ClickType.SHIFT_LEFT || e.getClick() == ClickType.SHIFT_RIGHT))
                e.setCancelled(true);

        // Prevent swapping it with an elytra in your hotbar
        if(e.getAction() == InventoryAction.HOTBAR_SWAP && isElytra(player.getInventory().getItem(e.getHotbarButton()))){
            e.setCancelled(true);
        }

        // Prevent placing it down in the armor slot
        if(e.getSlot() == CHEST_PIECE && isElytra(cursor))
            e.setCancelled(true);
    }

    private boolean isElytra(ItemStack item){
        return item != null && item.getType() == Material.ELYTRA;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRightClick(PlayerInteractEvent e){
        if(!isEnabled(e.getPlayer().getWorld())) return;

        // Must not be able to right click while holding it to wear it
        Action a = e.getAction();
        if(a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;

        if(!isElytra(e.getItem())) return;

        Block block = e.getClickedBlock();
        if(block != null && Config.getInteractiveBlocks().contains(block.getType())) return;

        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent e){
        if(!isEnabled(e.getWhoClicked().getWorld())) return;

        if(e.getOldCursor() == null || !isElytra(e.getCursor())) return;

        if(!e.getInventorySlots().contains(CHEST_PIECE)) return;

        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWorldChange(PlayerChangedWorldEvent e){
        Player player = e.getPlayer();
        World world = player.getWorld();
        if(!isEnabled(world)) return;

        PlayerInventory inventory = player.getInventory();

        ItemStack chestplate = inventory.getChestplate();

        if(!isElytra(chestplate)) return;

        inventory.setChestplate(new ItemStack(Material.AIR));

        if(inventory.firstEmpty() != -1)
            inventory.addItem(chestplate);
        else
            world.dropItem(player.getLocation(), chestplate);
    }
}