/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.oldcombatmechanics

import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.engine.concurrency.TestExecutionMode
import io.kotest.matchers.longs.shouldBeBetween
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kernitus.plugin.OldCombatMechanics.FakePlayer
import kernitus.plugin.OldCombatMechanics.MainThreadDispatcherExtension
import kernitus.plugin.OldCombatMechanics.OCMMain
import kernitus.plugin.OldCombatMechanics.OCMTestMain
import kernitus.plugin.OldCombatMechanics.module.ModuleGoldenApple
import kernitus.plugin.OldCombatMechanics.utilities.potions.PotionEffectTypeCompat
import kernitus.plugin.OldCombatMechanics.versions.materials.MaterialRegistry.ENCHANTED_GOLDEN_APPLE
import kotlinx.coroutines.delay
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.inventory.CraftingInventory
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

@OptIn(ExperimentalKotest::class)
class GoldenAppleIntegrationTest : FunSpec({
    val plugin = JavaPlugin.getPlugin(OCMTestMain::class.java)
    lateinit var player: Player
    lateinit var fakePlayer: FakePlayer
    val ocm = plugin as OCMMain
    val module = ModuleGoldenApple.getInstance()

    fun preparePlayer() {
        println("Preparing player")
        val world = Bukkit.getServer().getWorld("world")
        val location = Location(world, 0.0, 100.0, 0.0)

        fakePlayer = FakePlayer(plugin)
        fakePlayer.spawn(location)

        player = checkNotNull(Bukkit.getPlayer(fakePlayer.uuid))
    }

    suspend fun TestScope.withConfig(block: suspend TestScope.() -> Unit) {
        val oldPotionEffects = ocm.config.getBoolean("old-golden-apples.old-potion-effects")
        val normalCooldown = ocm.config.getLong("old-golden-apples.cooldown.normal")
        val enchantedCooldown = ocm.config.getLong("old-golden-apples.cooldown.enchanted")
        val sharedCooldown = ocm.config.getBoolean("old-golden-apples.cooldown.is-shared")
        val crafting = ocm.config.getBoolean("old-golden-apples.enchanted-golden-apple-crafting")
        val noConflict = ocm.config.getBoolean("old-golden-apples.no-conflict-mode")

        try {
            block()
        } finally {
            // Restore config to avoid affecting other tests
            ocm.config.set("old-golden-apples.old-potion-effects", oldPotionEffects)
            ocm.config.set("old-golden-apples.cooldown.normal", normalCooldown)
            ocm.config.set("old-golden-apples.cooldown.enchanted", enchantedCooldown)
            ocm.config.set("old-golden-apples.cooldown.is-shared", sharedCooldown)
            ocm.config.set("old-golden-apples.enchanted-golden-apple-crafting", crafting)
            ocm.config.set("old-golden-apples.no-conflict-mode", noConflict)
            module.reload()
        }
    }

    extensions(MainThreadDispatcherExtension(plugin))
    testExecutionMode = TestExecutionMode.Sequential

    beforeSpec {
        Bukkit.getScheduler().runTask(plugin, Runnable {
            plugin.logger.info("Running before all GoldenAppleIntegrationTest")
            preparePlayer()

            player.gameMode = GameMode.SURVIVAL
            player.maximumNoDamageTicks = 20
            player.noDamageTicks = 0 // remove spawn invulnerability
            player.isInvulnerable = false
            player.isOp = true // Allow command execution
        })
    }

    afterSpec {
        plugin.logger.info("Running after all GoldenAppleIntegrationTest")
        Bukkit.getScheduler().runTask(plugin, Runnable {
            fakePlayer.removePlayer()
        })
    }

    beforeTest {
        // Reset player state before each test
        Bukkit.getScheduler().runTask(plugin, Runnable {
            player.inventory.clear()
            player.activePotionEffects.forEach { player.removePotionEffect(it.type) }
            player.health = player.getAttribute(Attribute.MAX_HEALTH)!!.value
            player.foodLevel = 20
            player.performCommand("ocm mode old") // Ensure module is enabled by default for tests
        })
    }

    context("Potion Effects") {
        test("golden apple effects") {
            // Given
            player.inventory.setItemInMainHand(ItemStack(Material.GOLDEN_APPLE))

            // When
            Bukkit.getPluginManager().callEvent(
                PlayerItemConsumeEvent(player, player.inventory.itemInMainHand, EquipmentSlot.HAND)
            )
            delay(50) // Wait for task to run

            // Then
            val regeneration = player.getPotionEffect(PotionEffectType.REGENERATION)
            val absorption = player.getPotionEffect(PotionEffectType.ABSORPTION)

            regeneration.shouldNotBe(null)
            absorption.shouldNotBe(null)

            regeneration?.duration.shouldBe(5 * 20) // 5 seconds
            regeneration?.amplifier.shouldBe(1)

            absorption?.duration.shouldBe(120 * 20) // 120 seconds
            absorption?.amplifier.shouldBe(0)
        }

        test("enchanted golden apple effects") {
            // Given
            val enchantedGoldenApple = ENCHANTED_GOLDEN_APPLE.newInstance()
            player.inventory.setItemInMainHand(enchantedGoldenApple)

            // When
            Bukkit.getPluginManager()
                .callEvent(PlayerItemConsumeEvent(player, player.inventory.itemInMainHand, EquipmentSlot.HAND))
            delay(50) // Wait for task to run

            // Then
            val regeneration = player.getPotionEffect(PotionEffectType.REGENERATION)
            val absorption = player.getPotionEffect(PotionEffectType.ABSORPTION)
            val resistance = player.getPotionEffect(PotionEffectTypeCompat.RESISTANCE.get())
            val fireResistance = player.getPotionEffect(PotionEffectType.FIRE_RESISTANCE)

            regeneration.shouldNotBe(null)
            absorption.shouldNotBe(null)
            resistance.shouldNotBe(null)
            fireResistance.shouldNotBe(null)

            regeneration?.duration.shouldBe(30 * 20)
            regeneration?.amplifier.shouldBe(4)
            absorption?.duration.shouldBe(120 * 20)
            absorption?.amplifier.shouldBe(0)
            resistance?.duration.shouldBe(300 * 20)
            resistance?.amplifier.shouldBe(0)
            fireResistance?.duration.shouldBe(300 * 20)
            fireResistance?.amplifier.shouldBe(0)
        }

        test("effect application with higher amplifier") {
            // Given
            player.addPotionEffect(PotionEffect(PotionEffectType.REGENERATION, 100, 0)) // Regen I
            player.inventory.setItemInMainHand(ItemStack(Material.GOLDEN_APPLE)) // Gives Regen II (amp 1)

            // When
            Bukkit.getPluginManager()
                .callEvent(PlayerItemConsumeEvent(player, player.inventory.itemInMainHand, EquipmentSlot.HAND))
            delay(50)

            // Then
            val regeneration = player.getPotionEffect(PotionEffectType.REGENERATION)
            regeneration?.amplifier.shouldBe(1)
            regeneration?.duration.shouldBe(5 * 20)
        }

        test("effect application with same amplifier and longer duration") {
            // Given
            player.addPotionEffect(PotionEffect(PotionEffectType.REGENERATION, 50, 1)) // Regen II for 2.5s
            player.inventory.setItemInMainHand(ItemStack(Material.GOLDEN_APPLE)) // Gives Regen II for 5s

            // When
            Bukkit.getPluginManager()
                .callEvent(PlayerItemConsumeEvent(player, player.inventory.itemInMainHand, EquipmentSlot.HAND))
            delay(50)

            // Then
            val regeneration = player.getPotionEffect(PotionEffectType.REGENERATION)
            regeneration?.amplifier shouldBe 1
            regeneration?.duration shouldBe (5 * 20)
        }
    }

    context("Cooldowns") {
        test("golden apple cooldown") {
            withConfig {
                // Given
                ocm.config.set("old-golden-apples.cooldown.normal", 2) // 2 seconds
                module.reload()
                val item = ItemStack(Material.GOLDEN_APPLE)
                player.inventory.setItemInMainHand(item)

                // When
                val event1 = PlayerItemConsumeEvent(player, player.inventory.itemInMainHand, EquipmentSlot.HAND)
                Bukkit.getPluginManager().callEvent(event1)
                // Then
                event1.isCancelled.shouldBe(false)

                // When
                val event2 = PlayerItemConsumeEvent(player, player.inventory.itemInMainHand, EquipmentSlot.HAND)
                Bukkit.getPluginManager().callEvent(event2)
                // Then
                event2.isCancelled.shouldBe(true)

                // When
                delay(2100) // Wait for cooldown
                val event3 = PlayerItemConsumeEvent(player, player.inventory.itemInMainHand, EquipmentSlot.HAND)
                Bukkit.getPluginManager().callEvent(event3)
                // Then
                event3.isCancelled.shouldBe(false)
            }
        }

        test("shared cooldown") {
            withConfig {
                // Given
                ocm.config.set("old-golden-apples.cooldown.normal", 5)
                ocm.config.set("old-golden-apples.cooldown.enchanted", 10)
                ocm.config.set("old-golden-apples.cooldown.is-shared", true)
                module.reload()

                // When eat normal gapple
                player.inventory.setItemInMainHand(ItemStack(Material.GOLDEN_APPLE))
                val consumeGappleEvent =
                    PlayerItemConsumeEvent(player, player.inventory.itemInMainHand, EquipmentSlot.HAND)
                Bukkit.getPluginManager().callEvent(consumeGappleEvent)
                consumeGappleEvent.isCancelled.shouldBe(false)

                // Then try to eat enchanted gapple
                player.inventory.setItemInMainHand(ENCHANTED_GOLDEN_APPLE.newInstance())
                val consumeNappleEvent =
                    PlayerItemConsumeEvent(player, player.inventory.itemInMainHand, EquipmentSlot.HAND)
                Bukkit.getPluginManager().callEvent(consumeNappleEvent)
                consumeNappleEvent.isCancelled.shouldBe(true)
            }
        }

        test("non-shared cooldown") {
            withConfig {
                // Given
                ocm.config.set("old-golden-apples.cooldown.normal", 5)
                ocm.config.set("old-golden-apples.cooldown.enchanted", 10)
                ocm.config.set("old-golden-apples.cooldown.is-shared", false)
                module.reload()

                // When eat normal gapple
                player.inventory.setItemInMainHand(ItemStack(Material.GOLDEN_APPLE))
                val consumeGappleEvent =
                    PlayerItemConsumeEvent(player, player.inventory.itemInMainHand, EquipmentSlot.HAND)
                Bukkit.getPluginManager().callEvent(consumeGappleEvent)
                consumeGappleEvent.isCancelled.shouldBe(false)

                // Then try to eat enchanted gapple
                player.inventory.setItemInMainHand(ENCHANTED_GOLDEN_APPLE.newInstance())
                val consumeNappleEvent =
                    PlayerItemConsumeEvent(player, player.inventory.itemInMainHand, EquipmentSlot.HAND)
                Bukkit.getPluginManager().callEvent(consumeNappleEvent)
                consumeNappleEvent.isCancelled.shouldBe(false)
            }
        }
    }

    context("Configuration options") {
        test("module disabled via modeset") {
            withConfig {
                // Given
                ocm.config.set("old-golden-apples.golden-apple-effects.regeneration.duration", 99)
                module.reload()
                player.performCommand("ocm mode new") // 'new' modeset disables the module
                val item = ItemStack(Material.GOLDEN_APPLE)
                player.inventory.setItemInMainHand(item)

                // When
                Bukkit.getPluginManager()
                Bukkit.getPluginManager()
                    .callEvent(PlayerItemConsumeEvent(player, player.inventory.itemInMainHand, EquipmentSlot.HAND))
                delay(50)

                // Then: vanilla effects should be applied as the module is disabled
                val regeneration = player.getPotionEffect(PotionEffectType.REGENERATION)
                regeneration?.duration.shouldBe(5 * 20) // Vanilla duration
                regeneration?.amplifier.shouldBe(1) // Vanilla amplifier
            }
        }

        test("with old-potion-effects disabled") {
            withConfig {
                // Given
                ocm.config.set("old-golden-apples.old-potion-effects", false)
                ocm.config.set("old-golden-apples.golden-apple-effects.regeneration.duration", 99)
                module.reload()
                val item = ItemStack(Material.GOLDEN_APPLE)
                player.inventory.setItemInMainHand(item)

                // When
                Bukkit.getPluginManager()
                    .callEvent(PlayerItemConsumeEvent(player, player.inventory.itemInMainHand, EquipmentSlot.HAND))
                delay(50)

                // Then: vanilla effects should be applied as old-potion-effects is false
                val regeneration = player.getPotionEffect(PotionEffectType.REGENERATION)
                regeneration?.duration.shouldBe(5 * 20) // Vanilla duration
                regeneration?.amplifier.shouldBe(1) // Vanilla amplifier
            }
        }
    }

    context("Crafting") {
        suspend fun prepareCraftingGrid(inventory: CraftingInventory) {
            inventory.matrix = arrayOf(
                ItemStack(Material.GOLD_BLOCK), ItemStack(Material.GOLD_BLOCK), ItemStack(Material.GOLD_BLOCK),
                ItemStack(Material.GOLD_BLOCK), ItemStack(Material.APPLE), ItemStack(Material.GOLD_BLOCK),
                ItemStack(Material.GOLD_BLOCK), ItemStack(Material.GOLD_BLOCK), ItemStack(Material.GOLD_BLOCK)
            )
            delay(50) // Allow server to process inventory update
        }

        test("enchanted golden apple crafting enabled") {
            withConfig {
                // Given
                ocm.config.set("old-golden-apples.enchanted-golden-apple-crafting", true)
                module.reload()
                player.performCommand("ocm mode old")

                // When
                @Suppress("DEPRECATION") // Legacy API used for older server compatibility in tests.
                val inventoryView = player.openWorkbench(null, true)!!
                val craftingInventory = inventoryView.topInventory as CraftingInventory
                prepareCraftingGrid(craftingInventory)

                // Then
                craftingInventory.result.shouldNotBe(null)
                ENCHANTED_GOLDEN_APPLE.isSame(craftingInventory.result!!).shouldBe(true)

                player.closeInventory()
            }
        }

        test("enchanted golden apple crafting disabled in config") {
            withConfig {
                // Given
                ocm.config.set("old-golden-apples.enchanted-golden-apple-crafting", false)
                module.reload() // This doesn't remove recipe, but PrepareItemCraftEvent handler will deny it
                player.performCommand("ocm mode old")

                // When
                @Suppress("DEPRECATION") // Legacy API used for older server compatibility in tests.
                val inventoryView = player.openWorkbench(null, true)!!
                val craftingInventory = inventoryView.topInventory as CraftingInventory
                prepareCraftingGrid(craftingInventory)

                // Then
                craftingInventory.result.shouldBe(null)

                player.closeInventory()
            }
        }

        test("enchanted golden apple crafting disabled by modeset") {
            withConfig {
                // Given
                ocm.config.set("old-golden-apples.enchanted-golden-apple-crafting", true)
                module.reload()
                player.performCommand("ocm mode new") // disable module for player

                // When
                @Suppress("DEPRECATION") // Legacy API used for older server compatibility in tests.
                val inventoryView = player.openWorkbench(null, true)!!
                val craftingInventory = inventoryView.topInventory as CraftingInventory
                prepareCraftingGrid(craftingInventory)

                // Then
                craftingInventory.result shouldBe null

                player.closeInventory()
            }
        }

        test("no-conflict-mode allows crafting when module disabled for player") {
            withConfig {
                // Given
                ocm.config.set("old-golden-apples.enchanted-golden-apple-crafting", true)
                ocm.config.set("old-golden-apples.no-conflict-mode", true)
                module.reload()
                player.performCommand("ocm mode new") // disable module for player

                // When
                @Suppress("DEPRECATION") // Legacy API used for older server compatibility in tests.
                val inventoryView = player.openWorkbench(null, true)!!
                val craftingInventory = inventoryView.topInventory as CraftingInventory
                prepareCraftingGrid(craftingInventory)

                // Then
                craftingInventory.result shouldNotBe null
                ENCHANTED_GOLDEN_APPLE.isSame(craftingInventory.result!!) shouldBe true

                player.closeInventory()
            }
        }
    }

    context("API methods") {
        test("getGappleCooldown") {
            withConfig {
                // Given
                ocm.config.set("old-golden-apples.cooldown.normal", 10)
                module.reload()
                player.inventory.setItemInMainHand(ItemStack(Material.GOLDEN_APPLE))

                // When
                Bukkit.getPluginManager()
                    .callEvent(PlayerItemConsumeEvent(player, player.inventory.itemInMainHand, EquipmentSlot.HAND))
                delay(50)

                // Then
                var cooldown = module.getGappleCooldown(player.uniqueId)
                cooldown.shouldBeBetween(9L, 10L)

                // When
                delay(5000) // Wait 5 seconds

                // Then
                cooldown = module.getGappleCooldown(player.uniqueId)
                cooldown.shouldBeBetween(4L, 5L)

                // When
                delay(5000) // Wait another 5 seconds

                // Then
                cooldown = module.getGappleCooldown(player.uniqueId)
                cooldown.shouldBe(0L)
            }
        }

        test("getNappleCooldown") {
            withConfig {
                // Given
                ocm.config.set("old-golden-apples.cooldown.enchanted", 20)
                module.reload()
                player.inventory.setItemInMainHand(ENCHANTED_GOLDEN_APPLE.newInstance())

                // When
                Bukkit.getPluginManager()
                    .callEvent(PlayerItemConsumeEvent(player, player.inventory.itemInMainHand, EquipmentSlot.HAND))
                delay(50)

                // Then
                var cooldown = module.getNappleCooldown(player.uniqueId)
                cooldown.shouldBeBetween(19L, 20L)

                // When
                delay(10000) // Wait 10 seconds

                // Then
                cooldown = module.getNappleCooldown(player.uniqueId)
                cooldown.shouldBeBetween(9L, 10L)

                // When
                delay(10000) // Wait another 10 seconds

                // Then
                cooldown = module.getNappleCooldown(player.uniqueId)
                cooldown.shouldBe(0L)
            }
        }
    }
})
