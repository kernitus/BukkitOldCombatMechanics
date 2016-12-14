package kernitus.plugin.OldCombatMechanics.module;

import org.bukkit.Material;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

import kernitus.plugin.OldCombatMechanics.OCMMain;

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
		
		debug("An empty offhand slot was clicked");
		e.setResult(Event.Result.DENY);
		e.setCancelled(true);
	}
}