/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics

import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryView
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.Callable

@OptIn(ExperimentalKotest::class)
class DisableOffhandReflectionIntegrationTest :
    FunSpec({
        val testPlugin = JavaPlugin.getPlugin(OCMTestMain::class.java)
        extensions(MainThreadDispatcherExtension(testPlugin))

        fun <T> runSync(action: () -> T): T =
            if (Bukkit.isPrimaryThread()) {
                action()
            } else {
                Bukkit.getScheduler().callSyncMethod(testPlugin, Callable { action() }).get()
            }

        lateinit var fake: FakePlayer
        lateinit var player: Player

        beforeSpec {
            runSync {
                val world = Bukkit.getWorld("world") ?: error("world missing")
                fake = FakePlayer(testPlugin)
                fake.spawn(Location(world, 0.0, 100.0, 0.0))
                player = Bukkit.getPlayer(fake.uuid) ?: error("player missing")
                player.gameMode = GameMode.SURVIVAL
                player.isInvulnerable = false
            }
        }

        afterSpec {
            runSync {
                fake.removePlayer()
            }
        }

        beforeTest {
            runSync {
                player.closeInventory()
            }
        }

        test("reflective InventoryClickEvent getView works") {
            val view = runSync { player.openInventory(player.inventory) } ?: error("inventory view missing")
            try {
                val event =
                    runSync {
                        InventoryClickEvent(
                            view,
                            InventoryType.SlotType.CONTAINER,
                            0,
                            ClickType.LEFT,
                            InventoryAction.PICKUP_ALL,
                        )
                    }
                val method = Reflector.getMethod(event.javaClass, "getView") ?: error("getView missing")
                val reflectedView = Reflector.invokeMethod<InventoryView>(method, event)
                reflectedView shouldBe view
            } finally {
                runSync { player.closeInventory() }
            }
        }

        test("reflective InventoryView access returns top and bottom inventories") {
            val view = runSync { player.openInventory(player.inventory) } ?: error("inventory view missing")
            try {
                val bottomMethod =
                    Reflector.getMethod(view.javaClass, "getBottomInventory")
                        ?: error("getBottomInventory missing")
                val topMethod =
                    Reflector.getMethod(view.javaClass, "getTopInventory")
                        ?: error("getTopInventory missing")

                val bottom =
                    Reflector.invokeMethod<Inventory>(bottomMethod, view)
                        ?: error("bottom inventory missing")
                val top =
                    Reflector.invokeMethod<Inventory>(topMethod, view)
                        ?: error("top inventory missing")

                bottom.type shouldBe InventoryType.PLAYER
            } finally {
                runSync { player.closeInventory() }
            }
        }
    })
