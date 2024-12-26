/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics

import kernitus.plugin.OldCombatMechanics.utilities.Config
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.util.*

class OCMConfigHandler(private val plugin: OCMMain) {
    private val CONFIG_NAME = "config.yml"

    fun upgradeConfig() {
        // Remove old backup file if present
        val backup = getFile("config-backup.yml")
        if (backup.exists()) backup.delete()

        // Keeping YAML comments not available in lower versions
        if (Reflector.versionIsNewerOrEqualTo(1, 18, 1) ||
            Config.getConfig().getBoolean("force-below-1-18-1-config-upgrade", false)
        ) {
            plugin.logger.warning("Config version does not match, upgrading old config")
            // Load values from old config
            val oldConfig = getConfig(CONFIG_NAME)
            val defaultConfig = YamlConfiguration.loadConfiguration(
                InputStreamReader(Objects.requireNonNull(plugin.getResource(CONFIG_NAME)))
            )

            // Copies value from old config if present in new config
            for (key in defaultConfig.getKeys(true)) {
                if (key == "config-version" || !oldConfig.contains(key)) continue

                if (defaultConfig.isConfigurationSection(key)) continue  // Only get leaf keys


                defaultConfig[key] = oldConfig[key]
            }
            try {
                // Overwrites old file if needed
                defaultConfig.save(getFile(CONFIG_NAME))
                plugin.logger.info("Config has been updated")
            } catch (e: IOException) {
                plugin.logger.severe("Failed to upgrade config")
            }
        } else {
            plugin.logger.warning("Config version does not match, backing up old config and creating a new one")
            // Change name of old config
            val configFile = getFile(CONFIG_NAME)
            configFile.renameTo(backup)
        }

        // Save new version if none is present
        setupConfigIfNotPresent()
    }

    /**
     * Generates new config.yml file, if not present.
     */
    fun setupConfigIfNotPresent() {
        if (!doesConfigExist()) {
            plugin.saveDefaultConfig()
            plugin.logger.info("Config file generated")
        }
    }

    fun getConfig(fileName: String): YamlConfiguration {
        return YamlConfiguration.loadConfiguration(getFile(fileName))
    }

    fun getFile(fileName: String): File {
        return File(plugin.dataFolder, fileName.replace('/', File.separatorChar))
    }

    fun doesConfigExist(): Boolean {
        return getFile(CONFIG_NAME).exists()
    }
}
