/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics

import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kernitus.plugin.OldCombatMechanics.module.ModuleSwordBlocking
import kotlinx.coroutines.delay
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.util.Optional
import java.util.concurrent.Callable

@OptIn(ExperimentalKotest::class)
class ConsumableComponentIntegrationTest :
    FunSpec({
        val testPlugin = JavaPlugin.getPlugin(OCMTestMain::class.java)
        extensions(MainThreadDispatcherExtension(testPlugin))

        fun <T> runSync(action: () -> T): T =
            if (Bukkit.isPrimaryThread()) {
                action()
            } else {
                Bukkit
                    .getScheduler()
                    .callSyncMethod(testPlugin, Callable { action() })
                    .get()
            }

        suspend fun delayTicks(ticks: Long) {
            delay(ticks * 50L)
        }

        fun paperDataComponentApiPresent(): Boolean =
            try {
                Class.forName("io.papermc.paper.datacomponent.DataComponentTypes")
                true
            } catch (_: Throwable) {
                false
            }

        fun paperConsumablePathAvailable(): Boolean {
            if (!paperDataComponentApiPresent()) return false
            val module = ModuleLoader.getModules().filterIsInstance<ModuleSwordBlocking>().firstOrNull() ?: return false
            return try {
                val supportedField = ModuleSwordBlocking::class.java.getDeclaredField("paperSupported")
                supportedField.isAccessible = true
                val adapterField = ModuleSwordBlocking::class.java.getDeclaredField("paperAdapter")
                adapterField.isAccessible = true
                supportedField.getBoolean(module) && adapterField.get(module) != null
            } catch (_: Throwable) {
                false
            }
        }

        fun nmsItemStack(stack: ItemStack?): Any? {
            if (stack == null) return null
            return try {
                val craftItemStack = Class.forName("org.bukkit.craftbukkit.inventory.CraftItemStack")
                val asNmsCopy = craftItemStack.getMethod("asNMSCopy", ItemStack::class.java)
                asNmsCopy.invoke(null, stack)
            } catch (t: Throwable) {
                throw IllegalStateException(
                    "Failed to obtain NMS ItemStack (${t::class.java.simpleName}: ${t.message})",
                    t,
                )
            }
        }

        fun consumablePatchEntry(stack: ItemStack?): Optional<*>? {
            val nmsStack = nmsItemStack(stack) ?: return null
            return try {
                val patch = nmsStack.javaClass.getMethod("getComponentsPatch").invoke(nmsStack) ?: return null
                val dataComponentType = Class.forName("net.minecraft.core.component.DataComponentType")
                val dataComponents = Class.forName("net.minecraft.core.component.DataComponents")
                val consumableType = dataComponents.getField("CONSUMABLE").get(null)
                val getMethod = patch.javaClass.getMethod("get", dataComponentType)
                getMethod.invoke(patch, consumableType) as? Optional<*>
            } catch (t: Throwable) {
                throw IllegalStateException(
                    "Failed to inspect data component patch (${t::class.java.simpleName}: ${t.message})",
                    t,
                )
            }
        }

        fun hasConsumableRemoval(stack: ItemStack?): Boolean {
            val entry = consumablePatchEntry(stack) ?: return false
            return !entry.isPresent
        }

        fun assertNoConsumableRemoval(
            stack: ItemStack?,
            label: String,
        ) {
            val entry = consumablePatchEntry(stack)
            if (entry != null && !entry.isPresent) {
                error("$label gained !minecraft:consumable")
            }
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
                player.inventory.clear()
                player.inventory.setItemInOffHand(ItemStack(Material.AIR))
                player.updateInventory()
            }
        }

        afterSpec {
            runSync {
                fake.removePlayer()
            }
        }

        beforeTest {
            runSync {
                player.inventory.clear()
                player.inventory.setItemInOffHand(ItemStack(Material.AIR))
                player.setItemOnCursor(ItemStack(Material.AIR))
                player.inventory.heldItemSlot = 0
                player.updateInventory()
            }
        }

        test("hotbar swap keeps food consumable") {
            if (!paperConsumablePathAvailable()) {
                println("Skipping: Paper consumable component path unavailable")
                return@test
            }

            runSync {
                player.inventory.setItem(0, ItemStack(Material.BREAD))
                player.inventory.setItem(1, ItemStack(Material.STONE))
                player.inventory.heldItemSlot = 0
            }

            val before = runSync { player.inventory.getItem(0) }
            assertNoConsumableRemoval(before, "hotbar food (before)")

            runSync {
                Bukkit.getPluginManager().callEvent(PlayerItemHeldEvent(player, 0, 1))
            }

            val after = runSync { player.inventory.getItem(0) }
            hasConsumableRemoval(after) shouldBe false
        }

        test("inventory click keeps slot and cursor food consumable") {
            if (!paperConsumablePathAvailable()) {
                println("Skipping: Paper consumable component path unavailable")
                return@test
            }

            val view =
                runSync { player.openInventory(player.inventory) }
                    ?: error("inventory view missing")
            try {
                runSync {
                    player.inventory.setItem(0, ItemStack(Material.BREAD))
                    player.setItemOnCursor(ItemStack(Material.CARROT))
                }

                val slotItem = runSync { player.inventory.getItem(0) }
                val cursorItem = runSync { player.itemOnCursor }
                assertNoConsumableRemoval(slotItem, "slot food (before)")
                assertNoConsumableRemoval(cursorItem, "cursor food (before)")

                val event =
                    runSync {
                        val click =
                            InventoryClickEvent(
                                view,
                                InventoryType.SlotType.CONTAINER,
                                0,
                                ClickType.LEFT,
                                InventoryAction.PICKUP_ALL,
                            )
                        click.currentItem = slotItem
                        click.cursor = cursorItem
                        click
                    }

                runSync { Bukkit.getPluginManager().callEvent(event) }
                delayTicks(1)

                val afterSlot = runSync { player.inventory.getItem(0) }
                val afterCursor = runSync { player.itemOnCursor }
                hasConsumableRemoval(afterSlot) shouldBe false
                hasConsumableRemoval(afterCursor) shouldBe false
            } finally {
                runSync { player.closeInventory() }
            }
        }

        test("number-key hotbar swap keeps food consumable") {
            if (!paperConsumablePathAvailable()) {
                println("Skipping: Paper consumable component path unavailable")
                return@test
            }

            val view =
                runSync { player.openInventory(player.inventory) }
                    ?: error("inventory view missing")
            try {
                runSync {
                    player.inventory.setItem(0, ItemStack(Material.STONE))
                    player.inventory.setItem(2, ItemStack(Material.BREAD))
                }

                val hotbarItem = runSync { player.inventory.getItem(2) }
                assertNoConsumableRemoval(hotbarItem, "hotbar button food (before)")

                val event =
                    runSync {
                        val click =
                            InventoryClickEvent(
                                view,
                                InventoryType.SlotType.CONTAINER,
                                0,
                                ClickType.NUMBER_KEY,
                                InventoryAction.HOTBAR_SWAP,
                                2,
                            )
                        click.currentItem = player.inventory.getItem(0)
                        click
                    }

                runSync { Bukkit.getPluginManager().callEvent(event) }
                delayTicks(1)

                val after = runSync { player.inventory.getItem(2) }
                hasConsumableRemoval(after) shouldBe false
            } finally {
                runSync { player.closeInventory() }
            }
        }

        test("swap hand keeps food consumable") {
            if (!paperConsumablePathAvailable()) {
                println("Skipping: Paper consumable component path unavailable")
                return@test
            }

            runSync {
                player.inventory.setItemInMainHand(ItemStack(Material.STONE))
                player.inventory.setItemInOffHand(ItemStack(Material.BREAD))
            }

            val offhandBefore = runSync { player.inventory.itemInOffHand }
            assertNoConsumableRemoval(offhandBefore, "offhand food (before)")

            val event =
                runSync {
                    PlayerSwapHandItemsEvent(player, player.inventory.itemInMainHand, player.inventory.itemInOffHand)
                }
            runSync { Bukkit.getPluginManager().callEvent(event) }
            runSync {
                val main = player.inventory.itemInMainHand
                val off = player.inventory.itemInOffHand
                player.inventory.setItemInMainHand(off)
                player.inventory.setItemInOffHand(main)
            }
            delayTicks(1)

            val mainAfter = runSync { player.inventory.itemInMainHand }
            val offAfter = runSync { player.inventory.itemInOffHand }
            val foodAfter = if (mainAfter.type == Material.BREAD) mainAfter else offAfter
            hasConsumableRemoval(foodAfter) shouldBe false
        }

        test("dropping food keeps consumable component") {
            if (!paperConsumablePathAvailable()) {
                println("Skipping: Paper consumable component path unavailable")
                return@test
            }

            val drop =
                runSync {
                    player.world.dropItem(player.location, ItemStack(Material.BREAD))
                }
            try {
                val before = runSync { drop.itemStack }
                assertNoConsumableRemoval(before, "dropped food (before)")

                runSync { Bukkit.getPluginManager().callEvent(PlayerDropItemEvent(player, drop)) }

                val after = runSync { drop.itemStack }
                hasConsumableRemoval(after) shouldBe false
            } finally {
                runSync { drop.remove() }
            }
        }

        test("world change keeps hand food consumable") {
            if (!paperConsumablePathAvailable()) {
                println("Skipping: Paper consumable component path unavailable")
                return@test
            }

            runSync {
                player.inventory.setItemInMainHand(ItemStack(Material.BREAD))
                player.inventory.setItemInOffHand(ItemStack(Material.CARROT))
            }

            val beforeMain = runSync { player.inventory.itemInMainHand }
            val beforeOff = runSync { player.inventory.itemInOffHand }
            assertNoConsumableRemoval(beforeMain, "main hand food (before world change)")
            assertNoConsumableRemoval(beforeOff, "offhand food (before world change)")

            runSync { Bukkit.getPluginManager().callEvent(PlayerChangedWorldEvent(player, player.world)) }

            val afterMain = runSync { player.inventory.itemInMainHand }
            val afterOff = runSync { player.inventory.itemInOffHand }
            hasConsumableRemoval(afterMain) shouldBe false
            hasConsumableRemoval(afterOff) shouldBe false
        }

        test("quit event keeps hand food consumable") {
            if (!paperConsumablePathAvailable()) {
                println("Skipping: Paper consumable component path unavailable")
                return@test
            }

            runSync {
                player.inventory.setItemInMainHand(ItemStack(Material.BREAD))
                player.inventory.setItemInOffHand(ItemStack(Material.CARROT))
            }

            val beforeMain = runSync { player.inventory.itemInMainHand }
            val beforeOff = runSync { player.inventory.itemInOffHand }
            assertNoConsumableRemoval(beforeMain, "main hand food (before quit)")
            assertNoConsumableRemoval(beforeOff, "offhand food (before quit)")

            runSync { Bukkit.getPluginManager().callEvent(PlayerQuitEvent(player, "test")) }

            val afterMain = runSync { player.inventory.itemInMainHand }
            val afterOff = runSync { player.inventory.itemInOffHand }
            hasConsumableRemoval(afterMain) shouldBe false
            hasConsumableRemoval(afterOff) shouldBe false
        }
    })
