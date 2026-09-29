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
class ConfigMigrationIntegrationTest :
    FunSpec({
        val testPlugin = JavaPlugin.getPlugin(OCMTestMain::class.java)
        val ocm = JavaPlugin.getPlugin(OCMMain::class.java)

        extensions(MainThreadDispatcherExtension(testPlugin))

        fun runSync(action: () -> Unit) {
            if (Bukkit.isPrimaryThread()) {
                action()
            } else {
                Bukkit
                    .getScheduler()
                    .callSyncMethod(
                        testPlugin,
                        Callable {
                            action()
                            null
                        },
                    ).get()
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
                        linkedMapOf(
                            "custom" to listOf("disable-offhand"),
                            "alt" to listOf("old-golden-apples"),
                        ),
                    )
                    oldConfig.set("worlds.__default__", null)
                    oldConfig.set("worlds.world", listOf("custom", "alt"))
                    oldConfig.set("worlds.nether", listOf("alt"))

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

                    val modesetsSection =
                        upgradedConfig.getConfigurationSection("modesets")
                            ?: error("Modesets section missing after migration")
                    modesetsSection.getKeys(false).shouldContain("custom")
                    modesetsSection.getKeys(false).shouldContain("alt")

                    upgradedConfig.getStringList("modesets.custom").shouldNotContain("disable-offhand")
                    upgradedConfig.getStringList("modesets.alt").shouldContain("old-golden-apples")
                    upgradedConfig.getStringList("worlds.world") shouldBe listOf("custom", "alt")
                    upgradedConfig.getStringList("worlds.nether") shouldBe listOf("alt")
                    upgradedConfig.getStringList("worlds.__default__") shouldBe listOf("custom", "alt")
                }
            }
        }

        test("modern config upgrade rejects duplicate and conflicting assignments") {
            runSync {
                for (conflict in listOf(false, true)) {
                    withConfigFile {
                        val configFile = File(ocm.dataFolder, "config.yml")
                        val oldConfig = YamlConfiguration.loadConfiguration(configFile)
                        oldConfig.set("config-version", oldConfig.getInt("config-version") - 1)
                        oldConfig.set("force-below-1-18-1-config-upgrade", true)
                        ModuleLoader.getModules().forEach { oldConfig.set("${it.configName}.enabled", null) }
                        if (conflict) {
                            oldConfig.set(
                                "disabled_modules",
                                oldConfig.getStringList("disabled_modules") + "fishing-rod-velocity",
                            )
                        } else {
                            oldConfig.set(
                                "always_enabled_modules",
                                oldConfig.getStringList("always_enabled_modules") + "fishing-rod-velocity",
                            )
                        }
                        oldConfig.save(configFile)
                        io.kotest.assertions.throwables
                            .shouldThrow<IllegalStateException> { Config.reload() }
                    }
                }
            }
        }

        test("config upgrade preserves explicitly empty modern modesets") {
            runSync {
                withConfigFile {
                    val configFile = File(ocm.dataFolder, "config.yml")
                    val oldConfig = YamlConfiguration.loadConfiguration(configFile)
                    oldConfig.set("config-version", oldConfig.getInt("config-version") - 1)
                    oldConfig.set("force-below-1-18-1-config-upgrade", true)
                    val section = oldConfig.getConfigurationSection("modesets")!!
                    val modesetModules = section.getKeys(false).flatMap { section.getStringList(it) }
                    val always =
                        (oldConfig.getStringList("always_enabled_modules") + modesetModules)
                            .distinct()
                            .filter { it != "old-potion-throwing" }
                    oldConfig.set("always_enabled_modules", always)
                    oldConfig.set("modesets", emptyMap<String, Any>())
                    oldConfig.save(configFile)
                    Config.reload()
                    ocm.config
                        .getConfigurationSection("modesets")!!
                        .getKeys(false)
                        .isEmpty() shouldBe true
                    Config.getModesets().isEmpty() shouldBe true
                    ocm.config.getStringList("always_enabled_modules") shouldBe always
                    ocm.config.getStringList("disabled_modules").shouldContain("old-potion-throwing")
                }
            }
        }

        test("config upgrade preserves modern assignments and safely introduces potion throwing") {
            runSync {
                withConfigFile {
                    val configFile = File(ocm.dataFolder, "config.yml")
                    val oldConfig = YamlConfiguration.loadConfiguration(configFile)
                    oldConfig.set("config-version", oldConfig.getInt("config-version") - 1)
                    oldConfig.set("force-below-1-18-1-config-upgrade", true)
                    ModuleLoader.getModules().forEach { oldConfig.set("${it.configName}.enabled", null) }
                    val oldModes = oldConfig.getStringList("modesets.old").filter { it != "old-potion-throwing" }
                    oldConfig.set("modesets.custom", oldModes)
                    oldConfig.set("modesets.old", null)
                    val always = oldConfig.getStringList("always_enabled_modules") + "attack-range"
                    val disabled = oldConfig.getStringList("disabled_modules").filter { it != "attack-range" }
                    oldConfig.set("always_enabled_modules", always)
                    oldConfig.set("disabled_modules", disabled)
                    oldConfig.save(configFile)
                    Config.reload()
                    ocm.config.getStringList("always_enabled_modules") shouldBe always
                    ocm.config.getStringList("disabled_modules").toSet() shouldBe
                        (disabled + "old-potion-throwing").toSet()
                    ocm.config.getStringList("modesets.custom") shouldBe oldModes
                }
            }
        }

        test("config upgrade preserves existing default world modesets") {
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
                        linkedMapOf(
                            "custom" to listOf("disable-offhand"),
                            "alt" to listOf("old-golden-apples"),
                        ),
                    )
                    // Keep this modern assignment valid while testing preservation of the world default.
                    oldConfig.set(
                        "disabled_modules",
                        oldConfig.getStringList("disabled_modules").filter {
                            it !=
                                "disable-offhand"
                        },
                    )
                    oldConfig.set("worlds.__default__", listOf("legacy-default"))
                    oldConfig.set("worlds.world", listOf("custom"))

                    oldConfig.save(configFile)

                    Config.reload()

                    val upgradedConfig = ocm.config
                    upgradedConfig.getInt("config-version") shouldBe currentVersion
                    upgradedConfig.getStringList("worlds.__default__") shouldBe listOf("legacy-default")
                    upgradedConfig.getStringList("worlds.world") shouldBe listOf("custom")
                }
            }
        }
    })
