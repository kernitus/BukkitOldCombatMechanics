/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics.utilities.potions;

import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public enum PotionTypeCompat {
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
    LONG_SWIFTNESS("SPEED"),

    UNCHANGED("") // For potion type names that have not changed
    ;

    private final String oldName;
    private String newName;

    private PotionType potionType;
    private boolean isStrong;
    private boolean isLong;

    PotionTypeCompat(String oldName) {
        this.oldName = oldName.toUpperCase(Locale.ROOT);
        this.newName = name();
        if (oldName.isEmpty()) return; // No values to fill in

        computeModifiers();

        // Try loading the new name first
        // This only happens once per enum name
        try {
            potionType = PotionType.valueOf(name());
        } catch (IllegalArgumentException e) {
            System.out.println("Could not load " + name() + " trying " + oldName);
            // If the new name doesn't exist, fall back to the old name
            // Will throw IllegalArgumentException if it doesn't exist
            potionType = PotionType.valueOf(oldName);
        }
    }

    private void computeModifiers() {
        isStrong = name().startsWith("STRONG_");
        isLong = name().startsWith("LONG_");
    }

    public PotionType getType() {
        return potionType;
    }

    public boolean isStrong() {
        return isStrong;
    }

    public boolean isLong() {
        return isLong;
    }

    public String getNewName() {
        return newName;
    }

    /**
     * Gets correct {@link PotionType} for currently-running server version given new name.
     *
     * @param newName The {@link PotionType} >=1.20.6 name
     * @return The {@link PotionType} for the currently-running server version, if found
     * @throws IllegalArgumentException If value is not found
     */
    public static @NotNull PotionTypeCompat fromNewName(String newName) {
        final String upperName = newName.toUpperCase(Locale.ROOT);
        PotionTypeCompat compat;

        try { // See if it's one of the potion names that changed
            compat = valueOf(upperName);
        } catch (IllegalArgumentException e) {
            compat = UNCHANGED; // Use the generic entry for unlisted types
        }

        if (compat.potionType == null) {
            // For the UNCHANGED case, try to match directly to a PotionType
            try {
                compat.potionType = PotionType.valueOf(upperName);
                compat.computeModifiers();
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Invalid potion type: " + newName, ex);
            }
        }

        compat.newName = newName;
        return compat;
    }

    public static @NotNull PotionTypeCompat fromOldName(String oldName, boolean isStrong, boolean isLong) {
        final String upperName = oldName.toUpperCase(Locale.ROOT);

        for (PotionTypeCompat potionTypeCompat : values()) {
            // Check if the old name matches
            if (potionTypeCompat.oldName != null && potionTypeCompat.oldName.equals(upperName)) {
                // Check the 'strong' and 'long' flags
                if (potionTypeCompat.isStrong == isStrong && potionTypeCompat.isLong == isLong) {
                    return potionTypeCompat;
                }
            }
        }

        // If no match is found, return UNCHANGED with the appropriate modifiers
        final PotionTypeCompat compat = UNCHANGED;
        compat.potionType = PotionType.valueOf(upperName);

        compat.isStrong = isStrong;
        compat.isLong = isLong;

        // Name did not change, but might have modifiers
        if (isStrong) compat.newName = "STRONG_" + upperName;
        else if (isLong) compat.newName = "LONG_" + upperName;
        else compat.newName = upperName;

        return compat;
    }


    public static PotionTypeCompat getPotionTypeCompat(PotionMeta potionMeta) {
        try { // For >=1.20
            final PotionType potionType = potionMeta.getBasePotionType();
            return fromNewName(potionType.name());
        } catch (NoSuchMethodError e) {
            final var potionData = potionMeta.getBasePotionData();
            final PotionType potionType = potionData.getType();
            return fromOldName(potionType.name(), potionData.isUpgraded(), potionData.isExtended());
        }
    }
}
