package gvlfm78.plugin.OldCombatMechanics.utilities;

import gvlfm78.plugin.OldCombatMechanics.OCMMain;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;

public class WeaponDamages {

    private static HashMap<String, Double> damages;

    private static OCMMain plugin;
    private static FileConfiguration config;

    public static void Initialise(OCMMain plugin) {

        WeaponDamages.plugin = plugin;
        reload();

    }

    public static void reload() {

        config = plugin.getConfig();

        ConfigurationSection section = config.getConfigurationSection("old-tool-damage.damages");

        damages = new HashMap<>();

        for (String key : section.getKeys(false)) {

            double val = section.getDouble(key);
            Messenger.debug("[WeaponDamages] Loading damage '" + val + "' for type '" + key + "'");

            damages.put(key, section.getDouble(key));

        }

    }


    public static double getDamage(Material mat) {

        if (!damages.containsKey(mat.name()))
            return -1;

        return damages.get(mat.name());

    }
}
