package kernitus.plugin.OldCombatMechanics.module;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;

import kernitus.plugin.OldCombatMechanics.OCMMain;

public class ModuleDisableElytra extends Module {

	public ModuleDisableElytra(OCMMain plugin){
		super(plugin, "disable-elytra");
	}

	//Stop players from being able to use elytras
	@SuppressWarnings("deprecation")
	@EventHandler
	public void onInventoryClick(InventoryClickEvent e){
		if(!isEnabled(e.getWhoClicked().getWorld())) return;
		if(e.getCursor().getType().equals(Material.ELYTRA)){
			e.setCursor(null); //This deletes the elytra
			((Player) e.getWhoClicked()).updateInventory(); //This makes sure it doesn't still appear to them as if they have the item
		}
	}
}