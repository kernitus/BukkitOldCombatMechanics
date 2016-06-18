package kernitus.plugin.OldCombatMechanics;

import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Created by Rayzr522 on 6/14/16.
 */
public class Config {

    public static final int CONFIG_VERSION = 2;

    private static OCMMain plugin;
    private static FileConfiguration config;

    public static void Initialize(OCMMain plugin) {

        Config.plugin = plugin;
        config = plugin.getConfig();

        if (config.getInt("config-version") != CONFIG_VERSION) {
            plugin.getLogger().warning("Config version does not match, resetting config to default values");
            plugin.upgradeConfig();
            reload();
        }

        WeaponDamages.Initialize(plugin);

    }

    public static void reload() {

        if (plugin.doesConfigymlExist()) {
            plugin.reloadConfig();
            config = plugin.getConfig();
        } else
            plugin.upgradeConfig();

        plugin.restartTask();

    }

    public static boolean moduleEnabled(String name, World world) {

        ConfigurationSection section = config.getConfigurationSection(name);

        if (section == null) {
            System.err.println("Tried to check module '" + name + "', but it didn't exist!");
            return false;
        }

        if (section.getBoolean("enabled")) {

            if (world != null && section.getList("worlds").size() > 0 && !section.getList("worlds").contains(world.getName())) {

                return false;

            }

            return true;

        }

        return false;

    }

    public static boolean moduleEnabled(String name) {
        return moduleEnabled(name, null);
    }

}
