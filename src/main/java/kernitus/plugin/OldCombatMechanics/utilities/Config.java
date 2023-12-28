/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities;

import kernitus.plugin.OldCombatMechanics.ModuleLoader;
import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.module.OCMModule;
import kernitus.plugin.OldCombatMechanics.utilities.damage.EntityDamageByEntityListener;
import kernitus.plugin.OldCombatMechanics.utilities.damage.WeaponDamages;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.InputStreamReader;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class Config {

    private static final String CONFIG_NAME = "config.yml";
    private static OCMMain plugin;
    private static FileConfiguration config;
    private static final Map<String, Set<String>> modesets = new HashMap<>();

    public static void initialise(OCMMain plugin) {
        Config.plugin = plugin;
        config = plugin.getConfig();
        // Make sure to separately call reload()
    }

    /**
     * @return Whether config was changed or not
     */
    private static boolean checkConfigVersion() {
        final YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                new InputStreamReader(Objects.requireNonNull(plugin.getResource(CONFIG_NAME))));

        if (config.getInt("config-version") != defaultConfig.getInt("config-version")) {
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

        // checkConfigVersion will call #reload() again anyways
        if (checkConfigVersion()) return;

        Messenger.reloadConfig(config.getBoolean("debug.enabled"), config.getString("message-prefix"));

        WeaponDamages.initialise(plugin); //Reload weapon damages from config

        //Set EntityDamagedByEntityListener to enabled if either of these modules is enabled
        final EntityDamageByEntityListener EDBEL = EntityDamageByEntityListener.getINSTANCE();
        if (EDBEL != null) {
            EDBEL.setEnabled(moduleEnabled("old-tool-damage") ||
                    moduleEnabled("old-potion-effects")
                    || moduleEnabled("old-critical-hits")
            );
        }

        reloadModesets();

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

    private static void reloadModesets() {
        modesets.clear();

        final Set<String> moduleNames = ModuleLoader.getModules().stream().map(OCMModule::getConfigName).collect(Collectors.toSet());
        final ConfigurationSection modesetsSection = config.getConfigurationSection("modesets");

        // A set to keep track of all the modules that are already in a modeset
        final Set<String> modulesInModesets = new HashSet<>();

        // Iterate over each modeset
        for (String key : modesetsSection.getKeys(false)) {
            // Retrieve the list of module names for the current modeset
            final List<String> moduleList = modesetsSection.getStringList(key);
            final Set<String> moduleSet = new HashSet<>(moduleList);

            // Add the current modeset and its modules to the map
            modesets.put(key, moduleSet);

            // Add all modules in the current modeset to the tracking set
            modulesInModesets.addAll(moduleSet);
        }

        // Find modules not present in any modeset
        final Set<String> modulesNotInAnyModeset = new HashSet<>(moduleNames);
        modulesNotInAnyModeset.removeAll(modulesInModesets);

        // Add any module not present in any modeset to all modesets
        for (Set<String> modeSet : modesets.values()) {
            modeSet.addAll(modulesNotInAnyModeset);
        }
    }

    public static boolean moduleEnabled(String name, World world) {
        final ConfigurationSection section = config.getConfigurationSection(name);

        if (section == null) {
            plugin.getLogger().warning("Tried to check module '" + name + "', but it didn't exist!");
            return false;
        }

        if (!section.getBoolean("enabled")) return false;
        if (world == null) return true;

        final String worldName = world.getName();
        final List<String> list = section.getStringList("worlds");

        // If the list is empty, the module should be enabled in all worlds
        if (list.size() == 0) return true;

        boolean isInList = list.stream().anyMatch(entry -> entry.equalsIgnoreCase(worldName));

        final boolean isBlacklist = section.getBoolean("blacklist", false);
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

    /**
     * Only use if you can't access config through plugin instance
     *
     * @return config.yml instance
     */
    public static FileConfiguration getConfig() {
        return plugin.getConfig();
    }

    public static Map<String, Set<String>> getModesets(){
        return modesets;
    }
}
