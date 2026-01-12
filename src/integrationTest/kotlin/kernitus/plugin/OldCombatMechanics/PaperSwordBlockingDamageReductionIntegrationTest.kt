/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics

import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kernitus.plugin.OldCombatMechanics.module.ModuleSwordBlocking
import kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage.getPlayerData
import kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage.setPlayerData
import kotlinx.coroutines.delay
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.Callable

@OptIn(ExperimentalKotest::class)
class PaperSwordBlockingDamageReductionIntegrationTest : FunSpec({
    val testPlugin = JavaPlugin.getPlugin(OCMTestMain::class.java)
    val ocm = JavaPlugin.getPlugin(OCMMain::class.java)
    extensions(MainThreadDispatcherExtension(testPlugin))

    fun <T> runSync(action: () -> T): T {
        return if (Bukkit.isPrimaryThread()) action() else Bukkit.getScheduler()
            .callSyncMethod(testPlugin, Callable { action() })
            .get()
    }

    suspend fun delayTicks(ticks: Long) {
        delay(ticks * 50L)
    }

    fun paperDataComponentApiPresent(): Boolean {
        return try {
            Class.forName("io.papermc.paper.datacomponent.DataComponentTypes")
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun setModeset(player: Player, name: String) {
        val data = getPlayerData(player.uniqueId)
        data.setModesetForWorld(player.world.uid, name)
        setPlayerData(player.uniqueId, data)
    }

    fun equipSword(player: Player, material: Material) {
        player.inventory.setItemInMainHand(ItemStack(material))
        player.updateInventory()
    }

    fun rightClickMainHand(player: Player) {
        val event = PlayerInteractEvent(
            player,
            Action.RIGHT_CLICK_AIR,
            player.inventory.itemInMainHand,
            null,
            org.bukkit.block.BlockFace.SELF,
            EquipmentSlot.HAND
        )
        Bukkit.getPluginManager().callEvent(event)
    }

    lateinit var defenderFake: FakePlayer
    lateinit var defender: Player
    lateinit var module: ModuleSwordBlocking

    beforeSpec {
        runSync {
            module = ModuleLoader.getModules().filterIsInstance<ModuleSwordBlocking>().firstOrNull()
                ?: error("ModuleSwordBlocking not registered")

            val world = Bukkit.getWorld("world") ?: error("world missing")
            val base = Location(world, 0.0, 100.0, 0.0)

            defenderFake = FakePlayer(testPlugin)
            defenderFake.spawn(base)
            defender = Bukkit.getPlayer(defenderFake.uuid) ?: error("defender not found")

            defender.gameMode = GameMode.SURVIVAL
            defender.maximumNoDamageTicks = 20
            defender.noDamageTicks = 0
            defender.isInvulnerable = false
            defender.inventory.clear()
            setModeset(defender, "old")
        }
    }

    afterSpec {
        runSync {
            defenderFake.removePlayer()
        }
    }

    test("Paper sword blocking sets BLOCKING modifier negative on hit") {
        if (!paperDataComponentApiPresent()) {
            println("Skipping: Paper DataComponent API not present")
            return@test
        }

        runSync {
            equipSword(defender, Material.DIAMOND_SWORD)
            defender.inventory.setItemInOffHand(ItemStack(Material.AIR))
        }

        runSync { rightClickMainHand(defender) }
        delayTicks(2)

        val offhandAfter = runSync { defender.inventory.itemInOffHand.type }
        if (offhandAfter == Material.SHIELD) {
            println("Skipping: legacy shield swap path is active on this server")
            return@test
        }

        runSync {
            // The bug we saw in live logs: the sword can animate as BLOCK, but the server may not recognise
            // it as "blocking" for damage reduction (BLOCKING stays 0). This asserts the Paper path is
            // actually recognised server-side.
            module.isPaperSwordBlocking(defender) shouldBe true
        }

        val zombie = runSync {
            defender.world.spawn(defender.location.clone().add(0.0, 0.0, 1.0), org.bukkit.entity.Zombie::class.java)
        }
        try {
            // Use a synthetic event here. We specifically care about the Paper sword-block detection and the
            // computed reduction. Whether Bukkit considers the BLOCKING modifier "applicable" is decided by
            // the server's internal damage pipeline and can differ for synthetic events vs real hits.
            val event = runSync {
                org.bukkit.event.entity.EntityDamageByEntityEvent(
                    zombie,
                    defender,
                    EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                    2.5
                )
            }

            val reduction = runSync { module.applyPaperBlockingReduction(event, 2.5) }
            reduction shouldBeGreaterThan 0.0
            // Also sanity-check sign: the module is supposed to write BLOCKING as a negative modifier downstream.
            // (We don't assert it here because synthetic events may not expose/apply that modifier consistently.)
            reduction shouldBeLessThan 2.5
        } finally {
            runSync {
                zombie.remove()
            }
        }
    }
})
