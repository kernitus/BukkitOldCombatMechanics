package kernitus.plugin.OldCombatMechanics.utilities;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Various utilities for making it easier to work with {@link org.bukkit.configuration.Configuration Configurations}.
 *
 * @author Rayzr522
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
    public static Map<String, Double> loadDoubleMap(ConfigurationSection section){
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
    public static List<Material> loadMaterialList(ConfigurationSection section, String key){
        Objects.requireNonNull(section, "section cannot be null!");
        Objects.requireNonNull(key, "key cannot be null!");

        if(!section.isList(key)){
            return new ArrayList<>();
        }

        return section.getStringList(key).stream()
                .map(Material::matchMaterial)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}
