package gvlfm78.plugin.OldCombatMechanics.utilities;

import gvlfm78.plugin.OldCombatMechanics.OCMMain;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;

/**
 * Created by Rayzr522 on 6/14/16.
 */
public class ArmorValues {

    private static HashMap<String, Double> values = new HashMap<String, Double>();

    private static OCMMain plugin;
    private static FileConfiguration config;

    public ArmorValues(OCMMain instance) {
		ArmorValues.plugin = instance;
	}

    public static void Initialise(OCMMain plugin) {

        ArmorValues.plugin = plugin;
        reload();

    }

    public static void reload() {

        config = plugin.getConfig();

        ConfigurationSection section = config.getConfigurationSection("old-armour-strength.strength");

        for (String key : section.getKeys(false)) {

            values.put(key, section.getDouble(key));

        }

    }


    public double getValue(Material mat) {

        if (!values.containsKey(mat.name())) {
            return -1;
        }

        return values.get(mat.name());

    }
}
