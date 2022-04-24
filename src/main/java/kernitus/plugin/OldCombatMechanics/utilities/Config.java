/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities;

import kernitus.plugin.OldCombatMechanics.ModuleLoader;
import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.damage.EntityDamageByEntityListener;
import kernitus.plugin.OldCombatMechanics.utilities.damage.WeaponDamages;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class Config {

    private static OCMMain plugin;
    private static FileConfiguration config;
    private static List<Material> interactive = new ArrayList<>();

    public static void initialise(OCMMain plugin) {
        Config.plugin = plugin;
        config = plugin.getConfig();

        reload();
    }

    /**
     * @return Whether config was changed or not
     */
    private static boolean checkConfigVersion() {
        YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(plugin.getResource("config.yml")));

        if (config.getInt("config-version") != defaultConfig.getInt("config-version")) {
            plugin.getLogger().warning("Config version does not match, backing up old config and creating a new one");
            plugin.upgradeConfig();
            reload();
            return true;
        }

        return false;
    }


    public static void reload() {
        if (plugin.doesConfigExist()) {
            plugin.reloadConfig();
            config = plugin.getConfig();
        } else
            plugin.upgradeConfig();

        if (checkConfigVersion()) {
            // checkConfigVersion will call #reload() again anyways
            return;
        }

        Messenger.DEBUG_ENABLED = config.getBoolean("debug.enabled");

        WeaponDamages.initialise(plugin); //Reload weapon damages from config

        // Load all interactive blocks (used by sword blocking and elytra modules)
        reloadInteractiveBlocks();

        //Set EntityDamagedByEntityListener to enabled if either of these modules is enabled
        if(EntityDamageByEntityListener.getINSTANCE() != null)
            EntityDamageByEntityListener.getINSTANCE().setEnabled(
                moduleEnabled("old-tool-damage") || moduleEnabled("old-potion-effects"));

        // Dynamically registers / unregisters all event listeners for optimal performance!
        ModuleLoader.toggleModules();

        ModuleLoader.getModules().forEach(module -> {
            try {
                module.reload();
            } catch (Exception e) {
                plugin.getLogger()
                        .log(Level.WARNING, "Error reloading module '" + module.toString() + "'", e);
            }
        });
    }

    public static boolean moduleEnabled(String name, World world) {
        boolean isBlacklist = config.getBoolean("worlds-is-blacklist");
        ConfigurationSection section = config.getConfigurationSection(name);

        if (section == null) {
            plugin.getLogger().warning("Tried to check module '" + name + "', but it didn't exist!");
            return false;
        }

        if (!section.getBoolean("enabled")) return false;
        if (world == null) return true;

        final String worldName = world.getName();
        final List<String> list = section.getStringList("worlds");

        // If the list is empty, the module should be enabled in all worlds
        if (list.size() <= 0) return true;

        boolean isInList = list.stream().anyMatch(entry -> entry.equalsIgnoreCase(worldName));
        return isBlacklist != isInList;
    }

    public static boolean moduleEnabled(String name) {
        return moduleEnabled(name, null);
    }

    public static boolean debugEnabled() {
        return moduleEnabled("debug", null);
    }

    public static List<?> getWorlds(String moduleName) {
        return config.getList(moduleName + ".worlds");
    }

    public static boolean moduleSettingEnabled(String moduleName, String moduleSettingName) {
        return config.getBoolean(moduleName + "." + moduleSettingName);
    }

    public static void setModuleSetting(String moduleName, String moduleSettingName, boolean value) {
        config.set(moduleName + "." + moduleSettingName, value);
        plugin.saveConfig();
    }

    private static void reloadInteractiveBlocks() {
        interactive = ConfigUtils.loadMaterialList(config, "interactive");
    }

    public static List<Material> getInteractiveBlocks() {
        return interactive;
    }

    /**
     * Only use if you can't access config through plugin instance
     *
     * @return config.yml instance
     */
    public static FileConfiguration getConfig() {
        return plugin.getConfig();
    }
}
