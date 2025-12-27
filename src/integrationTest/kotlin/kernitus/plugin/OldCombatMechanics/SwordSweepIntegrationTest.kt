/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics

import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kernitus.plugin.OldCombatMechanics.module.ModuleSwordSweep
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.Callable

@OptIn(ExperimentalKotest::class)
class SwordSweepIntegrationTest : FunSpec({
    val testPlugin = JavaPlugin.getPlugin(OCMTestMain::class.java)
    val module = ModuleLoader.getModules()
        .filterIsInstance<ModuleSwordSweep>()
        .firstOrNull() ?: error("ModuleSwordSweep not registered")

    lateinit var attacker: Player
    lateinit var victim: Player
    lateinit var fakeAttacker: FakePlayer
    lateinit var fakeVictim: FakePlayer

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
        val playerData = kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage.getPlayerData(player.uniqueId)
        playerData.setModesetForWorld(player.world.uid, modeset)
        kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage.setPlayerData(player.uniqueId, playerData)
    }

    extensions(MainThreadDispatcherExtension(testPlugin))

    beforeSpec {
        runSync {
            val world = Bukkit.getServer().getWorld("world")
            val attackerLocation = Location(world, 0.0, 100.0, 0.0)
            val victimLocation = Location(world, 1.0, 100.0, 0.0)

            fakeAttacker = FakePlayer(testPlugin)
            fakeVictim = FakePlayer(testPlugin)
            fakeAttacker.spawn(attackerLocation)
            fakeVictim.spawn(victimLocation)

            attacker = checkNotNull(Bukkit.getPlayer(fakeAttacker.uuid))
            victim = checkNotNull(Bukkit.getPlayer(fakeVictim.uuid))
            attacker.inventory.setItemInMainHand(ItemStack(Material.DIAMOND_SWORD))
            setModeset(attacker, "old")
            setModeset(victim, "old")
            module.reload()
        }
    }

    afterSpec {
        runSync {
            fakeAttacker.removePlayer()
            fakeVictim.removePlayer()
        }
    }

    beforeTest {
        runSync {
            attacker.inventory.setItemInMainHand(ItemStack(Material.DIAMOND_SWORD))
            setModeset(attacker, "old")
            setModeset(victim, "old")
            module.reload()
        }
    }

    context("Sweep attack cancellation") {
        test("sweep attack is cancelled when enabled") {
            val event = EntityDamageByEntityEvent(
                attacker,
                victim,
                EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK,
                1.0
            )
            Bukkit.getPluginManager().callEvent(event)
            event.isCancelled shouldBe true
        }

        test("non-sweep attack is not cancelled") {
            val event = EntityDamageByEntityEvent(
                attacker,
                victim,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                1.0
            )
            Bukkit.getPluginManager().callEvent(event)
            event.isCancelled shouldBe false
        }

        test("disabled module does not cancel sweep") {
            setModeset(attacker, "new")
            val event = EntityDamageByEntityEvent(
                attacker,
                victim,
                EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK,
                1.0
            )
            module.onEntityDamaged(event)
            event.isCancelled shouldBe false
        }
    }
})
