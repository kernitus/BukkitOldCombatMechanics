/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.versions.materials

import org.bukkit.Material
import org.bukkit.inventory.ItemStack

/**
 * A material that tries each name in a given list until it finds a working material.
 */
class NameListVersionedMaterial private constructor(private val finalMaterial: Material) : VersionedMaterial {
    override fun newInstance() = ItemStack(finalMaterial)

    override fun isSame(other: ItemStack) = other.type == finalMaterial

    companion object {
        /**
         * Returns a new [VersionedMaterial] that picks the first working one from a list of names.
         *
         * @param names the names of the materials
         * @return the versioned material
         * @throws IllegalArgumentException if no material was valid
         */
        fun ofNames(vararg names: String): VersionedMaterial {
            for (name in names) {
                var material = Material.matchMaterial(name)
                if (material != null) {
                    return NameListVersionedMaterial(material)
                }

                material = Material.matchMaterial(name, true)
                if (material != null) {
                    return NameListVersionedMaterial(material)
                }
            }

            throw IllegalArgumentException(
                "Could not find any working material, tried: " + java.lang.String.join(
                    ",", *names
                ) + "."
            )
        }
    }
}
