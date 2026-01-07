/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics

import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.assertions.throwables.shouldThrow
import kernitus.plugin.OldCombatMechanics.module.ModuleDisableOffHand
import kernitus.plugin.OldCombatMechanics.utilities.Config
import kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage.getPlayerData
import kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage.setPlayerData
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.Locale
import java.util.concurrent.Callable

@OptIn(ExperimentalKotest::class)
class ModesetRulesIntegrationTest : FunSpec({
    val testPlugin = JavaPlugin.getPlugin(OCMTestMain::class.java)
    val ocm = JavaPlugin.getPlugin(OCMMain::class.java)
    val module = ModuleLoader.getModules()
        .filterIsInstance<ModuleDisableOffHand>()
        .firstOrNull() ?: error("ModuleDisableOffHand not registered")
    val internalModules = setOf(
        "modeset-listener",
        "attack-cooldown-tracker",
        "entity-damage-listener"
    )
    val optionalModules = setOf(
        "disable-attack-sounds",
        "disable-sword-sweep-particles"
    )

    lateinit var player: Player
    lateinit var fakePlayer: FakePlayer

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

    fun setModeset(player: Player, modeset: String) {
        val playerData = getPlayerData(player.uniqueId)
        playerData.setModesetForWorld(player.world.uid, modeset)
        setPlayerData(player.uniqueId, playerData)
    }

    fun snapshotSection(path: String): Any? {
        val section = ocm.config.getConfigurationSection(path)
        return section?.getValues(false) ?: ocm.config.get(path)
    }

    fun restoreSection(path: String, value: Any?) {
        ocm.config.set(path, null)
        when (value) {
            null -> Unit
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                ocm.config.createSection(path, value as Map<String, Any?>)
            }
            else -> ocm.config.set(path, value)
        }
    }

    fun applyConfig(
        always: List<String>,
        disabled: List<String>,
        modesets: Map<String, List<String>>,
        worldModesets: List<String>
    ) {
        ocm.config.set("always_enabled_modules", always)
        ocm.config.set("disabled_modules", disabled)
        ocm.config.set("modesets", null)
        ocm.config.createSection("modesets", modesets)
        ocm.config.set("worlds.world", worldModesets)
        ocm.saveConfig()
        Config.reload()
    }

    fun completeAlways(
        always: List<String>,
        disabled: List<String>,
        modesets: Map<String, List<String>>
    ): List<String> {
        val assigned = HashSet<String>()
        always.forEach { assigned.add(it.lowercase(Locale.ROOT)) }
        disabled.forEach { assigned.add(it.lowercase(Locale.ROOT)) }
        modesets.values.flatten().forEach { assigned.add(it.lowercase(Locale.ROOT)) }

        val filled = LinkedHashSet<String>()
        filled.addAll(always)
        ModuleLoader.getModules()
            .map { it.configName.lowercase(Locale.ROOT) }
            .sorted()
            .filterNot { assigned.contains(it) }
            .filterNot { internalModules.contains(it) }
            .forEach { filled.add(it) }
        optionalModules
            .filterNot { assigned.contains(it) }
            .forEach { filled.add(it) }
        return filled.toList()
    }

    suspend fun withConfig(block: suspend () -> Unit) {
        val originalAlways = ocm.config.get("always_enabled_modules")
        val originalDisabled = ocm.config.get("disabled_modules")
        val originalModesets = snapshotSection("modesets")
        val originalWorlds = snapshotSection("worlds")

        try {
            block()
        } finally {
            runSync {
                ocm.config.set("always_enabled_modules", originalAlways)
                ocm.config.set("disabled_modules", originalDisabled)
                restoreSection("modesets", originalModesets)
                restoreSection("worlds", originalWorlds)
                ocm.saveConfig()
                Config.reload()
            }
        }
    }

    beforeSpec {
        runSync {
            val world = checkNotNull(Bukkit.getServer().getWorld("world"))
            fakePlayer = FakePlayer(testPlugin)
            fakePlayer.spawn(Location(world, 0.0, 100.0, 0.0))
            player = checkNotNull(Bukkit.getPlayer(fakePlayer.uuid))
        }
    }

    afterSpec {
        runSync {
            fakePlayer.removePlayer()
        }
    }

    test("always-enabled modules apply regardless of modeset") {
        withConfig {
            runSync {
                applyConfig(
                    always = completeAlways(
                        always = listOf("disable-offhand"),
                        disabled = emptyList(),
                        modesets = mapOf(
                            "old" to listOf("old-golden-apples"),
                            "new" to listOf("old-potion-effects")
                        )
                    ),
                    disabled = emptyList(),
                    modesets = mapOf(
                        "old" to listOf("old-golden-apples"),
                        "new" to listOf("old-potion-effects")
                    ),
                    worldModesets = listOf("old", "new")
                )

                setModeset(player, "old")
                module.isEnabled(player).shouldBeTrue()

                setModeset(player, "new")
                module.isEnabled(player).shouldBeTrue()
            }
        }
    }

    test("disabled modules never apply") {
        withConfig {
            runSync {
                applyConfig(
                    always = completeAlways(
                        always = emptyList(),
                        disabled = listOf("disable-offhand"),
                        modesets = mapOf(
                            "old" to listOf("old-golden-apples"),
                            "new" to listOf("old-potion-effects")
                        )
                    ),
                    disabled = listOf("disable-offhand"),
                    modesets = mapOf(
                        "old" to listOf("old-golden-apples"),
                        "new" to listOf("old-potion-effects")
                    ),
                    worldModesets = listOf("old", "new")
                )

                setModeset(player, "old")
                module.isEnabled(player).shouldBeFalse()

                setModeset(player, "new")
                module.isEnabled(player).shouldBeFalse()
            }
        }
    }

    test("modeset membership controls module activation") {
        withConfig {
            runSync {
                applyConfig(
                    always = completeAlways(
                        always = emptyList(),
                        disabled = emptyList(),
                        modesets = mapOf(
                            "old" to listOf("disable-offhand"),
                            "new" to listOf("old-potion-effects")
                        )
                    ),
                    disabled = emptyList(),
                    modesets = mapOf(
                        "old" to listOf("disable-offhand"),
                        "new" to listOf("old-potion-effects")
                    ),
                    worldModesets = listOf("old", "new")
                )

                setModeset(player, "old")
                module.isEnabled(player).shouldBeTrue()

                setModeset(player, "new")
                module.isEnabled(player).shouldBeFalse()
            }
        }
    }

    test("modules in disabled and another list fail reload") {
        withConfig {
            runSync {
                shouldThrow<IllegalStateException> {
                    applyConfig(
                        always = completeAlways(
                            always = listOf("disable-offhand"),
                            disabled = listOf("disable-offhand"),
                            modesets = mapOf(
                                "old" to listOf("old-potion-effects"),
                                "new" to listOf("old-golden-apples")
                            )
                        ),
                        disabled = listOf("disable-offhand"),
                        modesets = mapOf(
                            "old" to listOf("old-potion-effects"),
                            "new" to listOf("old-golden-apples")
                        ),
                        worldModesets = listOf("old", "new")
                    )
                }
            }
        }
    }

    test("modules missing from all lists fail reload") {
        withConfig {
            runSync {
                val moduleNames = (ModuleLoader.getModules()
                    .map { it.configName }
                    .filterNot { internalModules.contains(it) } + optionalModules)
                    .distinct()
                    .sorted()
                val missing = moduleNames.firstOrNull() ?: error("No modules registered")
                val always = moduleNames.filterNot { it == missing }

                shouldThrow<IllegalStateException> {
                    applyConfig(
                        always = always,
                        disabled = emptyList(),
                        modesets = mapOf("old" to emptyList()),
                        worldModesets = listOf("old")
                    )
                }
            }
        }
    }
})
