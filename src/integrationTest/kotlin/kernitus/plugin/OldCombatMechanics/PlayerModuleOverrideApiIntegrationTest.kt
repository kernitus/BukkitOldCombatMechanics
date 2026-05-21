/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics

import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import kernitus.plugin.OldCombatMechanics.api.OldCombatMechanicsAPI
import kernitus.plugin.OldCombatMechanics.api.PlayerModuleOverride
import kernitus.plugin.OldCombatMechanics.module.ModuleDisableOffHand
import kernitus.plugin.OldCombatMechanics.module.OCMModule
import kernitus.plugin.OldCombatMechanics.utilities.Config
import kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage.getPlayerData
import kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage.setPlayerData
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin

@OptIn(ExperimentalKotest::class)
class PlayerModuleOverrideApiIntegrationTest : FunSpec({
    val testPlugin = JavaPlugin.getPlugin(OCMTestMain::class.java)
    val api = Bukkit.getServicesManager().getRegistration(OldCombatMechanicsAPI::class.java)?.provider
        ?: error("OldCombatMechanicsApi service not registered")
    val module = ModuleLoader.getModules()
        .filterIsInstance<ModuleDisableOffHand>()
        .firstOrNull() ?: error("ModuleDisableOffHand not registered")
    val alwaysEnabledModule = moduleByName("attack-frequency")
    val modesetModule = moduleByName("old-tool-damage")

    lateinit var player: Player
    lateinit var fakePlayer: FakePlayer

    fun runSync(action: () -> Unit) {
        if (Bukkit.isPrimaryThread()) {
            action()
        } else {
            Bukkit.getScheduler().callSyncMethod(testPlugin) {
                action()
                null
            }.get()
        }
    }

    fun setModeset(player: Player, modeset: String) {
        val playerData = getPlayerData(player.uniqueId)
        playerData.setModesetForWorld(player.world.uid, modeset)
        setPlayerData(player.uniqueId, playerData)
    }

    extensions(MainThreadDispatcherExtension(testPlugin))

    beforeSpec {
        runSync {
            val world = checkNotNull(Bukkit.getServer().getWorld("world"))
            fakePlayer = FakePlayer(testPlugin)
            fakePlayer.spawn(Location(world, 0.0, 100.0, 0.0))
            player = checkNotNull(Bukkit.getPlayer(fakePlayer.uuid))
        }
    }

    beforeTest {
        runSync {
            api.clearAllModuleOverridesForPlayer(player)
            setModeset(player, "old")
        }
    }

    afterTest {
        runSync {
            api.clearAllModuleOverridesForPlayer(player)
            setModeset(player, "old")
        }
    }

    afterSpec {
        runSync {
            api.clearAllModuleOverridesForPlayer(player)
            fakePlayer.removePlayer()
        }
    }

    test("API can force module enable and disable per player") {
        runSync {
            val moduleName = module.configName
            val disabledModeset = Config.getModesets().entries.firstOrNull { (_, modules) ->
                !modules.contains(moduleName)
            }?.key
            if (disabledModeset != null) {
                setModeset(player, disabledModeset)
            }

            val baseline = module.isEnabled(player)

            api.forceEnableModuleForPlayer(player, moduleName)
            module.isEnabled(player) shouldBe true

            api.forceDisableModuleForPlayer(player, moduleName)
            module.isEnabled(player) shouldBe false

            api.clearModuleOverrideForPlayer(player, moduleName)
            module.isEnabled(player) shouldBe baseline
        }
    }

    test("unknown and internal module names are rejected") {
        runSync {
            shouldThrow<IllegalArgumentException> {
                api.forceEnableModuleForPlayer(player, "not-a-real-module")
            }
            shouldThrow<IllegalArgumentException> {
                api.forceDisableModuleForPlayer(player, "modeset-listener")
            }
            shouldThrow<IllegalArgumentException> {
                api.getModuleOverrideForPlayer(player, "attack-cooldown-tracker")
            }

            api.hasAnyOverrideForPlayer(player) shouldBe false
        }
    }

    test("bulk override setter rejects invalid keys before mutating overrides") {
        runSync {
            val moduleName = module.configName
            val alwaysEnabledModuleName = alwaysEnabledModule.configName
            api.forceEnableModuleForPlayer(player, moduleName)

            shouldThrow<IllegalArgumentException> {
                api.setModuleOverridesForPlayer(
                    player,
                    mapOf(
                        moduleName to PlayerModuleOverride.FORCE_DISABLED,
                        alwaysEnabledModuleName to PlayerModuleOverride.FORCE_DISABLED,
                        "missing-module" to PlayerModuleOverride.FORCE_ENABLED,
                    ),
                )
            }

            api.getModuleOverridesForPlayer(player) shouldBe mapOf(moduleName to PlayerModuleOverride.FORCE_ENABLED)
            api.getModuleOverrideForPlayer(player, alwaysEnabledModuleName) shouldBe PlayerModuleOverride.DEFAULT
        }
    }

    test("Java null bulk override value is rejected before mutating overrides") {
        runSync {
            val moduleName = module.configName
            api.forceEnableModuleForPlayer(player, moduleName)
            api.getModuleOverrideForPlayer(player, moduleName) shouldBe PlayerModuleOverride.FORCE_ENABLED

            shouldThrow<IllegalArgumentException> {
                callJavaNullBulkOverrideValue(api, player, moduleName)
            }

            api.getModuleOverrideForPlayer(player, moduleName) shouldBe PlayerModuleOverride.FORCE_ENABLED
            api.clearModuleOverrideForPlayer(player, moduleName)
        }
    }

    test("force enable overrides config-disabled and modeset-disabled modules") {
        runSync {
            val configDisabledModuleName = module.configName
            module.isEnabled(player) shouldBe false
            api.isModuleEnabledForPlayer(player, configDisabledModuleName) shouldBe false

            api.forceEnableModuleForPlayer(player, configDisabledModuleName)

            module.isEnabled(player) shouldBe true
            api.isModuleEnabledForPlayer(player, configDisabledModuleName) shouldBe true

            val modesetModuleName = modesetModule.configName
            setModeset(player, "new")
            modesetModule.isEnabled(player) shouldBe false
            api.isModuleEnabledForPlayer(player, modesetModuleName) shouldBe false

            api.forceEnableModuleForPlayer(player, modesetModuleName)

            modesetModule.isEnabled(player) shouldBe true
            api.isModuleEnabledForPlayer(player, modesetModuleName) shouldBe true
        }
    }

    test("force disable overrides config-enabled modules") {
        runSync {
            val moduleName = alwaysEnabledModule.configName
            alwaysEnabledModule.isEnabled(player) shouldBe true
            api.isModuleEnabledForPlayer(player, moduleName) shouldBe true

            api.forceDisableModuleForPlayer(player, moduleName)

            alwaysEnabledModule.isEnabled(player) shouldBe false
            api.isModuleEnabledForPlayer(player, moduleName) shouldBe false
        }
    }

    test("clearing overrides returns to configured and modeset behaviour") {
        runSync {
            val configDisabledModuleName = module.configName
            api.forceEnableModuleForPlayer(player, configDisabledModuleName)
            api.clearModuleOverrideForPlayer(player, configDisabledModuleName)
            api.getModuleOverrideForPlayer(player, configDisabledModuleName) shouldBe PlayerModuleOverride.DEFAULT
            module.isEnabled(player) shouldBe false

            val alwaysEnabledModuleName = alwaysEnabledModule.configName
            api.forceDisableModuleForPlayer(player, alwaysEnabledModuleName)
            api.clearModuleOverrideForPlayer(player, alwaysEnabledModuleName)
            api.getModuleOverrideForPlayer(player, alwaysEnabledModuleName) shouldBe PlayerModuleOverride.DEFAULT
            alwaysEnabledModule.isEnabled(player) shouldBe true

            val modesetModuleName = modesetModule.configName
            setModeset(player, "new")
            api.forceEnableModuleForPlayer(player, modesetModuleName)
            api.clearModuleOverrideForPlayer(player, modesetModuleName)
            modesetModule.isEnabled(player) shouldBe false

            setModeset(player, "old")
            modesetModule.isEnabled(player) shouldBe true
        }
    }

    test("override query methods expose only active configurable overrides") {
        runSync {
            val configDisabledModuleName = module.configName
            val alwaysEnabledModuleName = alwaysEnabledModule.configName
            val moduleNames = api.getModuleNames()

            moduleNames shouldContain configDisabledModuleName
            moduleNames shouldContain alwaysEnabledModuleName
            moduleNames shouldContain modesetModule.configName
            moduleNames shouldNotContain "modeset-listener"
            moduleNames shouldNotContain "attack-cooldown-tracker"
            moduleNames shouldNotContain "entity-damage-listener"

            api.hasAnyOverrideForPlayer(player) shouldBe false
            api.setModuleOverridesForPlayer(
                player,
                mapOf(
                    configDisabledModuleName to PlayerModuleOverride.FORCE_ENABLED,
                    alwaysEnabledModuleName to PlayerModuleOverride.FORCE_DISABLED,
                ),
            )

            api.hasAnyOverrideForPlayer(player) shouldBe true
            api.getModuleOverridesForPlayer(player) shouldBe mapOf(
                configDisabledModuleName to PlayerModuleOverride.FORCE_ENABLED,
                alwaysEnabledModuleName to PlayerModuleOverride.FORCE_DISABLED,
            )

            api.setModuleOverridesForPlayer(
                player,
                mapOf(configDisabledModuleName to PlayerModuleOverride.DEFAULT),
            )

            api.getModuleOverridesForPlayer(player) shouldBe mapOf(
                alwaysEnabledModuleName to PlayerModuleOverride.FORCE_DISABLED,
            )
            api.clearAllModuleOverridesForPlayer(player)
            api.hasAnyOverrideForPlayer(player) shouldBe false
        }
    }

    test("player quit lifecycle clears override state") {
        runSync {
            val moduleName = module.configName
            api.forceEnableModuleForPlayer(player, moduleName)
            api.hasAnyOverrideForPlayer(player) shouldBe true

            Bukkit.getPluginManager().callEvent(PlayerQuitEvent(player, ""))

            api.hasAnyOverrideForPlayer(player) shouldBe false
            api.getModuleOverrideForPlayer(player, moduleName) shouldBe PlayerModuleOverride.DEFAULT
        }
    }
})

private fun moduleByName(moduleName: String): OCMModule =
    ModuleLoader.getModules()
        .firstOrNull { it.configName == moduleName }
        ?: error("$moduleName module not registered")

private fun callJavaNullBulkOverrideValue(api: OldCombatMechanicsAPI, player: Player, moduleName: String) {
    val helper = Class.forName("kernitus.plugin.OldCombatMechanics.PlayerModuleOverrideJavaInterop")
    val method = helper.getDeclaredMethod(
        "setOverridesWithNullValue",
        OldCombatMechanicsAPI::class.java,
        Player::class.java,
        String::class.java,
    )
    method.isAccessible = true
    try {
        method.invoke(null, api, player, moduleName)
    } catch (exception: java.lang.reflect.InvocationTargetException) {
        throw exception.cause ?: exception
    }
}
