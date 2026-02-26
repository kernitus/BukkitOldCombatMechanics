/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics

import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.StringSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.shouldBe
import kernitus.plugin.OldCombatMechanics.module.ModuleSwordBlocking
import kotlinx.coroutines.delay
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.BlockFace
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.Item
import org.bukkit.entity.Player
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractAtEntityEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.util.Vector
import java.util.concurrent.Callable

@OptIn(ExperimentalKotest::class)
class SwordBlockingIntegrationTest :
    StringSpec({
        val plugin = JavaPlugin.getPlugin(OCMTestMain::class.java)
        val ocm = JavaPlugin.getPlugin(OCMMain::class.java)
        val module =
            ModuleLoader
                .getModules()
                .filterIsInstance<ModuleSwordBlocking>()
                .firstOrNull() ?: error("ModuleSwordBlocking not registered")
        extension(MainThreadDispatcherExtension(plugin))
        lateinit var player: Player
        lateinit var fakePlayer: FakePlayer

        fun <T> runSync(action: () -> T): T =
            if (Bukkit.isPrimaryThread()) {
                action()
            } else {
                Bukkit
                    .getScheduler()
                    .callSyncMethod(
                        plugin,
                        Callable {
                            action()
                        },
                    ).get()
            }

        fun preparePlayer() {
            println("Preparing player")
            val world = Bukkit.getServer().getWorld("world")
            val location = Location(world, 0.0, 100.0, 0.0)

            fakePlayer = FakePlayer(plugin)
            fakePlayer.spawn(location)

            player = checkNotNull(Bukkit.getPlayer(fakePlayer.uuid))
        }

        beforeSpec {
            Bukkit.getScheduler().runTask(
                plugin,
                Runnable {
                    plugin.logger.info("Running before all")
                    preparePlayer()

                    player.gameMode = GameMode.SURVIVAL
                    player.maximumNoDamageTicks = 20
                    player.noDamageTicks = 0 // remove spawn invulnerability
                    player.isInvulnerable = false
                },
            )
        }

        fun rightClickWithMainHand() =
            runSync {
                val event =
                    PlayerInteractEvent(
                        player,
                        Action.RIGHT_CLICK_AIR,
                        player.inventory.itemInMainHand,
                        null,
                        BlockFace.SELF,
                        EquipmentSlot.HAND,
                    )
                Bukkit.getPluginManager().callEvent(event)
            }

        fun rightClickEntity(
            target: Entity,
            hand: EquipmentSlot,
        ) = runSync {
            Bukkit.getPluginManager().callEvent(PlayerInteractEntityEvent(player, target, hand))
        }

        fun rightClickEntityAt(
            target: Entity,
            hand: EquipmentSlot,
        ) = runSync {
            Bukkit.getPluginManager().callEvent(PlayerInteractAtEntityEvent(player, target, Vector(0.0, 1.0, 0.0), hand))
        }

        fun spawnEntityTarget(): Entity =
            runSync {
                player.world.spawnEntity(player.location.clone().add(1.0, 0.0, 0.0), EntityType.VILLAGER)
            }

        fun forceRestoreViaHotbarChange() =
            runSync {
                val previous = player.inventory.heldItemSlot
                val next = (previous + 1) % 9
                Bukkit.getPluginManager().callEvent(PlayerItemHeldEvent(player, previous, next))
                player.inventory.heldItemSlot = previous
            }

        suspend fun delayTicks(ticks: Long) {
            delay(ticks * 50L)
        }

        suspend fun TestScope.withUsePermission(
            required: Boolean,
            block: suspend TestScope.() -> Unit,
        ) {
            val original = runSync { ocm.config.getBoolean("sword-blocking.use-permission") }
            runSync {
                ocm.config.set("sword-blocking.use-permission", required)
                module.reload()
                ModuleLoader.toggleModules()
            }
            try {
                block()
            } finally {
                runSync {
                    ocm.config.set("sword-blocking.use-permission", original)
                    module.reload()
                    ModuleLoader.toggleModules()
                }
            }
        }

        beforeTest {
            runSync {
                player.inventory.clear()
                player.noDamageTicks = 0
                player.maximumNoDamageTicks = 20
                player.isInvulnerable = false
            }
        }

        afterTest {
            forceRestoreViaHotbarChange()
            runSync { player.inventory.clear() }
        }

        afterSpec {
            plugin.logger.info("Running after all")
            Bukkit.getScheduler().runTask(
                plugin,
                Runnable {
                    fakePlayer.removePlayer()
                },
            )
        }

        "adds blocking when right-clicking with a sword (shield on legacy, consumable on Paper)" {
            runSync {
                player.inventory.setItemInMainHand(ItemStack(Material.DIAMOND_SWORD))
                player.inventory.setItemInOffHand(ItemStack(Material.AIR))
            }

            rightClickWithMainHand()
            delayTicks(1)

            runSync {
                // Legacy path: module injects a shield (actual "blocking" state is client-driven).
                // Paper path: offhand remains intact and a consumable-based use animation can surface as "hand raised".
                (player.inventory.itemInOffHand.type == Material.SHIELD || player.isBlocking || player.isHandRaised) shouldBe true
                // Legacy path injects shield; paper path keeps offhand intact
                setOf(Material.SHIELD, Material.AIR).contains(player.inventory.itemInOffHand.type) shouldBe true
            }
        }

        "does not start blocking without a sword in the main hand" {
            runSync {
                player.inventory.setItemInMainHand(ItemStack(Material.STICK))
                player.inventory.setItemInOffHand(ItemStack(Material.AIR))
            }

            rightClickWithMainHand()

            runSync {
                player.isBlocking shouldBe false
                player.inventory.itemInOffHand.type shouldBe Material.AIR
            }
        }

        "starts blocking on main-hand entity right-click (shield on legacy, consumable on Paper)" {
            val target = spawnEntityTarget()
            try {
                runSync {
                    player.inventory.setItemInMainHand(ItemStack(Material.DIAMOND_SWORD))
                    player.inventory.setItemInOffHand(ItemStack(Material.AIR))
                }

                rightClickEntity(target, EquipmentSlot.HAND)
                delayTicks(1)

                runSync {
                    (player.inventory.itemInOffHand.type == Material.SHIELD || player.isBlocking || player.isHandRaised) shouldBe true
                    setOf(Material.SHIELD, Material.AIR).contains(player.inventory.itemInOffHand.type) shouldBe true
                }
            } finally {
                runSync { target.remove() }
            }
        }

        "does not start blocking on offhand entity right-click" {
            val target = spawnEntityTarget()
            try {
                runSync {
                    player.inventory.setItemInMainHand(ItemStack(Material.DIAMOND_SWORD))
                    player.inventory.setItemInOffHand(ItemStack(Material.AIR))
                }

                rightClickEntity(target, EquipmentSlot.OFF_HAND)
                delayTicks(1)

                runSync {
                    player.isBlocking shouldBe false
                    player.inventory.itemInOffHand.type shouldBe Material.AIR
                }
            } finally {
                runSync { target.remove() }
            }
        }

        "entity interact plus interact-at should not duplicate side effects" {
            val originalOffhand = ItemStack(Material.APPLE)
            val target = spawnEntityTarget()
            try {
                runSync {
                    player.inventory.setItemInMainHand(ItemStack(Material.IRON_SWORD))
                    player.inventory.setItemInOffHand(originalOffhand.clone())
                }

                rightClickEntity(target, EquipmentSlot.HAND)
                rightClickEntityAt(target, EquipmentSlot.HAND)
                delayTicks(1)

                runSync {
                    (player.inventory.itemInOffHand.type == Material.SHIELD || player.isBlocking || player.isHandRaised) shouldBe true
                    if (player.inventory.itemInOffHand.type == Material.SHIELD) {
                        forceRestoreViaHotbarChange()
                    }
                    player.inventory.itemInOffHand.type shouldBe originalOffhand.type
                }
            } finally {
                runSync { target.remove() }
            }
        }

        "restores the previous offhand item after a hotbar change (or leaves untouched on Paper path)" {
            val originalOffhand = ItemStack(Material.APPLE)
            runSync {
                player.inventory.setItemInMainHand(ItemStack(Material.IRON_SWORD))
                player.inventory.setItemInOffHand(originalOffhand.clone())
            }

            rightClickWithMainHand()

            runSync {
                if (player.inventory.itemInOffHand.type == Material.SHIELD) {
                    forceRestoreViaHotbarChange()
                    player.isBlocking shouldBe false
                    player.inventory.itemInOffHand.type shouldBe originalOffhand.type
                } else {
                    // Paper path: offhand never changed
                    player.inventory.itemInOffHand.type shouldBe originalOffhand.type
                }
            }
        }

        "cancels dropping the temporary shield and restores the stored item (legacy path only)" {
            runSync {
                player.inventory.setItemInMainHand(ItemStack(Material.DIAMOND_SWORD))
                player.inventory.setItemInOffHand(ItemStack(Material.AIR))
            }

            rightClickWithMainHand()

            val dropped: Item = runSync { player.world.dropItem(player.location, ItemStack(Material.SHIELD)) }

            runSync {
                Bukkit.getPluginManager().callEvent(PlayerDropItemEvent(player, dropped))
            }

            runSync {
                if (player.inventory.itemInOffHand.type == Material.SHIELD) {
                    player.inventory.itemInOffHand.type shouldBe Material.AIR
                    player.isBlocking shouldBe false
                } else {
                    // Paper path: no injected shield; ensure we did not cancel normal state
                    player.inventory.itemInOffHand.type shouldBe Material.AIR
                }
            }
            runSync { dropped.remove() }
        }

        "respects permission requirement when enabled" {
            withUsePermission(required = true) {
                runSync {
                    player.isOp = false
                    player.inventory.setItemInMainHand(ItemStack(Material.DIAMOND_SWORD))
                    player.inventory.setItemInOffHand(ItemStack(Material.AIR))
                }

                rightClickWithMainHand()
                delayTicks(1)

                runSync {
                    player.isBlocking shouldBe false
                    player.inventory.itemInOffHand.type shouldBe Material.AIR
                }

                runSync { player.addAttachment(plugin, "oldcombatmechanics.swordblock", true) }
                rightClickWithMainHand()
                delayTicks(1)

                runSync {
                    // Legacy path injects a shield and sets isBlocking; Paper path keeps offhand intact and uses a
                    // consumable-based use animation which can surface as "hand raised".
                    (player.inventory.itemInOffHand.type == Material.SHIELD || player.isBlocking || player.isHandRaised) shouldBe true
                    setOf(Material.SHIELD, Material.AIR).contains(player.inventory.itemInOffHand.type) shouldBe true
                }
            }
        }

        "does not replace an existing real shield in offhand" {
            val namedShield =
                ItemStack(Material.SHIELD).apply {
                    val meta = itemMeta
                    meta?.setDisplayName("Real Shield")
                    itemMeta = meta
                }

            runSync {
                player.inventory.setItemInMainHand(ItemStack(Material.IRON_SWORD))
                player.inventory.setItemInOffHand(namedShield)
            }

            rightClickWithMainHand()

            runSync {
                val meta = player.inventory.itemInOffHand.itemMeta
                meta?.displayName shouldBe "Real Shield"
                player.inventory.itemInOffHand.type shouldBe Material.SHIELD
            }
        }
    })
