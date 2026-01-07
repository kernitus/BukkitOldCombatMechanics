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
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import javax.annotation.Nullable;
import java.io.InputStreamReader;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class Config {

    private static final String CONFIG_NAME = "config.yml";
    private static OCMMain plugin;
    private static FileConfiguration config;
    private static final Map<String, Set<String>> modesets = new HashMap<>();
    private static final Map<UUID, Set<String>> worlds = new HashMap<>();
    private static final Set<String> alwaysEnabledModules = new HashSet<>();
    private static final Set<String> disabledModules = new HashSet<>();
    private static final Set<String> optionalModules = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "disable-attack-sounds",
            "disable-sword-sweep-particles"
    )));
    private static final Set<String> internalModules = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "modeset-listener",
            "attack-cooldown-tracker",
            "entity-damage-listener"
    )));

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

        reloadModesets();
        reloadWorlds();

        //Set EntityDamagedByEntityListener to enabled if either of these modules is enabled
        final EntityDamageByEntityListener EDBEL = EntityDamageByEntityListener.getINSTANCE();
        if (EDBEL != null) {
            EDBEL.setEnabled(moduleEnabled("old-tool-damage") ||
                    moduleEnabled("old-potion-effects")
                    || moduleEnabled("old-critical-hits")
            );
        }

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
        alwaysEnabledModules.clear();
        disabledModules.clear();

        final Set<String> moduleNames = ModuleLoader.getModules().stream()
                .map(OCMModule::getConfigName)
                .map(Config::normaliseModuleName)
                .collect(Collectors.toSet());
        final Set<String> knownModuleNames = new HashSet<>(moduleNames);
        knownModuleNames.addAll(optionalModules);
        final Set<String> configurableModuleNames = new HashSet<>(knownModuleNames);
        configurableModuleNames.removeAll(internalModules);
        final ConfigurationSection modesetsSection = config.getConfigurationSection("modesets");
        if (modesetsSection == null) {
            throw new IllegalStateException("Missing 'modesets' section in config. Every module must be assigned to a modeset, always_enabled_modules, or disabled_modules.");
        }

        final List<String> errors = new ArrayList<>();

        // A set to keep track of all the modules that are already in a modeset
        final Set<String> modulesInModesets = new HashSet<>();

        final List<String> alwaysModules = config.getStringList("always_enabled_modules");
        for (String moduleName : alwaysModules) {
            final String normalised = normaliseModuleName(moduleName);
            if (internalModules.contains(normalised)) {
                errors.add("Internal module should not be configured: " + moduleName);
                continue;
            }
            if (!configurableModuleNames.contains(normalised)) {
                Messenger.warn("Unknown module in always_enabled_modules: " + moduleName);
                continue;
            }
            alwaysEnabledModules.add(normalised);
        }

        final List<String> disabledModuleList = config.getStringList("disabled_modules");
        for (String moduleName : disabledModuleList) {
            final String normalised = normaliseModuleName(moduleName);
            if (internalModules.contains(normalised)) {
                errors.add("Internal module should not be configured: " + moduleName);
                continue;
            }
            if (!configurableModuleNames.contains(normalised)) {
                Messenger.warn("Unknown module in disabled_modules: " + moduleName);
                continue;
            }
            disabledModules.add(normalised);
        }

        for (String moduleName : alwaysEnabledModules) {
            if (disabledModules.contains(moduleName)) {
                errors.add("Module listed in both always_enabled_modules and disabled_modules: " + moduleName);
            }
        }

        // Iterate over each modeset
        for (String key : modesetsSection.getKeys(false)) {
            // Retrieve the list of module names for the current modeset
            final List<String> moduleList = modesetsSection.getStringList(key);
            final Set<String> moduleSet = new HashSet<>();
            for (String moduleName : moduleList) {
                final String normalised = normaliseModuleName(moduleName);
                if (internalModules.contains(normalised)) {
                    errors.add("Internal module should not be configured: " + moduleName);
                    continue;
                }
                if (!configurableModuleNames.contains(normalised)) {
                    Messenger.warn("Unknown module in modeset '%s': %s", key, moduleName);
                    continue;
                }
                if (disabledModules.contains(normalised)) {
                    errors.add("Module listed in disabled_modules and modeset '" + key + "': " + moduleName);
                    continue;
                }
                if (alwaysEnabledModules.contains(normalised)) {
                    errors.add("Module listed in always_enabled_modules and modeset '" + key + "': " + moduleName);
                    continue;
                }
                moduleSet.add(normalised);
            }

            // Add the current modeset and its modules to the map
            modesets.put(key, moduleSet);

            // Add all modules in the current modeset to the tracking set
            modulesInModesets.addAll(moduleSet);
        }

        for (String moduleName : configurableModuleNames) {
            if (disabledModules.contains(moduleName)) {
                continue;
            }
            if (alwaysEnabledModules.contains(moduleName)) {
                continue;
            }
            if (modulesInModesets.contains(moduleName)) {
                continue;
            }
            errors.add("Module not assigned to any list: " + moduleName);
        }

        if (!errors.isEmpty()) {
            final String message = "Invalid module assignment configuration:\n - " + String.join("\n - ", errors)
                    + "\nEvery module must appear in exactly one of always_enabled_modules, disabled_modules, or modesets.";
            throw new IllegalStateException(message);
        }

        alwaysEnabledModules.addAll(internalModules);
    }

    private static void reloadWorlds() {
        worlds.clear();

        final ConfigurationSection worldsSection = config.getConfigurationSection("worlds");

        // Iterate over each world
        for (String worldName : worldsSection.getKeys(false)) {
            final World world = Bukkit.getWorld(worldName);
            if(world == null){
                Messenger.warn("Configured world " + worldName + " not found, skipping (might be loaded later?)...");
                continue;
            }
            addWorld(world, worldsSection);
        }
    }

    public static void addWorld(World world){
        final ConfigurationSection worldsSection = config.getConfigurationSection("worlds");
        addWorld(world, worldsSection);
    }

    public static void addWorld(World world, ConfigurationSection worldsSection) {
        // Retrieve the list of modeset names for the current world
        // Using a linkedhashset to remove duplicates but retain insertion order (important for default modeset)
        final LinkedHashSet<String> modesetsSet = new LinkedHashSet<>(worldsSection.getStringList(world.getName()));

        // Add the current world and its modesets to the map
        worlds.put(world.getUID(), modesetsSet);
    }

    public static void removeWorld(World world){
        worlds.remove(world.getUID());
    }

    /**
     * Get the default modeset for the given world.
     * @param worldId The UUID for the world to check the allowed modesets for
     * @return The default modeset, if found. Otherwise null.
     */
    public static @Nullable Set<String> getDefaultModeset(UUID worldId){
        if(!worlds.containsKey(worldId)) return null;

        final Set<String> set = worlds.get(worldId);
        if(set == null || set.isEmpty()) return null;

        final Iterator<String> iterator = set.iterator();
        if(iterator.hasNext()) {
            final String modesetName = iterator.next();
            if(modesets.containsKey(modesetName)){
                return modesets.get(modesetName);
            }
        }

        return null;
    }

    /**
     * Checks whether the module is present in the default modeset for the specified world
     * @param world The world to get the default modeset for
     * @return Whether the module is enabled for the found modeset
     */
    public static boolean moduleEnabled(String moduleName, World world) {
        final String normalised = normaliseModuleName(moduleName);
        if (disabledModules.contains(normalised)) return false;
        if (alwaysEnabledModules.contains(normalised)) return true;
        if (world == null) return isModuleInAnyModeset(normalised); // Only checking if module is globally enabled

        final Set<String> defaultModeset = getDefaultModeset(world.getUID());
        // If no default modeset found, the module should be enabled
        if(defaultModeset == null){
            return isModuleInAnyModeset(normalised);
        }

        // Check if module is in default modeset
        return defaultModeset.contains(normalised);
    }

    /**
     * Check if module is globally enable under its own config section
     * @param moduleName The name of the module to check
     * @return Whether the module has enabled: true in its config section
     */
    public static boolean moduleEnabled(String moduleName) {
        return moduleEnabled(moduleName, null);
    }

    public static boolean debugEnabled() {
        return config.getBoolean("debug.enabled");
    }

    public static boolean moduleSettingEnabled(String moduleName, String moduleSettingName) {
        return config.getBoolean(moduleName + "." + moduleSettingName);
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

    public static boolean isModuleAlwaysEnabled(String moduleName) {
        return alwaysEnabledModules.contains(normaliseModuleName(moduleName));
    }

    public static boolean isModuleDisabled(String moduleName) {
        return disabledModules.contains(normaliseModuleName(moduleName));
    }

    public static boolean isModuleInAnyModeset(String moduleName) {
        return modesets.values().stream().anyMatch(set -> set.contains(normaliseModuleName(moduleName)));
    }

    public static Map<UUID, Set<String>> getWorlds() {
        return worlds;
    }

    private static String normaliseModuleName(String moduleName) {
        return moduleName == null ? "" : moduleName.toLowerCase(Locale.ROOT);
    }
}
