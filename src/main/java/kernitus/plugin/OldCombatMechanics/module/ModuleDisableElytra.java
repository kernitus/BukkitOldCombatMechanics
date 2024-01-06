/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Prevents players from equipping an elytra.
 */
public class ModuleDisableElytra extends OCMModule {

    private static final int CHEST_SLOT = 38;
    private static final int OFFHAND_SLOT = 40;

    public ModuleDisableElytra(OCMMain plugin) {
        super(plugin, "disable-elytra");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        final Player player = (Player) e.getWhoClicked();

        if (!isEnabled(player) || player.getGameMode() == GameMode.CREATIVE) return;

        // If they're in their own inventory, and not chests etc.
        final InventoryType inventoryType = e.getInventory().getType();
        if (inventoryType != InventoryType.CRAFTING && inventoryType != InventoryType.PLAYER) return;

        final ItemStack cursor = e.getCursor();
        final ItemStack currentItem = e.getCurrentItem();
        final ClickType clickType = e.getClick();
        final PlayerInventory inv = player.getInventory();
        final int slot = e.getSlot();

        try {
            // If they used a number key to swap an elytra from the hotbar into the chest slot
            if ((clickType == ClickType.NUMBER_KEY && slot == CHEST_SLOT && isElytra(inv.getItem(e.getHotbarButton())))

                    // If they are placing it into the chest slot directly
                    || (slot == CHEST_SLOT && isElytra(cursor))

                    // If they shift-clicked an elytra into the chest slot
                    || (isElytra(currentItem) && slot != CHEST_SLOT && e.isShiftClick())

                    // If they used F to swap an elytra from the offhand slot into the chest slot
                    || (clickType == ClickType.SWAP_OFFHAND && slot == CHEST_SLOT && isElytra(inv.getItem(OFFHAND_SLOT)))
            )
                e.setCancelled(true);
        } catch (NoSuchFieldError ignored) {
        } // For versions below 1.16 where you couldn't use F to swap offhand in inventory
    }

    private boolean isElytra(ItemStack item) {
        return item != null && item.getType() == Material.ELYTRA;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRightClick(PlayerInteractEvent e) {
        if (!isEnabled(e.getPlayer())) return;

        // Must not be able to right click while holding an elytra to wear it
        final Action action = e.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        // Stop usage of item, but allow interacting with blocks
        if (isElytra(e.getItem()))
            e.setUseItemInHand(Event.Result.DENY);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent e) {
        if (!isEnabled(e.getWhoClicked())) return;

        final ItemStack oldCursor = e.getOldCursor();

        if (e.getInventorySlots().contains(CHEST_SLOT) && isElytra(oldCursor))
            e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWorldChange(PlayerChangedWorldEvent e) {
        onModesetChange(e.getPlayer());
    }

    @Override
    public void onModesetChange(Player player) {
        if (!isEnabled(player)) return;

        final PlayerInventory inventory = player.getInventory();
        final ItemStack chestplate = inventory.getChestplate();

        if (!isElytra(chestplate)) return;

        inventory.setChestplate(new ItemStack(Material.AIR));

        if (inventory.firstEmpty() != -1)
            inventory.addItem(chestplate);
        else
            player.getWorld().dropItem(player.getLocation(), chestplate);

    }
}