/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics.utilities.potions;

import org.bukkit.potion.PotionType;

public enum PotionTypeCompat {
    HARMING("INSTANT_DAMAGE"),
    HEALING("INSTANT_HEAL"),
    LEAPING("JUMP"),
    REGENERATION("REGEN"),
    SWIFTNESS("SPEED"),
    // uncraftable -> null

    //long_ and strong_ versions of these potions

    ;

    private PotionType potionType;

    PotionTypeCompat(String oldName) {
        // Try loading the new name first
        // This only happens once per enum name
        try {
            potionType = PotionType.valueOf(name());
        } catch (IllegalArgumentException e) {
            // If the new name doesn't exist, fall back to the old name
            // Will through IllegalArgumentException if doesn't exist
            potionType = PotionType.valueOf(oldName);
        }
    }

    public PotionType get() {
        return potionType;
    }
}
