package gvlfm78.plugin.OldCombatMechanics.utilities;

import gvlfm78.plugin.OldCombatMechanics.OCMMain;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by Rayzr522 on 6/14/16.
 */
public class ArmourValues {

    private static Map<String, Double> values;

    private static OCMMain plugin;

    public static void Initialise(OCMMain plugin) {
        Messenger.debug("Initialised armour values");
        ArmourValues.plugin = plugin;
        reload();
    }

    public static void reload() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("old-armour-strength.strength");

        values = section.getKeys(false).stream()
                .filter(section::isDouble)
                // .peek(key -> Messenger.debug("[ArmourValues] Loading value '" + section.getDouble(key) + "' for type '" + key + "'"))
                .collect(Collectors.toMap(key -> key, section::getDouble));
    }


    public static double getValue(Material mat) {
        return values.getOrDefault(mat.name(), 0.0);
    }
}
