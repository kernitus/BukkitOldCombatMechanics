/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics.utilities.potions;

import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public class PotionTypeCompat {
    /**
     * Map new potion type names to old (pre 1.20.5)
     */
    private enum PotionTypeMapper {
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
        LONG_SWIFTNESS("SPEED");

        private final String oldName;

        PotionTypeMapper(String oldName) {
            this.oldName = oldName;
        }
    }

    private final String oldName;
    private final String newName;
    private final PotionType potionType;
    private final boolean isStrong;
    private final boolean isLong;

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

    @Override
    public int hashCode() {
       return newName.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PotionTypeCompat && newName.equals(((PotionTypeCompat) o).newName);
    }

    /**
     * Get PotionType for currently-running server. Requires newName and oldName to already be set.
     *
     * @return PotionType if found.
     * @throws IllegalArgumentException If potion type could not be found.
     */
    private @Nullable PotionType getPotionType() {

        PotionType potionType;
        try {
            potionType = PotionType.valueOf(this.newName);
        } catch (IllegalArgumentException e) {

            // If running >=1.20.5, UNCRAFTABLE has been turned into null type
            if(this.oldName.equals("UNCRAFTABLE")) return null;

            try {
                potionType = PotionType.valueOf(this.oldName);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Invalid potion type, tried " + newName + " and " + oldName, exception);
            }
        }
        return potionType;
    }

    /**
     * Create an instance of {@link PotionTypeCompat} starting from the new potion name. (>=1.20.5)
     * Name will be turned into upper case as needed.
     *
     * @param newName The new potion type name, e.g. strong_leaping.
     */
    public PotionTypeCompat(String newName) {
        this.newName = newName.toUpperCase(Locale.ROOT);

        String oldName;
        try { // See if it's one of the potion names that changed
            oldName = PotionTypeMapper.valueOf(newName).oldName;
        } catch (IllegalArgumentException e) {
            // Name did not change, but remove modifiers
            oldName = this.newName.replace("STRONG_", "").replace("LONG_", "");
        }
        this.oldName = oldName;

        this.potionType = getPotionType();

        isStrong = newName.startsWith("STRONG_");
        isLong = newName.startsWith("LONG_");
    }


    /**
     * Create an instance of {@link PotionTypeCompat} starting from the old potion name. (<1.20.5)
     * Name will be turned into upper case as needed.
     *
     * @param oldName The old potion type name, e.g. jump.
     * @param isStrong Whether the potion is upgraded to amplifier 1 (II).
     * @param isLong Whether the potion is extended.
     */
    public PotionTypeCompat(String oldName, boolean isStrong, boolean isLong) {
        this.oldName = oldName.toUpperCase(Locale.ROOT);

        String newName = null;
        for (PotionTypeMapper mapped : PotionTypeMapper.values()) {
            // Check if the old name matches
            if (mapped.oldName.equals(this.oldName)) {
                // Check the 'strong' and 'long' flags
                final String mappedName = mapped.name();
                if (isStrong == mappedName.startsWith("STRONG_") && isLong == mappedName.startsWith("LONG_")) {
                    newName = mappedName;
                    break;
                }
            }
        }

        if (newName == null) { // Name did not change
            if (isStrong)
                this.newName = "STRONG_" + this.oldName;
            else if (isLong)
                this.newName = "LONG_" + this.oldName;
            else
                this.newName = this.oldName;
        } else this.newName = newName;

        this.potionType = getPotionType();
        this.isStrong = isStrong;
        this.isLong = isLong;
    }


    /**
     * Create an instance of {@link PotionTypeCompat} based on methods available in currently-running server version.
     * @return Instance of {@link PotionTypeCompat}, if found.
     */
    public static PotionTypeCompat fromPotionMeta(PotionMeta potionMeta) {
        try { // For >=1.20.5
            final PotionType potionType = potionMeta.getBasePotionType();
            return new PotionTypeCompat(potionType.name());
        } catch (NoSuchMethodError e) {
            final var potionData = potionMeta.getBasePotionData();
            final PotionType potionType = potionData.getType();
            return new PotionTypeCompat(potionType.name(), potionData.isUpgraded(), potionData.isExtended());
        }
    }
}
