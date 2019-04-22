package gvlfm78.plugin.OldCombatMechanics.versions.materials;

import org.bukkit.inventory.ItemStack;

/**
 * A material in different versions.
 */
public interface VersionedMaterial {

    /**
     * Creates a new item stack.
     *
     * @return the created item stack
     */
    ItemStack newInstance();

    /**
     * Returns whether the item stack is of this material.
     *
     * @param other the itemstack
     * @return true if the item stack is of this material.
     */
    boolean isSame(ItemStack other);
}
