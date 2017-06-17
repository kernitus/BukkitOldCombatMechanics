package gvlfm78.plugin.OldCombatMechanics.module;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BrewingStand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;

import gvlfm78.plugin.OldCombatMechanics.OCMMain;

public class ModuleOldBrewingStand extends Module {

	public ModuleOldBrewingStand(OCMMain plugin){
		super(plugin, "old-brewing-stand");
	}

	@EventHandler
	public void onBrew(BrewEvent e) {
		Block block = e.getBlock();

		if(isEnabled(block.getWorld()) && block.getType().equals(Material.BREWING_STAND)) //Just in case...
			((BrewingStand) block.getState()).setFuelLevel(20);
	}

	@EventHandler
	public void onInventoryOpen(InventoryOpenEvent e) {
		if(!isEnabled(e.getPlayer().getWorld())) return;

		Inventory inv = e.getInventory();
		Block block = inv.getLocation().getBlock();

		if(!block.getType().equals(Material.BREWING_STAND)) return;

		BrewingStand stand = (BrewingStand) block.getState();

		stand.setFuelLevel(20);

	}

	/*@EventHandler
	public void onInventoryClick(InventoryClickEvent e) {
		if(!isEnabled(e.getWhoClicked().getWorld())) return;

		if(e.getInventory().getType().equals(InventoryType.BREWING)){
			ItemStack item = e.getCurrentItem();

			if(item!=null && 
					( (item.getType().equals(Material.BLAZE_POWDER) && e.getRawSlot() == 4) ||
							( e.getCursor().getType().equals(Material.BLAZE_POWDER) && e.getClick().equals(ClickType.DOUBLE_CLICK) ) ))
				e.setCancelled(true);
		}
	}

	@EventHandler
	public void onInventoryClose(InventoryCloseEvent e) {
		if(!isEnabled(e.getPlayer().getWorld())) return;

		Inventory inv = e.getInventory();
		if(inv.getType().equals(InventoryType.BREWING)){
			BrewerInventory bi = (BrewerInventory) inv;
			ItemStack fuel = bi.getFuel();
			if(fuel != null && fuel.getType().equals(Material.BLAZE_POWDER))
				bi.setFuel(new ItemStack(Material.AIR));
		}
	}
	@EventHandler (priority = EventPriority.HIGHEST)
	public void onBlockBreak(BlockBreakEvent e){
		Block block = e.getBlock();
		if(!block.getType().equals(Material.BREWING_STAND)) return;
		//e.setDropItems(false);

		e.setCancelled(true);

		BrewingStand stand = (BrewingStand) block.getState();
		BrewerInventory inv = stand.getInventory();

		final List<HumanEntity> viewers = inv.getViewers();

		//Making a copy to not get a concurrent modification exception
		final List<HumanEntity> vws = new ArrayList<HumanEntity>();
		vws.addAll(viewers);

		//Closing the inventory of all viewers because this event does strange stuff when this doesn't happen
		//Also so that above listeners can remove the fule blaze powder before it is dropped
		vws.forEach(viewer -> viewer.closeInventory());

		//Removing the block
		block.setType(Material.AIR);
	}*/
}