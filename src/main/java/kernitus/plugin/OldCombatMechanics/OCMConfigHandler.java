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
import java.io.InputStreamReader;
import java.util.Objects;

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
            // Load values from old config
            final YamlConfiguration oldConfig = getConfig(CONFIG_NAME);
            final YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(Objects.requireNonNull(plugin.getResource(CONFIG_NAME))));

            // Copies value from old config if present in new config
            for (String key : defaultConfig.getKeys(true)) {
                if (key.equals("config-version") || !oldConfig.contains(key)) continue;

                if (defaultConfig.isConfigurationSection(key)) continue; // Only get leaf keys

                defaultConfig.set(key, oldConfig.get(key));
            }
            try {
                // Overwrites old file if needed
                defaultConfig.save(getFile(CONFIG_NAME));
                plugin.getLogger().info("Config has been updated");
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to upgrade config");
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
