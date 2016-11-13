package kernitus.plugin.OldCombatMechanics.module;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

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
	public void onArmourUnequip(ArmourEquipEvent e) {
		final Player p = e.getPlayer();
		debug("OnArmourUnequip was called", p);
		ItemStack oldPiece = e.getOldArmourPiece();
		if (oldPiece != null && oldPiece.getType() != Material.AIR) {
			debug("Attempting to apply armour value to new armour piece", p);

			e.setNewArmourPiece(apply(oldPiece, false));
		}
	}

	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent e){
		final Player player = e.getPlayer();
		debug("onPlayerJoin armour event was called", player);
		setArmourAccordingly(player);
	}

	@EventHandler
	public void onPlayerLeave(PlayerQuitEvent e) {
		final Player player = e.getPlayer();
		debug("onPlayerLeave armour event was called", player);
		setArmourToDefault(player);
	}

	@EventHandler
	public void onWorldChange(PlayerChangedWorldEvent e) {
		final Player player = e.getPlayer();
		debug("onWorldChange armour event was called", player);
		setArmourAccordingly(player);
	}

	private void setArmourToDefault(final Player player) {
		// Tells method that module is disabled in this world
		setArmourAccordingly(player, false);
	}

	private void setArmourAccordingly(final Player player){
		boolean enabled = isEnabled(player.getWorld());
		setArmourAccordingly(player, enabled);
	}

	private void setArmourAccordingly(final Player player, boolean enabled) {
		final PlayerInventory inv = player.getInventory();
		ItemStack[] armours = inv.getContents();
		// Check the whole inventory for armour pieces

		for (int i = 0; i < armours.length; i++) {
			ItemStack piece = armours[i];

			if (piece != null && piece.getType() != Material.AIR) {
				debug("Attempting to apply armour value to armour piece", player);
				//If this piece is one of the ones being worn right now
				boolean wasWorn = false;
				for(ItemStack wornPiece : inv.getArmorContents()){
					if(wornPiece!=null && wornPiece.equals(armours[i])){ // TODO This equals doesn't work
						armours[i] = apply(piece, enabled); //Apply/remove values according state of module in this world
						wasWorn = true;
					}
				}
				if(wasWorn) armours[i] = apply(piece, false); //Otherwise set values back to default
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
		//If there's no armour tag present add it
		if(!armourTagPresent) attributes.add(Attributes.Attribute.newBuilder().name("Armor").type(Attributes.AttributeType.GENERIC_ARMOR).amount(strength).slot(slot).build());
		//If there's no toughness tag present add it
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
