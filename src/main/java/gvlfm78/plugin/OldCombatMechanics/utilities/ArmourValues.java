package gvlfm78.plugin.OldCombatMechanics.utilities;

import gvlfm78.plugin.OldCombatMechanics.OCMMain;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;

/**
 * Created by Rayzr522 on 6/14/16.
 */
public class ArmourValues {

    private static HashMap<String, Double> values;

    private static OCMMain plugin;
    private static FileConfiguration config;

    public static void Initialise(OCMMain plugin) {
        Messenger.debug("Initialised armour values");
        ArmourValues.plugin = plugin;
        reload();

    }

    public static void reload() {

        config = plugin.getConfig();

        ConfigurationSection section = config.getConfigurationSection("old-armour-strength.strength");

        values = new HashMap<>();

        for (String key : section.getKeys(false)) {

            double val = section.getDouble(key);
            Messenger.debug("[ArmourValues] Loading value '" + val + "' for type '" + key + "'");

            values.put(key, section.getDouble(key));

        }
    }


    public static double getValue(Material mat) {

        if (!values.containsKey(mat.name()))
            return 0;

        return values.get(mat.name());
    }
}
