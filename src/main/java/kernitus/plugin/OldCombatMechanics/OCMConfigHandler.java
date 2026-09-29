/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics;

import kernitus.plugin.OldCombatMechanics.utilities.Config;
import kernitus.plugin.OldCombatMechanics.utilities.CompatibilityCapabilities;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class OCMConfigHandler {
    private final String CONFIG_NAME = "config.yml";
    private final OCMMain plugin;

    public OCMConfigHandler(OCMMain instance) {
        this.plugin = instance;
    }

    public void upgradeConfig() {
        // Reject invalid modern assignments before renaming or replacing the user's configuration.
        final YamlConfiguration candidate = YamlConfiguration.loadConfiguration(getFile(CONFIG_NAME));
        final boolean legacyToggles = ModuleLoader.getModules().stream()
                .anyMatch(module -> candidate.contains(module.getConfigName() + ".enabled"))
                || candidate.contains("disable-attack-sounds.enabled")
                || candidate.contains("disable-sword-sweep-particles.enabled");
        if (!legacyToggles) validateModernAssignments(candidate);

        // Remove old backup file if present
        final File backup = getFile("config-backup.yml");
        if (backup.exists()) backup.delete();

        // Only auto-upgrade when Bukkit can parse and save YAML comments without stripping the default config.
        if (CompatibilityCapabilities.canPreserveYamlComments() ||
                Config.getConfig().getBoolean("force-below-1-18-1-config-upgrade", false)
        ) {
            plugin.getLogger().warning("Config version does not match, upgrading old config");

            final File configFile = getFile(CONFIG_NAME);

            // Back up the old config file
            if (!configFile.renameTo(backup)) {
                plugin.getLogger().severe("Could not back up old config file. Aborting config upgrade.");
                return;
            }

            // Save the new default config from the JAR to config.yml. This ensures all old keys are gone.
            plugin.saveResource(CONFIG_NAME, true);

            // Now, load the old values from the backup and the new config from the fresh file
            final YamlConfiguration oldConfig = CompatibilityCapabilities.loadCommentPreservingYaml(backup);
            final YamlConfiguration newConfig = CompatibilityCapabilities.loadCommentPreservingYaml(configFile);

            // Copy user's values for keys that still exist
            for (String key : newConfig.getKeys(true)) {
                if (key.equals("config-version")) continue;
                if (newConfig.isConfigurationSection(key)) continue;

                if (oldConfig.contains(key) && !oldConfig.isConfigurationSection(key)) {
                    newConfig.set(key, oldConfig.get(key));
                }
            }

            migrateModuleLists(oldConfig, newConfig);

            // Save the final, merged config
            try {
                newConfig.save(configFile);
                plugin.getLogger().info("Config has been updated. A backup of your old config is available at config-backup.yml");
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to save upgraded config. It has been restored from backup.");
                e.printStackTrace();
                backup.renameTo(configFile); // Restore backup
            }
        } else {
            plugin.getLogger().warning("Config version does not match, backing up old config and creating a new one");
            // Change name of old config
            final File configFile = getFile(CONFIG_NAME);
            configFile.renameTo(backup);
        }

        // Save new version if none is present
        setupConfigIfNotPresent();
    }

    /**
     * Generates new config.yml file, if not present.
     */
    public void setupConfigIfNotPresent() {
        if (!doesConfigExist()) {
            plugin.saveDefaultConfig();
            plugin.getLogger().info("Config file generated");
        }
    }

    private static void validateModernAssignments(YamlConfiguration config) {
        final Set<String> internalModules = new HashSet<>(Arrays.asList(
                "modeset-listener", "attack-cooldown-tracker", "entity-damage-listener"));
        final Map<String, String> assigned = new LinkedHashMap<>();
        final Map<String, String> lists = new LinkedHashMap<>();
        lists.put("always_enabled_modules", "always_enabled_modules");
        lists.put("disabled_modules", "disabled_modules");
        final ConfigurationSection modes = config.getConfigurationSection("modesets");
        if (modes != null) modes.getKeys(false).forEach(name -> lists.put("modesets." + name, "modesets"));
        for (Map.Entry<String, String> list : lists.entrySet()) {
            final Set<String> seen = new HashSet<>();
            for (String entry : config.getStringList(list.getKey())) {
                final String name = entry.toLowerCase(Locale.ROOT);
                final String category = assigned.putIfAbsent(name, list.getValue());
                if (!seen.add(name) || internalModules.contains(name)
                        || (category != null && !category.equals(list.getValue()))) {
                    throw new IllegalStateException("Invalid module assignment configuration: " + name + " in " + list.getKey());
                }
            }
        }
    }

    private void migrateModuleLists(YamlConfiguration oldConfig, YamlConfiguration newConfig) {
        final Set<String> internalModules = new HashSet<>(Arrays.asList(
                "modeset-listener",
                "attack-cooldown-tracker",
                "entity-damage-listener"
        ));
        final Set<String> optionalModules = new HashSet<>(Arrays.asList(
                "disable-attack-sounds",
                "disable-sword-sweep-particles"
        ));
        final Set<String> moduleNames = ModuleLoader.getModules().stream()
                .map(module -> module.getConfigName().toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        final ConfigurationSection oldModesets = oldConfig.getConfigurationSection("modesets");
        final Map<String, List<String>> migratedModesets = new LinkedHashMap<>();
        final Set<String> modulesInModesets = new HashSet<>();

        if (oldModesets != null) {
            for (String modesetName : oldModesets.getKeys(false)) {
                final List<String> moduleList = oldModesets.getStringList(modesetName);
                final List<String> normalisedList = moduleList.stream()
                        .map(name -> name.toLowerCase(Locale.ROOT))
                        .collect(Collectors.toList());
                normalisedList.removeIf(internalModules::contains);
                migratedModesets.put(modesetName, normalisedList);
                modulesInModesets.addAll(normalisedList);
            }
        }

        moduleNames.addAll(optionalModules);

        if (moduleNames.isEmpty()) {
            for (String key : oldConfig.getKeys(true)) {
                if (!key.endsWith(".enabled")) {
                    continue;
                }
                final String moduleName = key.substring(0, key.length() - ".enabled".length())
                        .toLowerCase(Locale.ROOT);
                if (internalModules.contains(moduleName)) {
                    continue;
                }
                moduleNames.add(moduleName);
            }
            moduleNames.addAll(modulesInModesets);
        }

        moduleNames.removeAll(internalModules);

        final List<String> alwaysEnabled = new ArrayList<>();
        final List<String> disabledModules = new ArrayList<>();

        final boolean hasLegacyToggles = moduleNames.stream().anyMatch(name -> oldConfig.contains(name + ".enabled"));
        final Set<String> oldAlways = oldConfig.getStringList("always_enabled_modules").stream()
                .map(name -> name.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        final Set<String> oldDisabled = oldConfig.getStringList("disabled_modules").stream()
                .map(name -> name.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        if (!hasLegacyToggles) {
            alwaysEnabled.addAll(oldConfig.getStringList("always_enabled_modules"));
            disabledModules.addAll(oldConfig.getStringList("disabled_modules"));
        }
        // Introduce the independent throwing module in the existing old modeset only.
        // Custom configurations without that modeset can opt in explicitly.
        final String potionThrowing = "old-potion-throwing";
        if (!modulesInModesets.contains(potionThrowing) && !oldAlways.contains(potionThrowing)
                && !oldDisabled.contains(potionThrowing) && migratedModesets.containsKey("old")) {
            migratedModesets.get("old").add(potionThrowing);
            modulesInModesets.add(potionThrowing);
        }

        for (String moduleName : moduleNames) {
            if (!hasLegacyToggles) {
                if (oldDisabled.contains(moduleName) || oldAlways.contains(moduleName)) continue;
                if (modulesInModesets.contains(moduleName)) continue;
            }
            if (potionThrowing.equals(moduleName) && !modulesInModesets.contains(moduleName)) {
                disabledModules.add(moduleName);
                continue;
            }
            final String enabledKey = moduleName + ".enabled";
            if ("attack-range".equals(moduleName)) {
                disabledModules.add(moduleName);
                continue;
            }
            final boolean enabled = !oldConfig.contains(enabledKey) || oldConfig.getBoolean(enabledKey);

            if (!enabled) {
                disabledModules.add(moduleName);
                continue;
            }

            if (!modulesInModesets.contains(moduleName)) {
                alwaysEnabled.add(moduleName);
            }
        }

        // Remove disabled modules from all modesets
        if (!disabledModules.isEmpty()) {
            for (Map.Entry<String, List<String>> entry : migratedModesets.entrySet()) {
                entry.getValue().removeIf(disabledModules::contains);
            }
        }

        newConfig.set("always_enabled_modules", alwaysEnabled);
        newConfig.set("disabled_modules", disabledModules);

        if (oldModesets != null) {
            ConfigurationSection targetModesets = newConfig.getConfigurationSection("modesets");
            if (targetModesets == null) {
                targetModesets = newConfig.createSection("modesets");
            }
            // Remove old keys that are no longer present, without deleting the section (keeps placement)
            for (String key : new ArrayList<>(targetModesets.getKeys(false))) {
                if (!migratedModesets.containsKey(key)) {
                    targetModesets.set(key, null);
                }
            }
            // Apply migrated entries
            for (Map.Entry<String, List<String>> entry : migratedModesets.entrySet()) {
                targetModesets.set(entry.getKey(), entry.getValue());
            }
        }

        final ConfigurationSection oldWorlds = oldConfig.getConfigurationSection("worlds");
        boolean hasDefaultWorld = false;
        if (oldWorlds != null) {
            hasDefaultWorld = oldWorlds.contains("__default__");
            for (String worldName : oldWorlds.getKeys(false)) {
                newConfig.set("worlds." + worldName, oldWorlds.getStringList(worldName));
            }
        }
        if (!hasDefaultWorld) {
            final ConfigurationSection upgradedModesets = newConfig.getConfigurationSection("modesets");
            if (upgradedModesets != null) {
                newConfig.set("worlds.__default__", new ArrayList<>(upgradedModesets.getKeys(false)));
            }
        }
    }

    public YamlConfiguration getConfig(String fileName) {
        return YamlConfiguration.loadConfiguration(getFile(fileName));
    }

    public File getFile(String fileName) {
        return new File(plugin.getDataFolder(), fileName.replace('/', File.separatorChar));
    }

    public boolean doesConfigExist() {
        return getFile(CONFIG_NAME).exists();
    }
}
