/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities.potions

import org.bukkit.inventory.meta.PotionMeta
import org.bukkit.potion.PotionType

class PotionTypeCompat {
    /**
     * Map new potion type names to old (pre 1.20.5)
     */
    private enum class PotionTypeMapper(val oldName: String) {
        HARMING("INSTANT_DAMAGE"),
        STRONG_HARMING("INSTANT_DAMAGE"),
        HEALING("INSTANT_HEAL"),
        STRONG_HEALING("INSTANT_HEAL"),
        LEAPING("JUMP"),
        STRONG_LEAPING("JUMP"),
        LONG_LEAPING("JUMP"),
        REGENERATION("REGEN"),
        STRONG_REGENERATION("REGEN"),
        LONG_REGENERATION("REGEN"),
        SWIFTNESS("SPEED"),
        STRONG_SWIFTNESS("SPEED"),
        LONG_SWIFTNESS("SPEED")
    }

    companion object {
        /**
         * Create an instance of [PotionTypeCompat] based on methods available in currently-running server version.
         * @return Instance of [PotionTypeCompat], if found.
         */
        fun fromPotionMeta(potionMeta: PotionMeta): PotionTypeCompat {
            try { // For >=1.20.5
                val potionType = potionMeta.basePotionType
                return PotionTypeCompat(potionType!!.name)
            } catch (e: NoSuchMethodError) {
                val potionData = potionMeta.basePotionData
                val potionType = potionData!!.type
                return PotionTypeCompat(potionType.name, potionData.isUpgraded, potionData.isExtended)
            }
        }
    }

    private val oldName: String

    val newName: String
    val type: PotionType?

    val isStrong: Boolean
    val isLong: Boolean

    override fun hashCode() = newName.hashCode()

    override fun equals(other: Any?) = other is PotionTypeCompat && newName == other.newName

    /**
     * Get PotionType for currently-running server. Requires newName and oldName to already be set.
     *
     * @return PotionType if found.
     * @throws IllegalArgumentException If potion type could not be found.
     */
    private fun getPotionType(): PotionType? {
        val potionType = try {
            PotionType.valueOf(this.newName)
        } catch (e: IllegalArgumentException) {
            // If running >=1.20.5, UNCRAFTABLE has been turned into null type

            if (this.oldName == "UNCRAFTABLE") return null

            try {
                PotionType.valueOf(this.oldName)
            } catch (exception: IllegalArgumentException) {
                throw IllegalArgumentException("Invalid potion type, tried $newName and $oldName", exception)
            }
        }
        return potionType
    }

    /**
     * Create an instance of [PotionTypeCompat] starting from the new potion name. (>=1.20.5)
     * Name will be turned into upper case as needed.
     *
     * @param newName The new potion type name, e.g. strong_leaping.
     */
    constructor(newName: String) {
        this.newName = newName.uppercase()

        val oldName: String
        oldName = try { // See if it's one of the potion names that changed
            PotionTypeMapper.valueOf(newName).oldName
        } catch (e: IllegalArgumentException) {
            // Name did not change, but remove modifiers
            this.newName.replace("STRONG_", "").replace("LONG_", "")
        }
        this.oldName = oldName

        this.type = getPotionType()

        isStrong = newName.startsWith("STRONG_")
        isLong = newName.startsWith("LONG_")
    }


    /**
     * Create an instance of [PotionTypeCompat] starting from the old potion name. (<1.20.5)
     * Name will be turned into upper case as needed.
     *
     * @param oldName The old potion type name, e.g. jump.
     * @param isStrong Whether the potion is upgraded to amplifier 1 (II).
     * @param isLong Whether the potion is extended.
     */
    constructor(oldName: String, isStrong: Boolean, isLong: Boolean) {
        this.oldName = oldName.uppercase()

        var newName: String? = null
        for (mapped in PotionTypeMapper.entries) {
            // Check if the old name matches
            if (mapped.oldName == this.oldName) {
                // Check the 'strong' and 'long' flags
                val mappedName = mapped.name
                if (isStrong == mappedName.startsWith("STRONG_") && isLong == mappedName.startsWith("LONG_")) {
                    newName = mappedName
                    break
                }
            }
        }

        if (newName == null) { // Name did not change
            if (isStrong) this.newName = "STRONG_" + this.oldName
            else if (isLong) this.newName = "LONG_" + this.oldName
            else this.newName = this.oldName
        } else this.newName = newName

        this.type = getPotionType()
        this.isStrong = isStrong
        this.isLong = isLong
    }
}
