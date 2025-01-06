/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.versions.materials

import kernitus.plugin.OldCombatMechanics.utilities.reflection.VersionCompatUtils
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import java.util.function.Supplier

/**
 * A material that has a version for before 1.13 and after 1.13.
 */
class DualVersionedMaterial
/**
 * Creates a new dual versioned material.
 *
 * @param oldItem the item supplier for the old version
 * @param newItem the item supplier for the new version
 */(private val oldItem: Supplier<ItemStack>, private val newItem: Supplier<ItemStack>) :
    VersionedMaterial {

    companion object {
        /**
         * Returns a new [DualVersionedMaterial] based on the material names.
         *
         * @param nameOld the old name
         * @param nameNew the new name
         * @return a dual versioned material using the supplied names
         */
        fun ofMaterialNames(nameOld: String, nameNew: String) =
            DualVersionedMaterial(fromMaterial(nameOld), fromMaterial(nameNew))

        private fun fromMaterial(name: String): () -> ItemStack {
            return {
                val material = Material.matchMaterial(name) ?: throw IllegalArgumentException("Invalid material $name")
                ItemStack(material)
            }
        }
    }

    override fun newInstance() = itemSupplier.get()

    @Suppress("deprecation")
    override fun isSame(other: ItemStack): Boolean {
        val baseInstance = newInstance()

        // items do not differ in more than those two things
        return baseInstance.type == other.type && baseInstance.durability == other.durability
    }

    private val itemSupplier: Supplier<ItemStack>
        get() = if (VersionCompatUtils.versionIsNewerOrEqualTo(
                1,
                13,
                0
            )
        ) newItem else oldItem

    override fun toString(): String {
        return ("DualVersionedMaterial{" +
                "picked=" + (if (itemSupplier === newItem) "new" else "old")
                + ", item=" + newInstance()
                + '}')
    }

}
