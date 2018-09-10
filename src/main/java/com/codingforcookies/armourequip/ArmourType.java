package com.codingforcookies.armourequip;

import org.bukkit.inventory.ItemStack;

/**
 * Enum declarations by (<a href="http://codingforcookies.com/">Barlea</a>), methods by us.
 */
public enum ArmourType {
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
    public static ArmourType matchType(final ItemStack itemStack){
        if(itemStack == null){
            return null;
        }

        String typeName = itemStack.getType().name();

        if(typeName.endsWith("_HELMET")){
            return HELMET;
        } else if(typeName.endsWith("_CHESTPLATE")){
            return CHESTPLATE;
        } else if(typeName.endsWith("_LEGGINGS")){
            return LEGGINGS;
        } else if(typeName.endsWith("_BOOTS")){
            return BOOTS;
        } else {
            return null;
        }
    }

    public int getSlot(){
        return slot;
    }
}