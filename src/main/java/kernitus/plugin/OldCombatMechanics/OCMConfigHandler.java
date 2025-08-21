/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics;

import kernitus.plugin.OldCombatMechanics.utilities.Config;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public class OCMConfigHandler {
    private final String CONFIG_NAME = "config.yml";
    private final OCMMain plugin;

    public OCMConfigHandler(OCMMain instance) {
        this.plugin = instance;
    }

    public void upgradeConfig() {
        // Remove old backup file if present
        final File backup = getFile("config-backup.yml");
        if (backup.exists()) backup.delete();

        // Keeping YAML comments not available in lower versions
        if (Reflector.versionIsNewerOrEqualTo(1, 18, 1) ||
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
            final YamlConfiguration oldConfig = YamlConfiguration.loadConfiguration(backup);
            final YamlConfiguration newConfig = YamlConfiguration.loadConfiguration(configFile);

            // Copy user's values for keys that still exist
            for (String key : newConfig.getKeys(true)) {
                if (key.equals("config-version")) continue;
                if (newConfig.isConfigurationSection(key)) continue;

                if (oldConfig.contains(key) && !oldConfig.isConfigurationSection(key)) {
                    newConfig.set(key, oldConfig.get(key));
                }
            }

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
