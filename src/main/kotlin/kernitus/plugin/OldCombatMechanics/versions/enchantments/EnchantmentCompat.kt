/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.versions.enchantments

import org.bukkit.enchantments.Enchantment

enum class EnchantmentCompat(oldName: String) {
    UNBREAKING("DURABILITY"),
    PROTECTION("PROTECTION_ENVIRONMENTAL"),
    FIRE_PROTECTION("PROTECTION_FIRE"),
    BLAST_PROTECTION("PROTECTION_EXPLOSIONS"),
    PROJECTILE_PROTECTION("PROTECTION_PROJECTILE"),
    FEATHER_FALLING("PROTECTION_FALL"),
    SMITE("DAMAGE_UNDEAD"),
    BANE_OF_ARTHROPODS("DAMAGE_ARTHROPODS"),
    SHARPNESS("DAMAGE_ALL");

    // Try loading the new name first
    // This only happens once per enum name
    private var enchantment: Enchantment = Enchantment.getByName(name)
        ?: Enchantment.getByName(oldName)
        ?: throw IllegalStateException("PotionEffectType not found for: $name or $oldName")

    fun get(): Enchantment {
        return enchantment
    }

    companion object {
        /**
         * Gets correct [Enchantment] for currently-running server version given new name.
         *
         * @param newName The [Enchantment] >=1.20.6 name
         * @return The [Enchantment] for the currently-running server version, or null if not found.
         */
        fun fromNewName(newName: String): Enchantment? {
            return try {
                // See if new name needs mapping to old
                valueOf(newName.uppercase())
                    .get()
            } catch (e: IllegalArgumentException) {
                // Otherwise use new name directly
                Enchantment.getByName(newName)
            }
        }
    }
}
