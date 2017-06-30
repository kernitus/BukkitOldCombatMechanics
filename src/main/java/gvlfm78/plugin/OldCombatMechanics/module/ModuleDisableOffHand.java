package gvlfm78.plugin.OldCombatMechanics.module;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

import gvlfm78.plugin.OldCombatMechanics.OCMMain;

public class ModuleDisableOffHand extends Module {

	private static ArrayList<Material> mats = new ArrayList<Material>();

	public static ModuleDisableOffHand INSTANCE;

	public ModuleDisableOffHand(OCMMain plugin) {
		super(plugin, "disable-offhand");
		INSTANCE = this;
		reloadList();
	}

	public void reloadList(){
		mats.clear();
		List<String> items = module().getStringList("items");

		//There is no list, just block everything
		if(items==null || items.isEmpty())
			return;

		//Looping through name list and adding valid materials to list
		for(String itemName : items){
			Material foundMat = Material.matchMaterial(itemName);
			debug("Found material: " + foundMat);
			if(foundMat != null) mats.add(foundMat);
		}
	}

	@EventHandler (priority = EventPriority.HIGHEST)
	public void onSwapHandItems(PlayerSwapHandItemsEvent e) {
		if(isEnabled(e.getPlayer().getWorld()) && shouldWeCancel(e.getOffHandItem())){
			e.setCancelled(true);
		}
	}

	@EventHandler (priority = EventPriority.HIGHEST)
	public void onInventoryClick(InventoryClickEvent e){
		if(!isEnabled(e.getWhoClicked().getWorld())) return;
		if(e.getInventory().getType() != InventoryType.CRAFTING) return; //Making sure it's a survival player's inventory

		if(e.getSlot() != 40) return;
		// If they didn't click into the offhand slot, return

		if(e.getClick().equals(ClickType.NUMBER_KEY) || shouldWeCancel(e.getCursor())){
			e.setResult(Event.Result.DENY);
			e.setCancelled(true);
		}
	}

	@EventHandler (priority = EventPriority.HIGHEST)
	public void onInventoryDrag(InventoryDragEvent e){
		if(!isEnabled(e.getWhoClicked().getWorld()) || 
				e.getInventory().getType() != InventoryType.CRAFTING ||
				!e.getInventorySlots().contains(40)) return;

		if(shouldWeCancel(e.getOldCursor())){
			e.setResult(Event.Result.DENY);
			e.setCancelled(true);
		}
	}

	public boolean shouldWeCancel(ItemStack item){
		if (item == null || item.getType() == Material.AIR) {
			return false;
		}

		Material mat = item.getType();
		boolean isContained = mats.contains(mat);
		boolean isWhitelist = module().getBoolean("whitelist");

		if(isWhitelist && !isContained || !isWhitelist && isContained)
			return true;

		return false;
	}
}