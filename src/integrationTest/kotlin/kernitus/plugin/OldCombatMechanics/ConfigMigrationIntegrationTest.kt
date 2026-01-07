/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics

import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import kernitus.plugin.OldCombatMechanics.utilities.Config
import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.concurrent.Callable

@OptIn(ExperimentalKotest::class)
class ConfigMigrationIntegrationTest : FunSpec({
    val testPlugin = JavaPlugin.getPlugin(OCMTestMain::class.java)
    val ocm = JavaPlugin.getPlugin(OCMMain::class.java)

    extensions(MainThreadDispatcherExtension(testPlugin))

    fun runSync(action: () -> Unit) {
        if (Bukkit.isPrimaryThread()) {
            action()
        } else {
            Bukkit.getScheduler().callSyncMethod(testPlugin, Callable {
                action()
                null
            }).get()
        }
    }

    fun withConfigFile(block: () -> Unit) {
        val dataFolder = ocm.dataFolder
        val configFile = File(dataFolder, "config.yml")
        val backupFile = File(dataFolder, "config-backup.yml")
        val originalConfig = if (configFile.exists()) configFile.readText() else ""
        val hadBackup = backupFile.exists()
        val originalBackup = if (hadBackup) backupFile.readText() else null

        try {
            block()
        } finally {
            if (!configFile.parentFile.exists()) {
                configFile.parentFile.mkdirs()
            }
            configFile.writeText(originalConfig)
            if (hadBackup) {
                backupFile.writeText(originalBackup ?: "")
            } else if (backupFile.exists()) {
                backupFile.delete()
            }
            ocm.reloadConfig()
            Config.reload()
        }
    }

    test("config upgrade migrates module buckets and preserves modesets") {
        runSync {
            withConfigFile {
                val configFile = File(ocm.dataFolder, "config.yml")
                val oldConfig = YamlConfiguration.loadConfiguration(configFile)
                val currentVersion = oldConfig.getInt("config-version")
                val oldVersion = currentVersion - 1

                oldConfig.set("config-version", oldVersion)
                oldConfig.set("force-below-1-18-1-config-upgrade", true)

                oldConfig.set(
                    "modesets",
                    mapOf(
                        "custom" to listOf("disable-offhand"),
                        "alt" to listOf("old-golden-apples")
                    )
                )
                oldConfig.set("worlds.world", listOf("custom", "alt"))

                oldConfig.set("disable-offhand.enabled", false)
                oldConfig.set("old-golden-apples.enabled", true)
                oldConfig.set("old-potion-effects.enabled", true)
                oldConfig.set("disable-attack-cooldown.enabled", false)

                oldConfig.save(configFile)

                Config.reload()

                val upgradedConfig = ocm.config
                upgradedConfig.getInt("config-version") shouldBe currentVersion

                val alwaysEnabled = upgradedConfig.getStringList("always_enabled_modules")
                val disabledModules = upgradedConfig.getStringList("disabled_modules")

                alwaysEnabled.shouldContain("old-potion-effects")
                disabledModules.shouldContain("disable-offhand")
                disabledModules.shouldContain("disable-attack-cooldown")
                disabledModules.shouldNotContain("old-golden-apples")

                val modesetsSection = upgradedConfig.getConfigurationSection("modesets")
                    ?: error("Modesets section missing after migration")
                modesetsSection.getKeys(false).shouldContain("custom")
                modesetsSection.getKeys(false).shouldContain("alt")

                upgradedConfig.getStringList("modesets.custom").shouldNotContain("disable-offhand")
                upgradedConfig.getStringList("modesets.alt").shouldContain("old-golden-apples")
                upgradedConfig.getStringList("worlds.world") shouldBe listOf("custom", "alt")
            }
        }
    }
})
