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
import io.kotest.core.test.Enabled
import io.kotest.core.test.TestScope
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.longs.shouldBeBetween
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kernitus.plugin.OldCombatMechanics.TesterUtils.getPotionEffectCompat
import kernitus.plugin.OldCombatMechanics.module.ModuleGoldenApple
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector
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

@OptIn(ExperimentalKotest::class)
class GoldenAppleIntegrationTest :
    FunSpec({
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
            val showItemCooldown = ocm.config.getBoolean("old-golden-apples.cooldown.show-item-cooldown")
            val crafting = ocm.config.getBoolean("old-golden-apples.enchanted-golden-apple-crafting")
            val noConflict = ocm.config.getBoolean("old-golden-apples.no-conflict-mode")

            try {
                block()
            } finally {
                ocm.config.set("old-golden-apples.old-potion-effects", oldPotionEffects)
                ocm.config.set("old-golden-apples.cooldown.normal", normalCooldown)
                ocm.config.set("old-golden-apples.cooldown.enchanted", enchantedCooldown)
                ocm.config.set("old-golden-apples.cooldown.is-shared", sharedCooldown)
                ocm.config.set("old-golden-apples.cooldown.show-item-cooldown", showItemCooldown)
                ocm.config.set("old-golden-apples.enchanted-golden-apple-crafting", crafting)
                ocm.config.set("old-golden-apples.no-conflict-mode", noConflict)
                module.reload()
                ModuleLoader.toggleModules()
                if (Material.getMaterial("ENCHANTED_GOLDEN_APPLE") != null) {
                    player.setCooldown(Material.GOLDEN_APPLE, 0)
                    player.setCooldown(Material.ENCHANTED_GOLDEN_APPLE, 0)
                }
            }
        }

        extensions(MainThreadDispatcherExtension(testPlugin))

        fun runSync(action: () -> Unit) {
            if (Bukkit.isPrimaryThread()) {
                action()
            } else {
                Bukkit
                    .getScheduler()
                    .callSyncMethod(testPlugin) {
                        action()
                        null
                    }.get()
            }
        }

        fun setModeset(modeset: String) {
            val playerData =
                kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage.getPlayerData(
                    player.uniqueId,
                )
            playerData.setModesetForWorld(player.world.uid, modeset)
            kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage
                .setPlayerData(player.uniqueId, playerData)
        }

        fun callConsume(item: ItemStack): PlayerItemConsumeEvent {
            val ctor =
                PlayerItemConsumeEvent::class.java.constructors.firstOrNull { constructor ->
                    val params = constructor.parameterTypes
                    params.size == 3 &&
                        Player::class.java.isAssignableFrom(params[0]) &&
                        ItemStack::class.java.isAssignableFrom(params[1]) &&
                        EquipmentSlot::class.java.isAssignableFrom(params[2])
                }
            val event =
                if (ctor != null) {
                    ctor.newInstance(player, item, EquipmentSlot.HAND) as PlayerItemConsumeEvent
                } else {
                    PlayerItemConsumeEvent(player, item)
                }
            Bukkit.getPluginManager().callEvent(event)
            return event
        }

        fun prepareCraftResult(result: ItemStack): PrepareItemCraftEvent {
            val openWorkbench =
                player.javaClass.getMethod(
                    "openWorkbench",
                    Location::class.java,
                    Boolean::class.javaPrimitiveType,
                )
            val viewObj = openWorkbench.invoke(player, null, true) ?: error("Workbench view was null")
            val getTopInventory = viewObj.javaClass.getMethod("getTopInventory")
            val inventory = getTopInventory.invoke(viewObj) as CraftingInventory
            inventory.result = result

            val ctor =
                PrepareItemCraftEvent::class.java.constructors.firstOrNull { constructor ->
                    val params = constructor.parameterTypes
                    params.size == 3 &&
                        CraftingInventory::class.java.isAssignableFrom(params[0]) &&
                        params[2] == Boolean::class.javaPrimitiveType
                } ?: error("PrepareItemCraftEvent constructor not found")
            return ctor.newInstance(inventory, viewObj, false) as PrepareItemCraftEvent
        }

        fun assertDuration(
            effect: PotionEffect?,
            expectedTicks: Int,
        ) {
            effect.shouldNotBe(null)
            val duration = effect!!.duration
            duration.shouldBeGreaterThanOrEqual(expectedTicks - 10)
            duration.shouldBeLessThanOrEqual(expectedTicks)
        }

        suspend fun waitForEffects(ticks: Long = 2L) {
            suspendCancellableCoroutine { continuation ->
                Bukkit.getScheduler().runTaskLater(
                    testPlugin,
                    Runnable {
                        if (continuation.isActive) {
                            continuation.resume(Unit)
                        }
                    },
                    ticks,
                )
            }
        }

        fun maxHealthAttribute(): Attribute = XAttribute.MAX_HEALTH.get() ?: error("Max health attribute not available")

        fun enchantedAppleItem(): ItemStack =
            XMaterial.ENCHANTED_GOLDEN_APPLE.parseItem()
                ?: error("Enchanted golden apple item not available")

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

        context("Item cooldown indicator") {
            val modernMaterials =
                Enabled(
                    Reflector.versionIsNewerOrEqualTo(1, 13, 0),
                    "Enchanted golden apples share the GOLDEN_APPLE material before 1.13",
                )

            fun configureIndicator(
                shared: Boolean,
                show: Boolean = true,
            ) {
                ocm.config.set("old-golden-apples.cooldown.normal", 10)
                ocm.config.set("old-golden-apples.cooldown.enchanted", 30)
                ocm.config.set("old-golden-apples.cooldown.is-shared", shared)
                ocm.config.set("old-golden-apples.cooldown.show-item-cooldown", show)
                module.reload()
                player.setCooldown(Material.GOLDEN_APPLE, 0)
                player.setCooldown(Material.ENCHANTED_GOLDEN_APPLE, 0)
            }

            test("legacy shared material never receives a native indicator")
                .config(enabledOrReasonIf = {
                    Enabled(!Reflector.versionIsNewerOrEqualTo(1, 13, 0), "Distinct apple materials are present")
                }) {
                    withConfig {
                        ocm.config.set("old-golden-apples.cooldown.normal", 10)
                        ocm.config.set("old-golden-apples.cooldown.enchanted", 30)
                        ocm.config.set("old-golden-apples.cooldown.show-item-cooldown", true)
                        module.reload()
                        callConsume(ItemStack(Material.GOLDEN_APPLE)).isCancelled shouldBe false
                        waitForEffects()
                        player.getCooldown(Material.GOLDEN_APPLE) shouldBe 0
                    }
                }

            test("eating shows the cooldown on that apple type only")
                .config(enabledOrReasonIf = { modernMaterials }) {
                    withConfig {
                        configureIndicator(shared = false)
                        callConsume(ItemStack(Material.GOLDEN_APPLE)).isCancelled shouldBe false

                        waitForEffects()
                        player.getCooldown(Material.GOLDEN_APPLE).shouldBeGreaterThanOrEqual(190)
                        player.getCooldown(Material.GOLDEN_APPLE).shouldBeLessThanOrEqual(200)
                        player.getCooldown(Material.ENCHANTED_GOLDEN_APPLE) shouldBe 0
                    }
                }

            test("shared cooldown shows each apple type's own cooldown")
                .config(enabledOrReasonIf = { modernMaterials }) {
                    withConfig {
                        configureIndicator(shared = true)
                        callConsume(ItemStack(Material.GOLDEN_APPLE)).isCancelled shouldBe false

                        waitForEffects()
                        player.getCooldown(Material.GOLDEN_APPLE).shouldBeGreaterThanOrEqual(190)
                        player.getCooldown(Material.GOLDEN_APPLE).shouldBeLessThanOrEqual(200)
                        player.getCooldown(Material.ENCHANTED_GOLDEN_APPLE).shouldBeGreaterThanOrEqual(590)
                        player.getCooldown(Material.ENCHANTED_GOLDEN_APPLE).shouldBeLessThanOrEqual(600)
                    }
                }

            test("shared consumption refreshes the other type after the shorter cooldown expires")
                .config(enabledOrReasonIf = { modernMaterials }) {
                    withConfig {
                        configureIndicator(shared = true)
                        ocm.config.set("old-golden-apples.cooldown.normal", 1)
                        module.reload()
                        callConsume(ItemStack(Material.GOLDEN_APPLE)).isCancelled shouldBe false
                        waitForEffects(25)
                        player.getCooldown(Material.GOLDEN_APPLE) shouldBe 0
                        val previousEnchanted = player.getCooldown(Material.ENCHANTED_GOLDEN_APPLE)
                        previousEnchanted.shouldBeLessThanOrEqual(580)
                        callConsume(ItemStack(Material.GOLDEN_APPLE)).isCancelled shouldBe false
                        waitForEffects()
                        player.getCooldown(Material.GOLDEN_APPLE).shouldBeGreaterThanOrEqual(15)
                        player.getCooldown(Material.ENCHANTED_GOLDEN_APPLE).shouldBeGreaterThanOrEqual(590)
                    }
                }

            test("reload releases the owned native cooldown")
                .config(enabledOrReasonIf = { modernMaterials }) {
                    withConfig {
                        configureIndicator(shared = false)
                        callConsume(ItemStack(Material.GOLDEN_APPLE))
                        waitForEffects()
                        player.getCooldown(Material.GOLDEN_APPLE).shouldBeGreaterThanOrEqual(190)
                        module.reload()
                        player.getCooldown(Material.GOLDEN_APPLE) shouldBe 0
                    }
                }

            test("existing foreign cooldown remains untouched")
                .config(enabledOrReasonIf = { modernMaterials }) {
                    withConfig {
                        configureIndicator(shared = false)
                        player.setCooldown(Material.GOLDEN_APPLE, 900)
                        callConsume(ItemStack(Material.GOLDEN_APPLE))
                        player.getCooldown(Material.GOLDEN_APPLE) shouldBe 900
                        waitForEffects()
                        player.getCooldown(Material.GOLDEN_APPLE).shouldBeGreaterThanOrEqual(890)
                        module.reload()
                        player.getCooldown(Material.GOLDEN_APPLE).shouldBeGreaterThanOrEqual(890)
                        player.setCooldown(Material.GOLDEN_APPLE, 0)
                    }
                }

            test("native countdown retains ownership and later foreign changes survive cleanup")
                .config(enabledOrReasonIf = { modernMaterials }) {
                    withConfig {
                        for (foreign in listOf(40, 900)) {
                            configureIndicator(shared = false)
                            callConsume(ItemStack(Material.GOLDEN_APPLE))
                            waitForEffects(5)
                            player.getCooldown(Material.GOLDEN_APPLE).shouldBeGreaterThanOrEqual(185)
                            player.setCooldown(Material.GOLDEN_APPLE, foreign)
                            waitForEffects(3)
                            val remaining = player.getCooldown(Material.GOLDEN_APPLE)
                            remaining.shouldBeGreaterThanOrEqual(foreign - 5)
                            module.reload()
                            player.getCooldown(Material.GOLDEN_APPLE) shouldBe remaining
                            player.setCooldown(Material.GOLDEN_APPLE, 0)
                        }
                    }
                }

            test("disabled modeset releases owned cooldown after native ticks")
                .config(enabledOrReasonIf = { modernMaterials }) {
                    withConfig {
                        configureIndicator(shared = true)
                        callConsume(ItemStack(Material.GOLDEN_APPLE))
                        waitForEffects(5)
                        setModeset("missing")
                        module.onModesetChange(player)
                        player.getCooldown(Material.GOLDEN_APPLE) shouldBe 0
                        player.getCooldown(Material.ENCHANTED_GOLDEN_APPLE) shouldBe 0
                    }
                }

            test("late cancellation creates no native restriction")
                .config(enabledOrReasonIf = { modernMaterials }) {
                    withConfig {
                        configureIndicator(shared = true)
                        val event = callConsume(ItemStack(Material.GOLDEN_APPLE))
                        event.isCancelled = true
                        waitForEffects()
                        player.getCooldown(Material.GOLDEN_APPLE) shouldBe 0
                        player.getCooldown(Material.ENCHANTED_GOLDEN_APPLE) shouldBe 0
                    }
                }

            test("zero durations create no native restriction")
                .config(enabledOrReasonIf = { modernMaterials }) {
                    withConfig {
                        configureIndicator(shared = true)
                        ocm.config.set("old-golden-apples.cooldown.normal", 0)
                        ocm.config.set("old-golden-apples.cooldown.enchanted", 0)
                        module.reload()
                        callConsume(ItemStack(Material.GOLDEN_APPLE))
                        waitForEffects()
                        player.getCooldown(Material.GOLDEN_APPLE) shouldBe 0
                        player.getCooldown(Material.ENCHANTED_GOLDEN_APPLE) shouldBe 0
                    }
                }

            test("wall clock expiry clears native restriction after a stalled tick and stops task")
                .config(enabledOrReasonIf = { modernMaterials }) {
                    withConfig {
                        configureIndicator(shared = false)
                        ocm.config.set("old-golden-apples.cooldown.normal", 1)
                        module.reload()
                        callConsume(ItemStack(Material.GOLDEN_APPLE))
                        waitForEffects()
                        player.getCooldown(Material.GOLDEN_APPLE).shouldBeGreaterThanOrEqual(15)
                        // Deliberately stall the server: native cooldown ticks cannot advance with wall time.
                        Thread.sleep(1100)
                        waitForEffects()
                        player.getCooldown(Material.GOLDEN_APPLE) shouldBe 0
                        val task = ModuleGoldenApple::class.java.getDeclaredField("itemCooldownTask")
                        task.isAccessible = true
                        task.get(module) shouldBe null
                    }
                }

            test("large durations cannot overflow into an absent native cooldown")
                .config(enabledOrReasonIf = { modernMaterials }) {
                    withConfig {
                        configureIndicator(shared = false)
                        ocm.config.set("old-golden-apples.cooldown.normal", Long.MAX_VALUE)
                        module.reload()
                        callConsume(ItemStack(Material.GOLDEN_APPLE))
                        waitForEffects()
                        player.getCooldown(Material.GOLDEN_APPLE).shouldBeGreaterThanOrEqual(1000000)
                        module.reload()
                        player.getCooldown(Material.GOLDEN_APPLE) shouldBe 0
                    }
                }

            test("player override releases owned cooldown on the shared task")
                .config(enabledOrReasonIf = { modernMaterials }) {
                    withConfig {
                        configureIndicator(shared = true)
                        callConsume(ItemStack(Material.GOLDEN_APPLE))
                        waitForEffects()
                        try {
                            kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerModuleOverrides.setOverride(
                                player,
                                "old-golden-apples",
                                kernitus.plugin.OldCombatMechanics.api.PlayerModuleOverride.FORCE_DISABLED,
                            )
                            waitForEffects()
                            player.getCooldown(Material.GOLDEN_APPLE) shouldBe 0
                            player.getCooldown(Material.ENCHANTED_GOLDEN_APPLE) shouldBe 0
                        } finally {
                            kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerModuleOverrides.clearOverride(
                                player,
                                "old-golden-apples",
                            )
                        }
                    }
                }

            test("registered shutdown callback clears owned cooldown and pending consumption")
                .config(enabledOrReasonIf = { modernMaterials }) {
                    withConfig {
                        configureIndicator(shared = true)
                        callConsume(ItemStack(Material.GOLDEN_APPLE))
                        waitForEffects()
                        val field = OCMMain::class.java.getDeclaredField("disableListeners")
                        field.isAccessible = true
                        val callbacks = field.get(ocm) as List<*>
                        val callback =
                            callbacks.filterIsInstance<Runnable>().single { candidate ->
                                candidate.javaClass.declaredFields.any { captured ->
                                    captured.isAccessible = true
                                    captured.get(candidate) === module
                                }
                            }
                        callback.run()
                        player.getCooldown(Material.GOLDEN_APPLE) shouldBe 0
                        player.getCooldown(Material.ENCHANTED_GOLDEN_APPLE) shouldBe 0
                        module.reload()
                        callConsume(ItemStack(Material.GOLDEN_APPLE))
                        callback.run()
                        waitForEffects()
                        player.getCooldown(Material.GOLDEN_APPLE) shouldBe 0
                    }
                }

            test("world transfer with unchanged modeset releases an inactive indicator immediately")
                .config(enabledOrReasonIf = { modernMaterials }) {
                    withConfig {
                        configureIndicator(shared = true)
                        val origin = player.location.clone()
                        val destination = Bukkit.getWorlds().first { it.uid != player.world.uid }
                        val data =
                            kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage.getPlayerData(
                                player.uniqueId,
                            )
                        val previous = data.getModesetForWorld(destination.uid)
                        data.setModesetForWorld(destination.uid, "old")
                        try {
                            callConsume(ItemStack(Material.GOLDEN_APPLE))
                            waitForEffects()
                            player.getCooldown(Material.GOLDEN_APPLE).shouldBeGreaterThanOrEqual(190)
                            kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerModuleOverrides.setOverride(
                                player,
                                "old-golden-apples",
                                kernitus.plugin.OldCombatMechanics.api.PlayerModuleOverride.FORCE_DISABLED,
                            )
                            player.teleport(Location(destination, 0.5, 100.0, 0.5)) shouldBe true
                            player.world.uid shouldBe destination.uid
                            player.getCooldown(Material.GOLDEN_APPLE) shouldBe 0
                            player.getCooldown(Material.ENCHANTED_GOLDEN_APPLE) shouldBe 0
                        } finally {
                            player.teleport(origin)
                            kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerModuleOverrides.clearOverride(
                                player,
                                "old-golden-apples",
                            )
                            if (previous == null) {
                                data.clearModesetForWorld(destination.uid)
                            } else {
                                data.setModesetForWorld(destination.uid, previous)
                            }
                        }
                    }
                }

            test("native player quit releases the indicator")
                .config(enabledOrReasonIf = { modernMaterials }) {
                    withConfig {
                        configureIndicator(shared = true)
                        callConsume(ItemStack(Material.GOLDEN_APPLE))
                        waitForEffects()
                        val previousPlayer = player
                        try {
                            fakePlayer.removePlayer()
                            previousPlayer.getCooldown(Material.GOLDEN_APPLE) shouldBe 0
                            previousPlayer.getCooldown(Material.ENCHANTED_GOLDEN_APPLE) shouldBe 0
                        } finally {
                            fakePlayer = FakePlayer(testPlugin)
                            fakePlayer.spawn(Location(Bukkit.getWorld("world"), 0.0, 100.0, 0.0))
                            player = checkNotNull(Bukkit.getPlayer(fakePlayer.uuid))
                            player.gameMode = GameMode.SURVIVAL
                            player.isOp = true
                            setModeset("old")
                        }
                    }
                }

            val cooldownEventClass =
                runCatching {
                    Class
                        .forName("io.papermc.paper.event.player.PlayerItemCooldownEvent")
                        .asSubclass(org.bukkit.event.Event::class.java)
                }.getOrNull()
            test("native cooldown listeners retain changed and cancelled writes including reentrant reload")
                .config(enabledOrReasonIf = {
                    Enabled(cooldownEventClass != null, "Native item cooldown event is absent")
                }) {
                    withConfig {
                        for (action in listOf("replace", "cancel", "reload")) {
                            configureIndicator(shared = false)
                            val listener = object : org.bukkit.event.Listener {}
                            var intercepted = false
                            Bukkit.getPluginManager().registerEvent(
                                cooldownEventClass!!,
                                listener,
                                org.bukkit.event.EventPriority.HIGHEST,
                                { _, event ->
                                    val ticks = event.javaClass.getMethod("getCooldown").invoke(event) as Int
                                    if (!intercepted && ticks > 0) {
                                        intercepted = true
                                        when (action) {
                                            "replace" -> {
                                                event.javaClass
                                                    .getMethod("setCooldown", Int::class.javaPrimitiveType)
                                                    .invoke(event, 900)
                                            }

                                            "cancel" -> {
                                                (event as org.bukkit.event.Cancellable).isCancelled = true
                                            }

                                            "reload" -> {
                                                module.reload()
                                            }
                                        }
                                    }
                                },
                                testPlugin,
                            )
                            try {
                                callConsume(ItemStack(Material.GOLDEN_APPLE))
                                waitForEffects(3)
                                intercepted shouldBe true
                                if (action == "replace") {
                                    player.getCooldown(Material.GOLDEN_APPLE).shouldBeGreaterThanOrEqual(890)
                                    module.reload()
                                    player.getCooldown(Material.GOLDEN_APPLE).shouldBeGreaterThanOrEqual(890)
                                } else {
                                    player.getCooldown(Material.GOLDEN_APPLE) shouldBe 0
                                }
                            } finally {
                                org.bukkit.event.HandlerList
                                    .unregisterAll(listener)
                                player.setCooldown(Material.GOLDEN_APPLE, 0)
                            }
                        }
                    }
                }

            test("disabled indicator leaves item cooldowns untouched")
                .config(enabledOrReasonIf = { modernMaterials }) {
                    withConfig {
                        configureIndicator(shared = true, show = false)
                        callConsume(ItemStack(Material.GOLDEN_APPLE)).isCancelled shouldBe false

                        player.getCooldown(Material.GOLDEN_APPLE) shouldBe 0
                        player.getCooldown(Material.ENCHANTED_GOLDEN_APPLE) shouldBe 0
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
