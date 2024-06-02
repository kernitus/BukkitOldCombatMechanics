/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics.utilities.potions;

import org.bukkit.potion.PotionEffectType;

public enum PotionEffectTypeCompat {
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

    private PotionEffectType potionEffectType;

    PotionEffectTypeCompat(String oldName) {
        // Try loading the new name first
        // This only happens once per enum name
        potionEffectType = PotionEffectType.getByName(name());
        // If the new name doesn't exist, fall back to the old name
        if (potionEffectType == null) {
            potionEffectType = PotionEffectType.getByName(oldName);
        }

        if (potionEffectType == null) {
            throw new IllegalStateException("PotionEffectType not found for: " + name() + " or " + oldName);
        }
    }

    public PotionEffectType get() {
        return potionEffectType;
    }

    // Static method to get the custom enum from the Bukkit PotionEffectType
    public static PotionEffectTypeCompat fromBukkit(PotionEffectType bukkitType) {
        for (PotionEffectTypeCompat compatType : values()) {
            if (compatType.get().equals(bukkitType)) {
                return compatType;
            }
        }
        throw new IllegalArgumentException("No matching PotionEffectTypeCompat for " + bukkitType.getName());
    }
}
