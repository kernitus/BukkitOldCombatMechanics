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

    private final String oldName;
    private Enchantment enchantment;

    EnchantmentCompat(String oldName) {
        this.oldName = oldName;
    }

    public synchronized Enchantment get() {
        if (enchantment != null) {
            return enchantment;
        }

        // Try loading the new name first
        // This only happens once per enum name
        Enchantment found = Enchantment.getByName(name());
        // If the new name doesn't exist, fall back to the old name
        if (found == null) {
            found = Enchantment.getByName(oldName);
        }

        if (found == null) {
            throw new IllegalStateException("Enchantment not found for: " + name() + " or " + oldName);
        }
        this.enchantment = found;
        return found;
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
