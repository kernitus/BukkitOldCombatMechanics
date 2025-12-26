/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics

import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.concurrency.TestExecutionMode
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.block.Action
import org.bukkit.block.BlockFace
import org.bukkit.inventory.EquipmentSlot
import io.kotest.matchers.shouldBe

@OptIn(ExperimentalKotest::class)
class SwordBlockingIntegrationTest : StringSpec({
    val plugin = JavaPlugin.getPlugin(OCMTestMain::class.java)
    extension(MainThreadDispatcherExtension(plugin))
    testExecutionMode = TestExecutionMode.Sequential

    lateinit var player: Player
    lateinit var fakePlayer: FakePlayer

    fun preparePlayer() {
        println("Preparing player")
        val world = Bukkit.getServer().getWorld("world")
        val location = Location(world, 0.0, 100.0, 0.0)

        fakePlayer = FakePlayer(plugin)
        fakePlayer.spawn(location)

        player = checkNotNull(Bukkit.getPlayer(fakePlayer.uuid))
    }

    beforeSpec {
        Bukkit.getScheduler().runTask(plugin, Runnable {
            plugin.logger.info("Running before all")
            preparePlayer()

            player.gameMode = GameMode.SURVIVAL
            player.maximumNoDamageTicks = 20
            player.noDamageTicks = 0 // remove spawn invulnerability
            player.isInvulnerable = false
        })
    }

    afterSpec {
        plugin.logger.info("Running after all")
        Bukkit.getScheduler().runTask(plugin, Runnable {
            fakePlayer.removePlayer()
        })
    }

    "test sword blocking" {
        player.inventory.setItemInMainHand(ItemStack(Material.DIAMOND_SWORD))

        // Simulate right-clicking
        val event = PlayerInteractEvent(
            player,
            Action.RIGHT_CLICK_AIR,
            player.inventory.itemInMainHand,
            null,
            BlockFace.SELF,
            EquipmentSlot.HAND
        )
        Bukkit.getPluginManager().callEvent(event)

        // Check if player is blocking and has a shield in off-hand
        player.isBlocking shouldBe true
        player.inventory.itemInOffHand.type shouldBe Material.SHIELD
    }
})
