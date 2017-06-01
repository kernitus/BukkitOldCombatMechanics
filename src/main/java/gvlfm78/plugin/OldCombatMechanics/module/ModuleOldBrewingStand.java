package kernitus.plugin.OldCombatMechanics.module;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BrewingStand;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import kernitus.plugin.OldCombatMechanics.OCMMain;

public class ModuleOldBrewingStand extends Module {

	public ModuleOldBrewingStand(OCMMain plugin){
		super(plugin, "old-brewing-stand");
	}

	@EventHandler
	public void onBrew(BrewEvent e) {
		if(isEnabled(e.getBlock().getWorld()))
			e.getContents().setFuel(new ItemStack(Material.BLAZE_POWDER, 64));
	}

	@EventHandler
	public void onInventoryClick(InventoryClickEvent e) {
		if(!isEnabled(e.getWhoClicked().getWorld())) return;

		if(e.getInventory().getType().equals(InventoryType.BREWING)){
			ItemStack item = e.getCurrentItem();

			if(item!=null && 
					( (item.getType().equals(Material.BLAZE_POWDER) && e.getRawSlot() == 4) 
							|| e.getClick().equals(ClickType.DOUBLE_CLICK) ) )
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

	@EventHandler
	public void onInventoryOpen(InventoryOpenEvent e) {
		if(!isEnabled(e.getPlayer().getWorld())) return;

		Inventory inv = e.getInventory();
		if(inv.getType().equals(InventoryType.BREWING))
			( (BrewerInventory) inv).setFuel(new ItemStack(Material.BLAZE_POWDER, 64));
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
	}
}