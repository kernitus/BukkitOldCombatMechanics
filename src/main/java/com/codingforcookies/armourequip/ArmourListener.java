package com.codingforcookies.armourequip;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.InventoryType.SlotType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import com.codingforcookies.armourequip.ArmourEquipEvent.EquipMethod;

import gvlfm78.plugin.OldCombatMechanics.OCMMain;
import gvlfm78.plugin.OldCombatMechanics.module.Module;

/**
 * @Author Borlea
 * @Github https://github.com/borlea/
 * @Website http://codingforcookies.com/
 * @since Jul 30, 2015 6:43:34 PM
 */
public class ArmourListener extends Module implements Listener {

    public ArmourListener(OCMMain plugin) {
        super(plugin, "old-armour-strength");
    }

    @EventHandler
    public final void onInventoryClick(final InventoryClickEvent e) {
        boolean shift = false, numberkey = false;
        if (e.isCancelled()) return;
        if (e.getClick().equals(ClickType.SHIFT_LEFT) || e.getClick().equals(ClickType.SHIFT_RIGHT)) {
            shift = true;
        }
        if (e.getClick().equals(ClickType.NUMBER_KEY)) {
            numberkey = true;
        }

        if ((e.getSlotType() != SlotType.ARMOR || e.getSlotType() != SlotType.QUICKBAR) && !e.getInventory().getType().equals(InventoryType.CRAFTING))
            return;

        if (!(e.getWhoClicked() instanceof Player)) return;
        if (e.getCurrentItem() == null && e.getCursor() == null) return;
        ArmourType newArmourType = ArmourType.matchType(shift ? e.getCurrentItem() : e.getCursor());
        if (!shift && newArmourType != null && e.getRawSlot() != newArmourType.getSlot()) {
            // Used for drag and drop checking to make sure you aren't trying to place a helmet in the boots place.
            return;
        }

        if (shift) {
            newArmourType = ArmourType.matchType(e.getCurrentItem());
            if (newArmourType != null) {
                boolean equipping = true;
                if (e.getRawSlot() == newArmourType.getSlot()) {
                    equipping = false;
                }
                if (newArmourType.equals(ArmourType.HELMET) && (equipping ? e.getWhoClicked().getInventory().getHelmet() == null : e.getWhoClicked().getInventory().getHelmet() != null) || newArmourType.equals(ArmourType.CHESTPLATE) && (equipping ? e.getWhoClicked().getInventory().getChestplate() == null : e.getWhoClicked().getInventory().getChestplate() != null) || newArmourType.equals(ArmourType.LEGGINGS) && (equipping ? e.getWhoClicked().getInventory().getLeggings() == null : e.getWhoClicked().getInventory().getLeggings() != null) || newArmourType.equals(ArmourType.BOOTS) && (equipping ? e.getWhoClicked().getInventory().getBoots() == null : e.getWhoClicked().getInventory().getBoots() != null)) {
                    ArmourEquipEvent armourEquipEvent = new ArmourEquipEvent((Player) e.getWhoClicked(), EquipMethod.SHIFT_CLICK, newArmourType, equipping ? null : e.getCurrentItem(), equipping ? e.getCurrentItem() : null);
                    Bukkit.getServer().getPluginManager().callEvent(armourEquipEvent);
                    if (armourEquipEvent.isCancelled()) {
                        e.setCancelled(true);
                    }
                }
            }
        } else {

            ItemStack newArmourPiece = e.getCursor();
            ItemStack oldArmourPiece = e.getCurrentItem();
            if (numberkey) {

                    // e.getInventory() == The players inventory
                    // e.getHotBarButton() == key people are pressing to equip or unequip the item to or from.
                    // e.getRawSlot() == The slot the item is going to.
                    // e.getSlot() == Armour slot, can't use e.getRawSlot() as that gives a hotbar slot ;-;
  	
                    ItemStack hotbarItem = e.getInventory().getItem(e.getHotbarButton());
                    
                    //All of the following #getItem() return null
                   /* System.out.println("hotbar: "+e.getInventory().getItem(e.getHotbarButton()));
                    System.out.println("raw: "+e.getInventory().getItem(e.getRawSlot()));
                    System.out.println("slot: "+e.getInventory().getItem(e.getSlot()));
                    System.out.println("current: "+e.getCurrentItem());
                    System.out.println("current: "+e.getCursor());*/
                    
                    if (hotbarItem != null) {// Equipping
                        newArmourType = ArmourType.matchType(hotbarItem);
                        newArmourPiece = hotbarItem;
                        oldArmourPiece = e.getInventory().getItem(e.getSlot());
                    } else {// Unequipping
                        newArmourType = ArmourType.matchType(e.getCurrentItem() != null && e.getCurrentItem().getType() != Material.AIR ? e.getCurrentItem() : e.getCursor());
                    }
            } else {
                // e.getCurrentItem() == Unequip
                // e.getCursor() == Equip
                newArmourType = ArmourType.matchType(e.getCurrentItem() != null && e.getCurrentItem().getType() != Material.AIR ? e.getCurrentItem() : e.getCursor());
            }
            if (newArmourType != null && e.getRawSlot() == newArmourType.getSlot()) {
                EquipMethod method = EquipMethod.DRAG;
                if (e.getAction().equals(InventoryAction.HOTBAR_SWAP) || numberkey) method = EquipMethod.HOTBAR_SWAP;

                ArmourEquipEvent armourEquipEvent = new ArmourEquipEvent((Player) e.getWhoClicked(), method, newArmourType, oldArmourPiece, newArmourPiece);
                Bukkit.getServer().getPluginManager().callEvent(armourEquipEvent);
                if (armourEquipEvent.isCancelled()) {
                    e.setCancelled(true);
                }
            }
        }
    }

    @SuppressWarnings("deprecation")
	@EventHandler
    public void playerInteractEvent(PlayerInteractEvent e) {
        if (e.getAction() == Action.PHYSICAL) return;
        if (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            final Player player = e.getPlayer();
            if (e.getClickedBlock() != null && e.getAction() == Action.RIGHT_CLICK_BLOCK) {// Having both of these checks is useless, might as well do it though.
                // Some blocks have actions when you right click them which stops the client from equipping the armour in hand.
            }
            ArmourType newArmourType = ArmourType.matchType(e.getItem());
            if (newArmourType != null) {
                if (newArmourType.equals(ArmourType.HELMET) && e.getPlayer().getInventory().getHelmet() == null || newArmourType.equals(ArmourType.CHESTPLATE) && e.getPlayer().getInventory().getChestplate() == null || newArmourType.equals(ArmourType.LEGGINGS) && e.getPlayer().getInventory().getLeggings() == null || newArmourType.equals(ArmourType.BOOTS) && e.getPlayer().getInventory().getBoots() == null) {
                    ArmourEquipEvent armourEquipEvent = new ArmourEquipEvent(e.getPlayer(), EquipMethod.HOTBAR, ArmourType.matchType(e.getItem()), null, e.getItem());
                    Bukkit.getServer().getPluginManager().callEvent(armourEquipEvent);
                    if (armourEquipEvent.isCancelled()) {
                        e.setCancelled(true);
                        player.updateInventory();
                    }
                }
            }
        }
    }

    @EventHandler
    public void dispenserFireEvent(BlockDispenseEvent e) {
        ArmourType type = ArmourType.matchType(e.getItem());
        if (ArmourType.matchType(e.getItem()) != null) {
            Location loc = e.getBlock().getLocation();
            for (Player p : loc.getWorld().getPlayers()) {
                if (loc.getBlockY() - p.getLocation().getBlockY() >= -1 && loc.getBlockY() - p.getLocation().getBlockY() <= 1) {
                    if (p.getInventory().getHelmet() == null && type.equals(ArmourType.HELMET) || p.getInventory().getChestplate() == null && type.equals(ArmourType.CHESTPLATE) || p.getInventory().getLeggings() == null && type.equals(ArmourType.LEGGINGS) || p.getInventory().getBoots() == null && type.equals(ArmourType.BOOTS)) {
                        org.bukkit.block.Dispenser dispenser = (org.bukkit.block.Dispenser) e.getBlock().getState();
                        org.bukkit.material.Dispenser dis = (org.bukkit.material.Dispenser) dispenser.getData();
                        BlockFace directionFacing = dis.getFacing();
                        // Someone told me not to do big if checks because it's hard to read, look at me doing it -_-
                        if (directionFacing == BlockFace.EAST && p.getLocation().getBlockX() != loc.getBlockX() && p.getLocation().getX() <= loc.getX() + 2.3 && p.getLocation().getX() >= loc.getX() || directionFacing == BlockFace.WEST && p.getLocation().getX() >= loc.getX() - 1.3 && p.getLocation().getX() <= loc.getX() || directionFacing == BlockFace.SOUTH && p.getLocation().getBlockZ() != loc.getBlockZ() && p.getLocation().getZ() <= loc.getZ() + 2.3 && p.getLocation().getZ() >= loc.getZ() || directionFacing == BlockFace.NORTH && p.getLocation().getZ() >= loc.getZ() - 1.3 && p.getLocation().getZ() <= loc.getZ()) {
                            ArmourEquipEvent armourEquipEvent = new ArmourEquipEvent(p, EquipMethod.DISPENSER, ArmourType.matchType(e.getItem()), null, e.getItem());
                            Bukkit.getServer().getPluginManager().callEvent(armourEquipEvent);
                            if (armourEquipEvent.isCancelled()) {
                                e.setCancelled(true);
                            }
                            return;
                        }
                    }
                }
            }
        }
    }
}
