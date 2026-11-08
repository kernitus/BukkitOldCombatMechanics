/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics

import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.Enabled
import io.kotest.matchers.shouldBe
import kernitus.plugin.OldCombatMechanics.module.ModuleDisableOffHand
import kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage.getPlayerData
import kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage.setPlayerData
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.Callable

@OptIn(ExperimentalKotest::class)
class DisableOffhandIntegrationTest :
    FunSpec({
        val testPlugin = JavaPlugin.getPlugin(OCMTestMain::class.java)
        val module =
            ModuleLoader
                .getModules()
                .filterIsInstance<ModuleDisableOffHand>()
                .firstOrNull() ?: error("ModuleDisableOffHand not registered")

        val ocm = JavaPlugin.getPlugin(OCMMain::class.java)
        val configPaths = listOf("disable-offhand.whitelist", "disable-offhand.items", "disable-offhand.denied-message")
        var savedConfig = emptyMap<String, Any?>()

        fun cachedSet(name: String): MutableSet<String> {
            val field = kernitus.plugin.OldCombatMechanics.utilities.Config::class.java.getDeclaredField(name)
            field.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            return field.get(null) as MutableSet<String>
        }
        var savedAlways = emptySet<String>()
        var savedDisabled = emptySet<String>()

        lateinit var player: Player
        lateinit var fakePlayer: FakePlayer

        fun runSync(action: () -> Unit) {
            if (Bukkit.isPrimaryThread()) {
                action()
            } else {
                Bukkit
                    .getScheduler()
                    .callSyncMethod(
                        testPlugin,
                        Callable {
                            action()
                            null
                        },
                    ).get()
            }
        }

        fun setModeset(
            player: Player,
            modeset: String,
        ) {
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
                savedAlways = cachedSet("alwaysEnabledModules").toSet()
                savedDisabled = cachedSet("disabledModules").toSet()
                savedConfig = configPaths.associateWith { ocm.config.get(it) }
                player.closeInventory()
                player.setItemOnCursor(ItemStack(Material.AIR))
                player.gameMode = GameMode.SURVIVAL
                player.inventory.clear()
                player.inventory.setItemInOffHand(ItemStack(Material.SHIELD))
                setModeset(player, "new")
            }
        }

        afterTest {
            runSync {
                player.setItemOnCursor(ItemStack(Material.AIR))
                player.closeInventory()
                cachedSet("alwaysEnabledModules").apply {
                    clear()
                    addAll(savedAlways)
                }
                cachedSet("disabledModules").apply {
                    clear()
                    addAll(savedDisabled)
                }
                savedConfig.forEach { (path, value) -> ocm.config.set(path, value) }
                module.reload()
            }
        }

        fun configure(
            whitelist: Boolean,
            items: List<String> = listOf("GOLDEN_APPLE"),
        ) {
            cachedSet("disabledModules").remove("disable-offhand")
            cachedSet("alwaysEnabledModules").add("disable-offhand")
            module.isEnabled(player) shouldBe true
            ocm.config.set("disable-offhand.whitelist", whitelist)
            ocm.config.set("disable-offhand.items", items)
            ocm.config.set("disable-offhand.denied-message", "")
            module.reload()
        }

        val swapOffhand = ClickType.values().firstOrNull { it.name == "SWAP_OFFHAND" }

        fun setViewItem(
            slot: Int,
            item: ItemStack,
        ) {
            // InventoryView changed from a class to an interface; resolve the concrete view method.
            val view = player.openInventory
            val method =
                checkNotNull(
                    kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector.getMethod(
                        view.javaClass,
                        "setItem",
                        "int",
                        "ItemStack",
                    ),
                )
            kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector.invokeMethod<Any>(
                method,
                view,
                slot,
                item,
            )
        }

        // Synthetic events isolate the module's decisions; they do not simulate client slot transactions.
        fun click(
            rawSlot: Int,
            type: ClickType,
            action: InventoryAction,
            key: Int = -1,
        ): InventoryClickEvent {
            val event =
                InventoryClickEvent(player.openInventory, InventoryType.SlotType.CONTAINER, rawSlot, type, action, key)
            module.onInventoryClick(event)
            return event
        }

        for (whitelist in listOf(false, true)) {
            for (container in listOf(false, true)) {
                for (material in listOf(Material.GOLDEN_APPLE, Material.STONE, Material.AIR)) {
                    test(
                        "inventory F respects whitelist=$whitelist container=$container item=$material (#986)",
                    ).config(enabledOrReasonIf = {
                        Enabled(swapOffhand != null, "Inventory offhand shortcut is absent from this API")
                    }) {
                        runSync {
                            configure(whitelist)
                            val rawSlot =
                                if (container) {
                                    player.openInventory(Bukkit.createInventory(null, 9))
                                    0
                                } else {
                                    36
                                }
                            setViewItem(rawSlot, ItemStack(material))
                            // The cursor is unrelated to the keyboard swap.
                            player.setItemOnCursor(ItemStack(Material.GOLDEN_APPLE))
                            val expected =
                                material != Material.AIR && ((material == Material.GOLDEN_APPLE) != whitelist)
                            click(rawSlot, checkNotNull(swapOffhand), InventoryAction.HOTBAR_SWAP).isCancelled shouldBe
                                expected
                        }
                    }
                }
            }
            test("ordinary hand swap respects whitelist=$whitelist") {
                runSync {
                    configure(whitelist)
                    for (material in listOf(Material.GOLDEN_APPLE, Material.STONE, Material.AIR)) {
                        val event = PlayerSwapHandItemsEvent(player, ItemStack(Material.STONE), ItemStack(material))
                        module.onSwapHandItems(event)
                        event.isCancelled shouldBe
                            (material != Material.AIR && ((material == Material.GOLDEN_APPLE) != whitelist))
                    }
                }
            }
        }

        test("empty blacklist allows inventory F (#986)").config(enabledOrReasonIf = {
            Enabled(swapOffhand != null, "Inventory offhand shortcut is absent from this API")
        }) {
            runSync {
                configure(false, emptyList())
                setViewItem(36, ItemStack(Material.STONE))
                click(36, checkNotNull(swapOffhand), InventoryAction.HOTBAR_SWAP).isCancelled shouldBe false
            }
        }

        test("disabled module allows inventory F for a listed item (#986)").config(enabledOrReasonIf = {
            Enabled(swapOffhand != null, "Inventory offhand shortcut is absent from this API")
        }) {
            runSync {
                configure(false)
                cachedSet("alwaysEnabledModules").remove("disable-offhand")
                cachedSet("disabledModules").add("disable-offhand")
                module.isEnabled(player) shouldBe false
                setViewItem(36, ItemStack(Material.GOLDEN_APPLE))
                click(36, checkNotNull(swapOffhand), InventoryAction.HOTBAR_SWAP).isCancelled shouldBe false
            }
        }

        test("offhand drop ignores a blocked cursor item") {
            runSync {
                configure(false)
                player.setItemOnCursor(ItemStack(Material.GOLDEN_APPLE))
                click(45, ClickType.DROP, InventoryAction.DROP_ONE_SLOT).isCancelled shouldBe false
            }
        }

        test("offhand number key checks the hotbar item independently of the cursor") {
            runSync {
                configure(false)
                player.inventory.setItem(1, ItemStack(Material.STONE))
                player.setItemOnCursor(ItemStack(Material.GOLDEN_APPLE))
                click(45, ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP, 1).isCancelled shouldBe false
                player.inventory.setItem(1, ItemStack(Material.GOLDEN_APPLE))
                player.setItemOnCursor(ItemStack(Material.AIR))
                click(45, ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP, 1).isCancelled shouldBe true
            }
        }

        test("offhand cursor placement still rejects blocked items") {
            runSync {
                configure(false)
                player.setItemOnCursor(ItemStack(Material.GOLDEN_APPLE))
                click(45, ClickType.LEFT, InventoryAction.SWAP_WITH_CURSOR).isCancelled shouldBe true
                player.setItemOnCursor(ItemStack(Material.STONE))
                click(45, ClickType.LEFT, InventoryAction.SWAP_WITH_CURSOR).isCancelled shouldBe false
            }
        }

        for (occupied in listOf(false, true)) {
            test("shift-clicking a blocked shield with occupied offhand=$occupied") {
                runSync {
                    configure(false, listOf("SHIELD"))
                    player.inventory.setItemInOffHand(ItemStack(if (occupied) Material.STONE else Material.AIR))
                    setViewItem(36, ItemStack(Material.SHIELD))
                    click(36, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY).isCancelled shouldBe
                        !occupied
                }
            }
        }

        for (material in listOf(Material.GOLDEN_APPLE, Material.STONE)) {
            test("dragging $material into the offhand respects the blacklist") {
                runSync {
                    configure(false)
                    player.inventory.setItemInOffHand(ItemStack(Material.AIR))
                    val event =
                        InventoryDragEvent(
                            player.openInventory,
                            ItemStack(Material.AIR),
                            ItemStack(material),
                            false,
                            mapOf(45 to ItemStack(material)),
                        )
                    module.onInventoryDrag(event)
                    event.isCancelled shouldBe (material == Material.GOLDEN_APPLE)
                }
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
