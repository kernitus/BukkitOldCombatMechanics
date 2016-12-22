package gvlfm78.plugin.OldCombatMechanics.module;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

import gvlfm78.plugin.OldCombatMechanics.OCMMain;

public class ModuleDisableOffHand extends Module {

	public ModuleDisableOffHand(OCMMain plugin) {
		super(plugin, "disable-offhand");
	}

	@EventHandler (priority = EventPriority.HIGHEST)
	public void onSwapHandItems(PlayerSwapHandItemsEvent e) {
		if(isEnabled(e.getPlayer().getWorld()))
			e.setCancelled(true);
	}

	@EventHandler (priority = EventPriority.HIGHEST)
	public void onInventoryClick(InventoryClickEvent e){
		if(!isEnabled(e.getWhoClicked().getWorld())) return;

		if(e.getInventory().getType().equals(InventoryType.PLAYER)) return; //Making sure it's a survival player's inventory

		if(e.getSlot() != 40) return;
		// If they didn't click into the offhand slot, return

		if(!e.getCurrentItem().getType().equals(Material.AIR)) return; 
		// If the slot is not empty, allow taking the item
		// This is because enough spamclicking will actually allow them to put an item into the offhand slot
		// They now need to get extremely lucky to stop clicking at the exact click in which this occurs,
		// as any further click on the item will take it back out

		ItemStack item = e.getCursor();
		if(shouldWeCancel(item)){
			e.setResult(Event.Result.DENY);
			e.setCancelled(true);
		}
	}

	@EventHandler (priority = EventPriority.HIGHEST)
	public void onInventoryDrag(InventoryDragEvent e){
		if(!isEnabled(e.getWhoClicked().getWorld()) || 
				e.getInventory().getType().equals(InventoryType.PLAYER) || 
				!e.getInventorySlots().contains(40)) return;

		e.setResult(Event.Result.DENY);
		e.setCancelled(true);
	}

	public boolean shouldWeCancel(ItemStack item){
		//Now checking if the item
		Material mat = item.getType();
		List<String> items = module().getStringList("items");

		//There is no list, just block everything
		if(items==null || items.isEmpty())
			return true;

		//List of materials in list
		ArrayList<Material> mats = new ArrayList<Material>();
		//Looping through name list and adding valid materials to list
		for(String itemName : items){
			Material foundMat = Material.matchMaterial(itemName);
			if(foundMat != null) mats.add(foundMat);
		}

		boolean isContained = mats.contains(mat);
		boolean isWhitelist = module().getBoolean("whitelist");

		if(isWhitelist && !isContained || !isWhitelist && isContained )
			return true;

		return false;
	}
}