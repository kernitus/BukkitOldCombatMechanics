/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics

import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kernitus.plugin.OldCombatMechanics.module.ModuleDisableOffHand
import kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage.getPlayerData
import kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage.setPlayerData
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.Callable

@OptIn(ExperimentalKotest::class)
class DisableOffhandIntegrationTest : FunSpec({
    val testPlugin = JavaPlugin.getPlugin(OCMTestMain::class.java)
    val module = ModuleLoader.getModules()
        .filterIsInstance<ModuleDisableOffHand>()
        .firstOrNull() ?: error("ModuleDisableOffHand not registered")

    lateinit var player: Player
    lateinit var fakePlayer: FakePlayer

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
            fakePlayer.removePlayer()
        }
    }

    beforeTest {
        runSync {
            player.inventory.clear()
            player.inventory.setItemInOffHand(ItemStack(Material.SHIELD))
            setModeset(player, "new")
        }
    }

    test("modeset-change handler ignores players without disable-offhand enabled") {
        runSync {
            module.isEnabled(player) shouldBe false
            val offhand = player.inventory.itemInOffHand.clone()

            module.onModesetChange(player)

            player.inventory.itemInOffHand.type shouldBe offhand.type
            player.inventory.itemInOffHand.amount shouldBe offhand.amount
        }
    }
})
