/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.versions.materials;

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
