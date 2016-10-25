package gvlfm78.plugin.OldCombatMechanics.module;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.inventory.ItemStack;

import com.codingforcookies.armourequip.ArmourEquipEvent;
import com.comphenix.example.Attributes;

import gvlfm78.plugin.OldCombatMechanics.OCMMain;
import gvlfm78.plugin.OldCombatMechanics.utilities.ArmourValues;
import gvlfm78.plugin.OldCombatMechanics.utilities.reflection.ItemData;

public class ModuleOldArmourStrength extends Module {

	private OCMMain plugin;
	public ModuleOldArmourStrength(OCMMain plugin) {
		super(plugin, "old-armour-strength");
		this.plugin = plugin;
	}

	@EventHandler
	public void onArmourEquip(ArmourEquipEvent e) {

		debug("OnArmourEquip was called", e.getPlayer());
		ItemStack newPiece = e.getNewArmourPiece();

		if (newPiece != null && newPiece.getType() != Material.AIR) {
			Player p = e.getPlayer();
			debug("Attempting to apply armour value to new armour piece", p);

			e.setNewArmourPiece(apply(newPiece));
		}
	}

	//This won't actually work because armour pieces won't necessarily be worn, they could be in chets etc.
	/*@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerQuit(PlayerQuitEvent e){
		Player player = e.getPlayer();
		debug("OnPlayerQuit armour event was called", player);

		ItemStack[] armours = player.getInventory().getArmorContents();

		for(int i = 0; i<armours.length-1; i++){
			ItemStack piece = armours[i];

			if (piece != null && piece.getType() != Material.AIR) {
				Player p = e.getPlayer();
				debug("Attempting to apply armour value to new armour piece", p);

				armours[i] = apply(piece);
			}
			player.getInventory().setArmorContents(armours);
		}
	}*/

	private ItemStack apply(ItemStack is) {

		if (ItemData.hasMark(is, "ArmorModifier"))
			return is;

		String slot = "";
		String type = is.getType().toString().toLowerCase();
		if(type.contains("helmet"))
			slot = "head";
		else if(type.contains("chestplate"))
			slot = "chest";
		else if(type.contains("leggings"))
			slot = "legs";
		else if(type.contains("boots"))
			slot = "feet";

		double strength = ArmourValues.getValue(is.getType());

		Attributes attributes = new Attributes(is);

		double toughness = plugin.getConfig().getDouble("old-armour-strength.toughness");

		attributes.add(Attributes.Attribute.newBuilder().name("ArmorToughness").type(Attributes.AttributeType.GENERIC_ARMOR_TOUGHNESS).amount(toughness).slot(slot).build());
		attributes.add(Attributes.Attribute.newBuilder().name("Armor").type(Attributes.AttributeType.GENERIC_ARMOR).amount(strength).slot(slot).build());

		ItemData.mark(is, "ArmorModifier");

		return is;

	}

}
