/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics

import com.cryptomorin.xseries.XAttribute
import com.cryptomorin.xseries.XMaterial
import com.cryptomorin.xseries.XPotion
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.longs.shouldBeBetween
import kernitus.plugin.OldCombatMechanics.module.ModuleGoldenApple
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.event.inventory.PrepareItemCraftEvent
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.inventory.CraftingInventory
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import kotlin.coroutines.resume
import kernitus.plugin.OldCombatMechanics.TesterUtils.getPotionEffectCompat

@OptIn(ExperimentalKotest::class)
class GoldenAppleIntegrationTest : FunSpec({
    val testPlugin = JavaPlugin.getPlugin(OCMTestMain::class.java)
    val ocm = JavaPlugin.getPlugin(OCMMain::class.java)
    val module = ModuleGoldenApple.getInstance()
    lateinit var player: Player
    lateinit var fakePlayer: FakePlayer

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
            ocm.config.set("old-golden-apples.old-potion-effects", oldPotionEffects)
            ocm.config.set("old-golden-apples.cooldown.normal", normalCooldown)
            ocm.config.set("old-golden-apples.cooldown.enchanted", enchantedCooldown)
            ocm.config.set("old-golden-apples.cooldown.is-shared", sharedCooldown)
            ocm.config.set("old-golden-apples.enchanted-golden-apple-crafting", crafting)
            ocm.config.set("old-golden-apples.no-conflict-mode", noConflict)
            module.reload()
            ModuleLoader.toggleModules()
        }
    }

    extensions(MainThreadDispatcherExtension(testPlugin))

    fun runSync(action: () -> Unit) {
        if (Bukkit.isPrimaryThread()) {
            action()
        } else {
            Bukkit.getScheduler().callSyncMethod(testPlugin) {
                action()
                null
            }.get()
        }
    }

    fun setModeset(modeset: String) {
        val playerData = kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage.getPlayerData(player.uniqueId)
        playerData.setModesetForWorld(player.world.uid, modeset)
        kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage.setPlayerData(player.uniqueId, playerData)
    }

    fun callConsume(item: ItemStack): PlayerItemConsumeEvent {
        val ctor = PlayerItemConsumeEvent::class.java.constructors.firstOrNull { constructor ->
            val params = constructor.parameterTypes
            params.size == 3 &&
                Player::class.java.isAssignableFrom(params[0]) &&
                ItemStack::class.java.isAssignableFrom(params[1]) &&
                EquipmentSlot::class.java.isAssignableFrom(params[2])
        }
        val event = if (ctor != null) {
            ctor.newInstance(player, item, EquipmentSlot.HAND) as PlayerItemConsumeEvent
        } else {
            PlayerItemConsumeEvent(player, item)
        }
        Bukkit.getPluginManager().callEvent(event)
        return event
    }

    fun prepareCraftResult(result: ItemStack): PrepareItemCraftEvent {
        val openWorkbench = player.javaClass.getMethod(
            "openWorkbench",
            Location::class.java,
            Boolean::class.javaPrimitiveType
        )
        val viewObj = openWorkbench.invoke(player, null, true) ?: error("Workbench view was null")
        val getTopInventory = viewObj.javaClass.getMethod("getTopInventory")
        val inventory = getTopInventory.invoke(viewObj) as CraftingInventory
        inventory.result = result

        val ctor = PrepareItemCraftEvent::class.java.constructors.firstOrNull { constructor ->
            val params = constructor.parameterTypes
            params.size == 3 &&
                CraftingInventory::class.java.isAssignableFrom(params[0]) &&
                params[2] == Boolean::class.javaPrimitiveType
        } ?: error("PrepareItemCraftEvent constructor not found")
        return ctor.newInstance(inventory, viewObj, false) as PrepareItemCraftEvent
    }

    fun assertDuration(effect: PotionEffect?, expectedTicks: Int) {
        effect.shouldNotBe(null)
        val duration = effect!!.duration
        duration.shouldBeGreaterThanOrEqual(expectedTicks - 10)
        duration.shouldBeLessThanOrEqual(expectedTicks)
    }

    suspend fun waitForEffects(ticks: Long = 2L) {
        suspendCancellableCoroutine { continuation ->
            Bukkit.getScheduler().runTaskLater(testPlugin, Runnable {
                if (continuation.isActive) {
                    continuation.resume(Unit)
                }
            }, ticks)
        }
    }

    fun maxHealthAttribute(): Attribute {
        return XAttribute.MAX_HEALTH.get() ?: error("Max health attribute not available")
    }

    fun enchantedAppleItem(): ItemStack {
        return XMaterial.ENCHANTED_GOLDEN_APPLE.parseItem()
            ?: error("Enchanted golden apple item not available")
    }

    beforeSpec {
        runSync {
            val world = Bukkit.getServer().getWorld("world")
            val location = Location(world, 0.0, 100.0, 0.0)

            fakePlayer = FakePlayer(testPlugin)
            fakePlayer.spawn(location)

            player = checkNotNull(Bukkit.getPlayer(fakePlayer.uuid))
            player.gameMode = GameMode.SURVIVAL
            player.maximumNoDamageTicks = 20
            player.noDamageTicks = 0
            player.isInvulnerable = false
            player.isOp = true
            setModeset("old")
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
            player.activePotionEffects.forEach { player.removePotionEffect(it.type) }
            player.health = player.getAttribute(maxHealthAttribute())!!.value
            player.foodLevel = 20
            setModeset("old")
            module.reload()
        }
    }

    context("Potion Effects") {
        test("golden apple applies configured effects") {
            player.inventory.setItemInMainHand(ItemStack(Material.GOLDEN_APPLE))
            callConsume(player.inventory.itemInMainHand)
            waitForEffects()

            val regeneration = player.getPotionEffectCompat(PotionEffectType.REGENERATION)
            val absorption = player.getPotionEffectCompat(PotionEffectType.ABSORPTION)

            assertDuration(regeneration, 5 * 20)
            regeneration?.amplifier shouldBe 1
            assertDuration(absorption, 120 * 20)
            absorption?.amplifier shouldBe 0
        }

        test("enchanted golden apple applies configured effects") {
            player.inventory.setItemInMainHand(enchantedAppleItem())
            callConsume(player.inventory.itemInMainHand)
            waitForEffects()

            val regeneration = player.getPotionEffectCompat(PotionEffectType.REGENERATION)
            val absorption = player.getPotionEffectCompat(PotionEffectType.ABSORPTION)
            val resistance = player.getPotionEffectCompat(XPotion.RESISTANCE.get()!!)
            val fireResistance = player.getPotionEffectCompat(PotionEffectType.FIRE_RESISTANCE)

            assertDuration(regeneration, 30 * 20)
            regeneration?.amplifier shouldBe 4
            assertDuration(absorption, 120 * 20)
            absorption?.amplifier shouldBe 0
            assertDuration(resistance, 300 * 20)
            resistance?.amplifier shouldBe 0
            assertDuration(fireResistance, 300 * 20)
            fireResistance?.amplifier shouldBe 0
        }

        test("higher amplifier replaces existing effect") {
            player.addPotionEffect(PotionEffect(PotionEffectType.REGENERATION, 100, 0))
            player.inventory.setItemInMainHand(ItemStack(Material.GOLDEN_APPLE))

            callConsume(player.inventory.itemInMainHand)
            waitForEffects()

            val regeneration = player.getPotionEffectCompat(PotionEffectType.REGENERATION)
            regeneration?.amplifier shouldBe 1
            assertDuration(regeneration, 5 * 20)
        }

        test("same amplifier with longer duration refreshes effect") {
            player.addPotionEffect(PotionEffect(PotionEffectType.REGENERATION, 50, 1))
            player.inventory.setItemInMainHand(ItemStack(Material.GOLDEN_APPLE))

            callConsume(player.inventory.itemInMainHand)
            waitForEffects()

            val regeneration = player.getPotionEffectCompat(PotionEffectType.REGENERATION)
            regeneration?.amplifier shouldBe 1
            assertDuration(regeneration, 5 * 20)
        }

        test("lower amplifier does not override existing effect") {
            player.addPotionEffect(PotionEffect(PotionEffectType.REGENERATION, 100, 3))
            player.inventory.setItemInMainHand(ItemStack(Material.GOLDEN_APPLE))

            callConsume(player.inventory.itemInMainHand)
            waitForEffects()

            val regeneration = player.getPotionEffectCompat(PotionEffectType.REGENERATION)
            regeneration?.amplifier shouldBe 3
        }

        test("old-potion-effects disabled leaves effects unchanged") {
            withConfig {
                ocm.config.set("old-golden-apples.old-potion-effects", false)
                module.reload()

                player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 200, 0))
                player.inventory.setItemInMainHand(ItemStack(Material.GOLDEN_APPLE))
                callConsume(player.inventory.itemInMainHand)
                waitForEffects()

                player.getPotionEffectCompat(PotionEffectType.SPEED).shouldNotBe(null)
                player.getPotionEffectCompat(PotionEffectType.REGENERATION).shouldBe(null)
                player.getPotionEffectCompat(PotionEffectType.ABSORPTION).shouldBe(null)
            }
        }
    }

    context("Cooldowns") {
        test("repeated consumption is blocked by cooldown") {
            withConfig {
                ocm.config.set("old-golden-apples.cooldown.normal", 60)
                ocm.config.set("old-golden-apples.cooldown.enchanted", 0)
                ocm.config.set("old-golden-apples.cooldown.is-shared", false)
                module.reload()

                player.inventory.setItemInMainHand(ItemStack(Material.GOLDEN_APPLE))
                val first = callConsume(player.inventory.itemInMainHand)
                first.isCancelled shouldBe false

                val second = callConsume(player.inventory.itemInMainHand)
                second.isCancelled shouldBe true
            }
        }

        test("shared cooldown blocks other apple type") {
            withConfig {
                ocm.config.set("old-golden-apples.cooldown.normal", 60)
                ocm.config.set("old-golden-apples.cooldown.enchanted", 60)
                ocm.config.set("old-golden-apples.cooldown.is-shared", true)
                module.reload()

                player.inventory.setItemInMainHand(ItemStack(Material.GOLDEN_APPLE))
                callConsume(player.inventory.itemInMainHand)

                player.inventory.setItemInMainHand(enchantedAppleItem())
                val enchantedEvent = callConsume(player.inventory.itemInMainHand)
                enchantedEvent.isCancelled shouldBe true
            }
        }

        test("separate cooldown allows other apple type") {
            withConfig {
                ocm.config.set("old-golden-apples.cooldown.normal", 60)
                ocm.config.set("old-golden-apples.cooldown.enchanted", 60)
                ocm.config.set("old-golden-apples.cooldown.is-shared", false)
                module.reload()

                player.inventory.setItemInMainHand(ItemStack(Material.GOLDEN_APPLE))
                callConsume(player.inventory.itemInMainHand)

                player.inventory.setItemInMainHand(enchantedAppleItem())
                val enchantedEvent = callConsume(player.inventory.itemInMainHand)
                enchantedEvent.isCancelled shouldBe false
            }
        }

        test("cooldown getters return remaining seconds") {
            withConfig {
                ocm.config.set("old-golden-apples.cooldown.normal", 5)
                ocm.config.set("old-golden-apples.cooldown.enchanted", 5)
                ocm.config.set("old-golden-apples.cooldown.is-shared", false)
                module.reload()

                player.inventory.setItemInMainHand(ItemStack(Material.GOLDEN_APPLE))
                callConsume(player.inventory.itemInMainHand)
                module.getGappleCooldown(player.uniqueId).shouldBeBetween(1, 5)

                player.inventory.setItemInMainHand(enchantedAppleItem())
                callConsume(player.inventory.itemInMainHand)
                module.getNappleCooldown(player.uniqueId).shouldBeBetween(1, 5)
            }
        }
    }

    context("Crafting") {
        test("enchanted golden apple crafting is blocked when disabled") {
            withConfig {
                ocm.config.set("old-golden-apples.enchanted-golden-apple-crafting", false)
                ocm.config.set("old-golden-apples.no-conflict-mode", false)
                module.reload()

                val event = prepareCraftResult(enchantedAppleItem())
                Bukkit.getPluginManager().callEvent(event)
                event.inventory.result.shouldBe(null)
                player.closeInventory()
            }
        }

        test("no-conflict-mode preserves crafting result") {
            withConfig {
                ocm.config.set("old-golden-apples.enchanted-golden-apple-crafting", false)
                ocm.config.set("old-golden-apples.no-conflict-mode", true)
                module.reload()

                val event = prepareCraftResult(enchantedAppleItem())
                Bukkit.getPluginManager().callEvent(event)
                event.inventory.result.shouldNotBe(null)
                player.closeInventory()
            }
        }

        test("unknown modeset disables crafting") {
            withConfig {
                ocm.config.set("old-golden-apples.enchanted-golden-apple-crafting", true)
                ocm.config.set("old-golden-apples.no-conflict-mode", false)
                module.reload()
                setModeset("missing")

                val event = prepareCraftResult(enchantedAppleItem())
                Bukkit.getPluginManager().callEvent(event)
                event.inventory.result.shouldBe(null)
                player.closeInventory()
            }
        }
    }
})
