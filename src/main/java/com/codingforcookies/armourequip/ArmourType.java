package com.codingforcookies.armourequip;

import org.bukkit.inventory.ItemStack;

/**
 * @Author Borlea
 * @Github https://github.com/borlea/
 * @Website http://codingforcookies.com/
 * @Since Jul 30, 2015 6:46:16 PM
 */
public enum ArmourType{
	HELMET(5), CHESTPLATE(6), LEGGINGS(7), BOOTS(8);

	private final int slot;

	ArmourType(int slot){
		this.slot = slot;
	}

	/**
	 * Attempts to match the ArmourType for the specified ItemStack.
	 *
	 * @param itemStack The ItemStack to parse the type of.
	 * @return The parsed ArmourType. (null if none were found.)
	 */
	public final static ArmourType matchType(final ItemStack itemStack){
		if(itemStack == null) { return null; }
		switch (itemStack.getType()){
			case DIAMOND_HELMET:
			case GOLD_HELMET:
			case IRON_HELMET:
			case CHAINMAIL_HELMET:
			case LEATHER_HELMET:
				return HELMET;
			case DIAMOND_CHESTPLATE:
			case GOLD_CHESTPLATE:
			case IRON_CHESTPLATE:
			case CHAINMAIL_CHESTPLATE:
			case LEATHER_CHESTPLATE:
				return CHESTPLATE;
			case DIAMOND_LEGGINGS:
			case GOLD_LEGGINGS:
			case IRON_LEGGINGS:
			case CHAINMAIL_LEGGINGS:
			case LEATHER_LEGGINGS:
				return LEGGINGS;
			case DIAMOND_BOOTS:
			case GOLD_BOOTS:
			case IRON_BOOTS:
			case CHAINMAIL_BOOTS:
			case LEATHER_BOOTS:
				return BOOTS;
			default:
				return null;
		}
	}

	public int getSlot(){
		return slot;
	}
}