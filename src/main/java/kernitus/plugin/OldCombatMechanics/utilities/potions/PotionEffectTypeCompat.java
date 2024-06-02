/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics.utilities.potions;

import org.bukkit.potion.PotionEffectType;

import java.util.Locale;

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

    /**
     * Gets correct PotionEffectType for currently-running server version givenn new name.
     * @param newName The PotionEffectType >=1.20.6 name
     * @return The PotionEffectType for the currently-running server version, if found
     */
    public static PotionEffectType fromNewName(String newName) {
        try {
            // See if new name needs mapping to old
            return valueOf(newName.toUpperCase(Locale.ROOT)).get();
        } catch (IllegalArgumentException e){
            // Otherwise just use new name directly
            return PotionEffectType.getByName(newName);
        }
    }
}
