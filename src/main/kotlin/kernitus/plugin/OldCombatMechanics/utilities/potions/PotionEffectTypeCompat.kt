/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities.potions

import org.bukkit.potion.PotionEffectType

enum class PotionEffectTypeCompat(oldName: String) {
    RESISTANCE("DAMAGE_RESISTANCE"),
    NAUSEA("CONFUSION"),
    HASTE("FAST_DIGGING"),
    INSTANT_DAMAGE("HARM"),
    INSTANT_HEALTH("HEAL"),
    STRENGTH("INCREASE_DAMAGE"),
    JUMP_BOOST("JUMP"),
    SLOWNESS("SLOW"),
    MINING_FATIGUE("SLOW_DIGGING"),
    ;

    private var potionEffectType: PotionEffectType =
        PotionEffectType.getByName(name)
            ?: PotionEffectType.getByName(oldName)
            ?: throw IllegalStateException("PotionEffectType not found for: $name or $oldName")

    fun get(): PotionEffectType {
        return potionEffectType
    }

    companion object {
        /**
         * Gets correct PotionEffectType for currently-running server version given new name.
         * @param newName The PotionEffectType >=1.20.6 name
         * @return The PotionEffectType for the currently-running server version, or null if not found.
         */
        @JvmStatic
        fun fromNewName(newName: String): PotionEffectType? {
            return try {
                // See if new name needs mapping to old
                valueOf(newName.uppercase())
                    .get()
            } catch (e: IllegalArgumentException) {
                // Otherwise use new name directly
                PotionEffectType.getByName(newName)
            }
        }
    }
}
