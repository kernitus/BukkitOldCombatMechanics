package gvlfm78.plugin.OldCombatMechanics;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;

/**
 * Created by Rayzr522 on 6/14/16.
 */
public class WeaponDamages {

    private static HashMap<String, Double> damages = new HashMap<String, Double>();

    private static OCMMain plugin;
    private static FileConfiguration config;
    
    public WeaponDamages(OCMMain instance) {
		WeaponDamages.plugin = instance;
	}

    public static void Initialise(OCMMain plugin) {

        WeaponDamages.plugin = plugin;
        reload();

    }

    public static void reload() {

        config = plugin.getConfig();

        ConfigurationSection section = config.getConfigurationSection("old-tool-damage.damages");

        for (String key : section.getKeys(false)) {

            damages.put(key, section.getDouble(key));

        }

    }


    public double getDamage(Material mat) {

        if (!damages.containsKey(mat.name())) {
            return -1;
        }

        return damages.get(mat.name());

    }
}
