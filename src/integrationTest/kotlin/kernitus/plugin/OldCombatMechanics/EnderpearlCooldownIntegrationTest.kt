/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics

import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kernitus.plugin.OldCombatMechanics.module.ModuleDisableEnderpearlCooldown
import kotlinx.coroutines.delay
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.EnderPearl
import org.bukkit.entity.Player
import org.bukkit.event.entity.ProjectileLaunchEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.Callable

@OptIn(ExperimentalKotest::class)
class EnderpearlCooldownIntegrationTest : FunSpec({
    val testPlugin = JavaPlugin.getPlugin(OCMTestMain::class.java)
    val ocm = JavaPlugin.getPlugin(OCMMain::class.java)
    val module = ModuleLoader.getModules().filterIsInstance<ModuleDisableEnderpearlCooldown>().firstOrNull()
        ?: error("ModuleDisableEnderpearlCooldown not registered")

    lateinit var player: Player
    lateinit var fakePlayer: FakePlayer

    fun <T> runSync(action: () -> T): T {
        return if (Bukkit.isPrimaryThread()) action() else Bukkit.getScheduler().callSyncMethod(testPlugin, Callable {
            action()
        }).get()
    }

    fun setModeset(player: Player, modeset: String) {
        val playerData = kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage.getPlayerData(player.uniqueId)
        playerData.setModesetForWorld(player.world.uid, modeset)
        kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage.setPlayerData(player.uniqueId, playerData)
        ModuleLoader.toggleModules()
    }

    suspend fun withConfig(cooldownSeconds: Int, showMessage: Boolean, block: suspend () -> Unit) {
        val oldCooldown = ocm.config.getInt("disable-enderpearl-cooldown.cooldown")
        val oldShow = ocm.config.getBoolean("disable-enderpearl-cooldown.showMessage")
        try {
            runSync {
                ocm.config.set("disable-enderpearl-cooldown.cooldown", cooldownSeconds)
                ocm.config.set("disable-enderpearl-cooldown.showMessage", showMessage)
                module.reload()
                ModuleLoader.toggleModules()
            }
            block()
        } finally {
            runSync {
                ocm.config.set("disable-enderpearl-cooldown.cooldown", oldCooldown)
                ocm.config.set("disable-enderpearl-cooldown.showMessage", oldShow)
                module.reload()
                ModuleLoader.toggleModules()
            }
        }
    }

    fun firePearlLaunchEvent(player: Player): ProjectileLaunchEvent {
        // Spawn a pearl entity directly; the module cancels this and launches a replacement.
        val pearl = runSync {
            val world = player.world
            val entity = world.spawn(player.eyeLocation, EnderPearl::class.java)
            entity.shooter = player
            entity
        }
        val event = ProjectileLaunchEvent(pearl)
        runSync { Bukkit.getPluginManager().callEvent(event) }
        return event
    }

    extensions(MainThreadDispatcherExtension(testPlugin))

    beforeSpec {
        runSync {
            val world = Bukkit.getWorld("world") ?: error("world not loaded")
            val location = Location(world, 0.0, 120.0, 0.0, 0f, 0f)
            fakePlayer = FakePlayer(testPlugin)
            fakePlayer.spawn(location)
            player = Bukkit.getPlayer(fakePlayer.uuid) ?: error("Player not found")
            player.isOp = true
            setModeset(player, "old")
        }
    }

    afterSpec {
        runSync { fakePlayer.removePlayer() }
    }

    beforeTest {
        runSync {
            setModeset(player, "old")
            player.gameMode = GameMode.SURVIVAL
            player.inventory.clear()
            player.inventory.setItemInMainHand(ItemStack(Material.ENDER_PEARL, 16))
        }
    }

    test("cooldown 0 allows repeated throws and consumes an enderpearl in survival") {
        withConfig(cooldownSeconds = 0, showMessage = false) {
            val before = runSync { player.inventory.itemInMainHand.amount }
            val e1 = firePearlLaunchEvent(player)
            e1.isCancelled shouldBe true
            val after1 = runSync { player.inventory.itemInMainHand.amount }
            (before - after1) shouldBe 1

            val e2 = firePearlLaunchEvent(player)
            e2.isCancelled shouldBe true
            val after2 = runSync { player.inventory.itemInMainHand.amount }
            (after1 - after2) shouldBe 1
        }
    }

    test("cooldown blocks a second throw within the window and exposes remaining cooldown") {
        withConfig(cooldownSeconds = 5, showMessage = false) {
            val e1 = firePearlLaunchEvent(player)
            e1.isCancelled shouldBe true

            val remainingAfterFirst = runSync { module.getEnderpearlCooldown(player.uniqueId) }
            remainingAfterFirst shouldBeGreaterThan 0

            val before2 = runSync { player.inventory.itemInMainHand.amount }
            val e2 = firePearlLaunchEvent(player)
            e2.isCancelled shouldBe true

            // Second throw attempt should not consume another pearl.
            val after2 = runSync { player.inventory.itemInMainHand.amount }
            before2 shouldBe after2
        }
    }

    test("creative mode does not consume enderpearls") {
        withConfig(cooldownSeconds = 0, showMessage = false) {
            runSync { player.gameMode = GameMode.CREATIVE }
            val before = runSync { player.inventory.itemInMainHand.amount }
            val e1 = firePearlLaunchEvent(player)
            e1.isCancelled shouldBe true
            val after = runSync { player.inventory.itemInMainHand.amount }
            before shouldBe after
        }
    }

    test("no enderpearl item in either hand does not throw or consume") {
        withConfig(cooldownSeconds = 0, showMessage = false) {
            runSync { player.inventory.clear() }
            val e1 = firePearlLaunchEvent(player)
            e1.isCancelled shouldBe true
            runSync { player.inventory.itemInMainHand.type shouldBe Material.AIR }
        }
    }

    test("cooldown expires after real time and throw becomes allowed again") {
        withConfig(cooldownSeconds = 1, showMessage = false) {
            val e1 = firePearlLaunchEvent(player)
            e1.isCancelled shouldBe true

            // Wait slightly over a second to ensure wall-clock cooldown is over.
            delay(1200)

            val before2 = runSync { player.inventory.itemInMainHand.amount }
            val e2 = firePearlLaunchEvent(player)
            e2.isCancelled shouldBe true
            val after2 = runSync { player.inventory.itemInMainHand.amount }
            (before2 - after2) shouldBe 1
        }
    }
})
