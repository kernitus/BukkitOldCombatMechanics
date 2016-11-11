package kernitus.plugin.OldCombatMechanics.module;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import kernitus.plugin.OldCombatMechanics.OCMMain;

public class ModuleOldBrewingStand extends Module{

	public ModuleOldBrewingStand(OCMMain plugin){
		super(plugin, "old-brewing-stand");
	}

	@EventHandler
	public void onBrew(BrewEvent e) {
		if(isEnabled(e.getBlock().getWorld()))
			e.getContents().setFuel(new ItemStack(Material.BLAZE_POWDER, 64));
	}

	@EventHandler
	public void onClick(InventoryClickEvent e) {
		if(!isEnabled(e.getWhoClicked().getWorld())) return;

		if(e.getInventory().getType().equals(InventoryType.BREWING) && e.getCurrentItem().getType() == Material.BLAZE_POWDER && e.getRawSlot()==4){
			e.setCancelled(true);
		}
	}

	@EventHandler
	public void onClose(InventoryCloseEvent e) {
		if(!isEnabled(e.getPlayer().getWorld())) return;
		
		Inventory inv = e.getInventory();
		if(inv.getType().equals(InventoryType.BREWING) && inv.contains(Material.BLAZE_POWDER))
			inv.remove(Material.BLAZE_POWDER);
	}

	@EventHandler
	public void onInventoryOpen(InventoryOpenEvent e) {
		if(!isEnabled(e.getPlayer().getWorld()));
		
		Inventory inv = e.getInventory();
		if(inv.getType().equals(InventoryType.BREWING))
			( (BrewerInventory) inv).setFuel(new ItemStack(Material.BLAZE_POWDER, 64));
	}
}