/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities

import kernitus.plugin.OldCombatMechanics.utilities.potions.PotionDurations
import kernitus.plugin.OldCombatMechanics.utilities.potions.PotionTypeCompat
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import java.util.*
import java.util.function.Predicate
import java.util.stream.Collectors

/**
 * Various utilities for making it easier to work with [Configurations][org.bukkit.configuration.Configuration].
 *
 * @see org.bukkit.configuration.file.YamlConfiguration
 *
 * @see org.bukkit.configuration.ConfigurationSection
 */
object ConfigUtils {
    /**
     * Safely loads all doubles from a configuration section, reading both double and integer values.
     *
     * @param section The section from which to load the doubles.
     * @return The map of doubles.
     */
    fun loadDoubleMap(section: ConfigurationSection): Map<String, Double> {
        Objects.requireNonNull(section, "section cannot be null!")

        return section.getKeys(false).stream()
            .filter(Predicate { path: String -> section.isDouble(path) }.or { path: String ->
                section.isInt(
                    path
                )
            })
            .collect(
                Collectors.toMap(
                    { key: String -> key },
                    { path: String -> section.getDouble(path) })
            )
    }

    /**
     * Loads the list of [Materials][Material] with the given key from a configuration section.
     * Safely ignores non-matching materials.
     *
     * @param section The section from which to load the material list.
     * @param key     The key of the material list.
     * @return The loaded material list, or an empty list if there is no list at the given key.
     */
    @JvmStatic
    fun loadMaterialList(section: ConfigurationSection, key: String): List<Material?> {
        Objects.requireNonNull(section, "section cannot be null!")
        Objects.requireNonNull(key, "key cannot be null!")

        if (!section.isList(key)) return ArrayList()

        return section.getStringList(key).stream()
            .map { name: String? -> Material.matchMaterial(name!!) }
            .filter { obj: Material? -> Objects.nonNull(obj) }
            .collect(Collectors.toList())
    }

    /**
     * Gets potion duration values from config for all configured potion types.
     * Will create map of new potion type name to durations.
     *
     * @param section The section from which to load the duration values
     * @return HashMap of [String] and [PotionDurations]
     */
    @JvmStatic
    fun loadPotionDurationsList(section: ConfigurationSection): HashMap<PotionTypeCompat, PotionDurations> {
        Objects.requireNonNull(section, "potion durations section cannot be null!")

        val durationsHashMap = HashMap<PotionTypeCompat, PotionDurations>()
        val durationsSection = section.getConfigurationSection("potion-durations")

        val drinkableSection = durationsSection!!.getConfigurationSection("drinkable")
        val splashSection = durationsSection.getConfigurationSection("splash")

        for (newPotionTypeName in drinkableSection!!.getKeys(false)) {
            try {
                // Get durations in seconds and convert to ticks
                val drinkableDuration = drinkableSection.getInt(newPotionTypeName) * 20
                val splashDuration = splashSection!!.getInt(newPotionTypeName) * 20
                val potionTypeCompat = PotionTypeCompat(newPotionTypeName)

                durationsHashMap[potionTypeCompat] = PotionDurations(drinkableDuration, splashDuration)
            } catch (e: IllegalArgumentException) {
                // In case the potion doesn't exist in the version running on the server
                Messenger.debug("Skipping loading $newPotionTypeName potion")
            }
        }

        return durationsHashMap
    }
}
