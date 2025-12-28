/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities.potions;

import com.cryptomorin.xseries.XPotion;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionType;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Key for potion duration configuration, based on effect and strength/length flags.
 */
public final class PotionKey {
    private static final String STRONG_PREFIX = "STRONG_";
    private static final String LONG_PREFIX = "LONG_";

    private final XPotion potion;
    private final boolean strong;
    private final boolean extended;

    private PotionKey(XPotion potion, boolean strong, boolean extended) {
        this.potion = Objects.requireNonNull(potion, "potion");
        this.strong = strong;
        this.extended = extended;
    }

    public XPotion getPotion() {
        return potion;
    }

    public boolean isStrong() {
        return strong;
    }

    public boolean isExtended() {
        return extended;
    }

    public boolean isPotion(XPotion target) {
        return potion == target;
    }

    public String getDebugName() {
        if (strong) {
            return STRONG_PREFIX + potion.name();
        }
        if (extended) {
            return LONG_PREFIX + potion.name();
        }
        return potion.name();
    }

    public static Optional<PotionKey> fromConfigKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        String name = key.toUpperCase(Locale.ROOT);
        boolean strong = false;
        boolean extended = false;

        if (name.startsWith(STRONG_PREFIX)) {
            strong = true;
            name = name.substring(STRONG_PREFIX.length());
        } else if (name.startsWith(LONG_PREFIX)) {
            extended = true;
            name = name.substring(LONG_PREFIX.length());
        }

        return fromBaseName(name, strong, extended);
    }

    public static Optional<PotionKey> fromPotionMeta(PotionMeta potionMeta) {
        if (potionMeta == null) {
            return Optional.empty();
        }

        try {
            PotionType potionType = potionMeta.getBasePotionType();
            if (potionType == null) {
                return Optional.empty();
            }
            return fromPotionTypeName(potionType.name(), false, false);
        } catch (NoSuchMethodError e) {
            PotionData potionData = potionMeta.getBasePotionData();
            return fromPotionTypeName(potionData.getType().name(), potionData.isUpgraded(), potionData.isExtended());
        }
    }

    private static Optional<PotionKey> fromPotionTypeName(String name, boolean upgraded, boolean extended) {
        String baseName = name.toUpperCase(Locale.ROOT);
        boolean strong = upgraded;
        boolean longDuration = extended;

        if (baseName.startsWith(STRONG_PREFIX)) {
            strong = true;
            baseName = baseName.substring(STRONG_PREFIX.length());
        } else if (baseName.startsWith(LONG_PREFIX)) {
            longDuration = true;
            baseName = baseName.substring(LONG_PREFIX.length());
        }

        return fromBaseName(baseName, strong, longDuration);
    }

    private static Optional<PotionKey> fromBaseName(String baseName, boolean strong, boolean extended) {
        Optional<XPotion> potion = XPotion.matchXPotion(baseName);
        if (!potion.isPresent()) {
            return Optional.empty();
        }

        XPotion found = potion.get();
        if (found == XPotion.INSTANT_DAMAGE || found == XPotion.INSTANT_HEALTH) {
            return Optional.empty();
        }

        return Optional.of(new PotionKey(found, strong, extended));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PotionKey)) {
            return false;
        }
        PotionKey that = (PotionKey) other;
        return strong == that.strong && extended == that.extended && potion == that.potion;
    }

    @Override
    public int hashCode() {
        return Objects.hash(potion, strong, extended);
    }

    @Override
    public String toString() {
        return "PotionKey{" + getDebugName() + "}";
    }
}
