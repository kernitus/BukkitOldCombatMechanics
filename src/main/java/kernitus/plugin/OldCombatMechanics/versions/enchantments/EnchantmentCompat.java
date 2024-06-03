/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics.versions.enchantments;

import org.bukkit.enchantments.Enchantment;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public enum EnchantmentCompat {
    UNBREAKING("DURABILITY"),
    PROTECTION("PROTECTION_ENVIRONMENTAL"),
    FIRE_PROTECTION("PROTECTION_FIRE"),
    BLAST_PROTECTION("PROTECTION_EXPLOSIONS"),
    PROJECTILE_PROTECTION("PROTECTION_PROJECTILE"),
    FEATHER_FALLING("PROTECTION_FALL"),
    SMITE("DAMAGE_UNDEAD"),
    BANE_OF_ARTHROPODS("DAMAGE_ARTHROPODS"),
    SHARPNESS("DAMAGE_ALL");

    private Enchantment enchantment;

    EnchantmentCompat(String oldName) {
        // Try loading the new name first
        // This only happens once per enum name
        enchantment = Enchantment.getByName(name());
        // If the new name doesn't exist, fall back to the old name
        if (enchantment == null) {
            enchantment = Enchantment.getByName(oldName);
        }

        if (enchantment == null) {
            throw new IllegalStateException("PotionEffectType not found for: " + name() + " or " + oldName);
        }
    }

    public Enchantment get() {
        return enchantment;
    }

    /**
     * Gets correct {@link Enchantment} for currently-running server version given new name.
     *
     * @param newName The {@link Enchantment} >=1.20.6 name
     * @return The {@link Enchantment} for the currently-running server version, or null if not found.
     */
    public static @Nullable Enchantment fromNewName(String newName) {
        try {
            // See if new name needs mapping to old
            return valueOf(newName.toUpperCase(Locale.ROOT)).get();
        } catch (IllegalArgumentException e) {
            // Otherwise use new name directly
            return Enchantment.getByName(newName);
        }
    }
}
