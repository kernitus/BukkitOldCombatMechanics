/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics

import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kernitus.plugin.OldCombatMechanics.api.OldCombatMechanicsAPI
import kernitus.plugin.OldCombatMechanics.module.ModuleDisableOffHand
import kernitus.plugin.OldCombatMechanics.utilities.Config
import kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage.getPlayerData
import kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage.setPlayerData
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

@OptIn(ExperimentalKotest::class)
class PlayerModuleOverrideApiIntegrationTest : FunSpec({
    val testPlugin = JavaPlugin.getPlugin(OCMTestMain::class.java)
    val api = Bukkit.getServicesManager().getRegistration(OldCombatMechanicsAPI::class.java)?.provider
        ?: error("OldCombatMechanicsApi service not registered")
    val module = ModuleLoader.getModules()
        .filterIsInstance<ModuleDisableOffHand>()
        .firstOrNull() ?: error("ModuleDisableOffHand not registered")

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
})
