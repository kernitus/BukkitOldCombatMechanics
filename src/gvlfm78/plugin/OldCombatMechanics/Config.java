package gvlfm78.plugin.OldCombatMechanics;

import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Created by Rayzr522 on 6/14/16.
 */
public class Config {

    private static OCMMain plugin;
    private static FileConfiguration config;

    public static void Initialize(OCMMain plugin) {

        Config.plugin = plugin;
        Config.config = plugin.getConfig();

    }

    public static void reload() {

        plugin.reloadConfig();
        config = plugin.getConfig();

    }

    public static boolean moduleEnabled(String name, World world) {

        ConfigurationSection section = config.getConfigurationSection(name);

        if (section == null) {
            System.err.println("Tried to check module '" + name + "', but it didn't exist!");
            return false;
        }

        System.out.println("section.getBoolean(\"enabled\") = " + section.getBoolean("enabled"));

        if (section.getBoolean("enabled")) {

            if (world != null && section.getList("worlds").size() > 0 && !section.getList("worlds").contains(world.getName())) {

                System.out.println("one");
                return false;

            }

            System.out.println("two");
            return true;

        }

        System.out.println("three");
        return false;

    }

    public static boolean moduleEnabled(String name) {
        return moduleEnabled(name, null);
    }

}
