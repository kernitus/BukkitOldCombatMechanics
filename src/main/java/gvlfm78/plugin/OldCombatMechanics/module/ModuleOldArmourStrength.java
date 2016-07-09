package gvlfm78.plugin.OldCombatMechanics.module;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.inventory.ItemStack;

import com.codingforcookies.armourequip.ArmourEquipEvent;

import gvlfm78.plugin.OldCombatMechanics.OCMMain;

public class ModuleOldArmourStrength extends Module{

	private static ModuleOldArmourStrength INSTANCE;
	
	public ModuleOldArmourStrength(OCMMain plugin) {
		super(plugin, "old-armour-strength");
		INSTANCE = this;
	}
	
	@EventHandler
	public void onArmourEquip(ArmourEquipEvent e){
		e.getPlayer().sendMessage("Test999");
		ItemStack newPiece = e.getNewArmourPiece();
		if(newPiece != null && newPiece.getType() != Material.AIR){
			e.getPlayer().sendMessage("Test123");
			//They equipped a valid new piece of armour
			
			// Check if there is armour toughness on piece, if so, remove it
			
			// Check if the armour strength on the piece is the same as in the config for that piece
			// if not, set the amour strength to the value in the config
		}
	}
}
