package gvlfm78.plugin.OldCombatMechanics.utilities;

import gvlfm78.plugin.OldCombatMechanics.OCMMain;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class WeaponDamages {

    private static Map<String, Double> damages;

    private static OCMMain plugin;
    private static FileConfiguration config;

    public static void Initialise(OCMMain plugin) {
        WeaponDamages.plugin = plugin;
        reload();
    }

    public static void reload() {
        config = plugin.getConfig();

        ConfigurationSection section = config.getConfigurationSection("old-tool-damage.damages");

        damages = section.getKeys(false).stream()
                .filter(section::isDouble)
                // .peek(key -> Messenger.debug("[WeaponDamages] Loading damage '" + section.getDouble(key) + "' for type '" + key + "'"))
                .collect(Collectors.toMap(key -> key, section::getDouble));
    }

    public static double getDamage(Material mat) {
        return damages.getOrDefault(mat.name(), -1.0);
    }
}
