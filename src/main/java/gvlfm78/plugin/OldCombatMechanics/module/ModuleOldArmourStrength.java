package kernitus.plugin.OldCombatMechanics.module;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.inventory.ItemStack;

import com.codingforcookies.armourequip.ArmourEquipEvent;
import com.comphenix.example.Attributes;
import com.comphenix.example.Attributes.Attribute;
import com.comphenix.example.Attributes.AttributeType;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.ArmourValues;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.ItemData;

public class ModuleOldArmourStrength extends Module {

	public ModuleOldArmourStrength(OCMMain plugin) {
		super(plugin, "old-armour-strength");
	}

	@EventHandler
	public void onArmourEquip(ArmourEquipEvent e) {
		final Player p = e.getPlayer();
		debug("OnArmourEquip was called", p);
		ItemStack newPiece = e.getNewArmourPiece();

		if (newPiece != null && newPiece.getType() != Material.AIR) {
			debug("Attempting to apply armour value to new armour piece", p);

			e.setNewArmourPiece(apply(newPiece, isEnabled(p.getWorld())));
		}
	}

	@EventHandler
	public void onWorldChange(PlayerChangedWorldEvent e){
		Player player = e.getPlayer();
		debug("onWorldChange armour event was called", player);

		ItemStack[] armours = player.getInventory().getContents();
		//Check the whole inventory for armour pieces

		boolean enabled = isEnabled(player.getWorld());

		for(int i = 0; i < armours.length; i++){
			ItemStack piece = armours[i];

			if (piece != null && piece.getType() != Material.AIR) {
				Player p = e.getPlayer();

				debug("Attempting to apply armour value to armour piece", p);

				armours[i] = apply(piece, enabled);
			}
		}
		player.getInventory().setContents(armours);
	}

	private ItemStack apply(ItemStack is, boolean enable) {

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
		else return is; //Not an armour piece

		double strength = ArmourValues.getValue(is.getType());

		Attributes attributes = new Attributes(is);

		double toughness;

		if(enable)
			toughness = plugin.getConfig().getDouble("old-armour-strength.toughness");
		else
			toughness = getDefaultToughness(is.getType());

		boolean armourTagPresent = false, toughnessTagPresent = false;

		for(int i = 0; i<attributes.size(); i++){
			Attribute att = attributes.get(i);
			if(att==null) continue;

			AttributeType attType = att.getAttributeType();

			if(attType.equals(AttributeType.GENERIC_ARMOR)){ //Found a generic armour tag
				if(armourTagPresent==true) //If we've already found another tag
					attributes.remove(att); //Remove this one as it's a duplicate
				else{
					armourTagPresent = true;
					if(att.getAmount()!=strength){ //If its value does not match what it should be, remove it
						attributes.remove(att);
						armourTagPresent = false; //Set armour value anew
					}
				}
			}

			else if(attType.equals(AttributeType.GENERIC_ARMOR_TOUGHNESS)){ //Found a generic armour toughness tag
				if(toughnessTagPresent==true) //If we've already found another tag
					attributes.remove(att); //Remove this one as it's a duplicate
				else{
					toughnessTagPresent = true;
					if(att.getAmount()!=toughness){ //If its value does not match what it should be, remove it
						attributes.remove(att);
						toughnessTagPresent = false; //Set armour value anew
					}
				}
			}
		}

		if(!armourTagPresent) attributes.add(Attributes.Attribute.newBuilder().name("Armor").type(Attributes.AttributeType.GENERIC_ARMOR).amount(strength).slot(slot).build());

		if(!toughnessTagPresent) attributes.add(Attributes.Attribute.newBuilder().name("ArmorToughness").type(Attributes.AttributeType.GENERIC_ARMOR_TOUGHNESS).amount(toughness).slot(slot).build());


		ItemData.mark(is, "ArmorModifier");

		return is;
	}
	public static int getDefaultToughness(Material mat){
		switch(mat){
		case DIAMOND_CHESTPLATE: case DIAMOND_HELMET: case DIAMOND_LEGGINGS: case DIAMOND_BOOTS:
			return 2;
		default:
			return 0;
		}
	}
}
