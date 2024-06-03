/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities;

import kernitus.plugin.OldCombatMechanics.utilities.potions.PotionDurations;
import kernitus.plugin.OldCombatMechanics.utilities.potions.PotionTypeCompat;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Various utilities for making it easier to work with {@link org.bukkit.configuration.Configuration Configurations}.
 *
 * @see org.bukkit.configuration.file.YamlConfiguration
 * @see org.bukkit.configuration.ConfigurationSection
 */
public class ConfigUtils {

    /**
     * Safely loads all doubles from a configuration section, reading both double and integer values.
     *
     * @param section The section from which to load the doubles.
     * @return The map of doubles.
     */
    public static Map<String, Double> loadDoubleMap(ConfigurationSection section) {
        Objects.requireNonNull(section, "section cannot be null!");

        return section.getKeys(false).stream()
                .filter(((Predicate<String>) section::isDouble).or(section::isInt))
                .collect(Collectors.toMap(key -> key, section::getDouble));
    }

    /**
     * Loads the list of {@link Material Materials} with the given key from a configuration section.
     * Safely ignores non-matching materials.
     *
     * @param section The section from which to load the material list.
     * @param key     The key of the material list.
     * @return The loaded material list, or an empty list if there is no list at the given key.
     */
    public static List<Material> loadMaterialList(ConfigurationSection section, String key) {
        Objects.requireNonNull(section, "section cannot be null!");
        Objects.requireNonNull(key, "key cannot be null!");

        if (!section.isList(key)) return new ArrayList<>();

        return section.getStringList(key).stream()
                .map(Material::matchMaterial)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Gets potion duration values from config for all configured potion types.
     * Will create map of new potion type name to durations.
     *
     * @param section The section from which to load the duration values
     * @return HashMap of {@link String} and {@link PotionDurations}
     */
    public static HashMap<PotionTypeCompat, PotionDurations> loadPotionDurationsList(ConfigurationSection section) {
        Objects.requireNonNull(section, "potion durations section cannot be null!");

        final HashMap<PotionTypeCompat, PotionDurations> durationsHashMap = new HashMap<>();
        final ConfigurationSection durationsSection = section.getConfigurationSection("potion-durations");

        final ConfigurationSection drinkableSection = durationsSection.getConfigurationSection("drinkable");
        final ConfigurationSection splashSection = durationsSection.getConfigurationSection("splash");

        for (String newPotionTypeName : drinkableSection.getKeys(false)) {
            try {
                // Get durations in seconds and convert to ticks
                final int drinkableDuration = drinkableSection.getInt(newPotionTypeName) * 20;
                final int splashDuration = splashSection.getInt(newPotionTypeName) * 20;
                final PotionTypeCompat potionTypeCompat = new PotionTypeCompat(newPotionTypeName);

                durationsHashMap.put(potionTypeCompat, new PotionDurations(drinkableDuration, splashDuration));
            } catch (IllegalArgumentException e) {
                // In case the potion doesn't exist in the version running on the server
                Messenger.debug("Skipping loading " + newPotionTypeName + " potion");
            }
        }

        return durationsHashMap;
    }

}
