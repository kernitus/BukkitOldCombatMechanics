/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics

import com.cryptomorin.xseries.XAttribute
import com.cryptomorin.xseries.XEnchantment
import com.cryptomorin.xseries.XMaterial
import com.cryptomorin.xseries.XPotion
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.Enabled
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import kernitus.plugin.OldCombatMechanics.api.OldCombatMechanicsAPI
import kernitus.plugin.OldCombatMechanics.utilities.Config
import kernitus.plugin.OldCombatMechanics.utilities.damage.DamageUtils
import kernitus.plugin.OldCombatMechanics.utilities.damage.OCMEntityDamageByEntityEvent
import kernitus.plugin.OldCombatMechanics.utilities.potions.PotionEffects
import kernitus.plugin.OldCombatMechanics.utilities.potions.WeaknessCompensation
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector
import kernitus.plugin.OldCombatMechanics.utilities.reflection.VersionCompatUtils
import kotlinx.coroutines.suspendCancellableCoroutine
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.attribute.AttributeModifier
import org.bukkit.entity.Cow
import org.bukkit.entity.Item
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionEffect
import org.bukkit.util.Vector
import java.io.File
import kotlin.coroutines.resume

/** Native attacks are invoked once, including attempts suppressed by immunity or cancellation. */
@OptIn(ExperimentalKotest::class)
class WeaponDamageBaselineIntegrationTest :
    FunSpec({
        val plugin = JavaPlugin.getPlugin(OCMTestMain::class.java)
        val ocm = JavaPlugin.getPlugin(OCMMain::class.java)
        extensions(MainThreadDispatcherExtension(plugin))

        val sweepingEdgeAvailable = XEnchantment.SWEEPING_EDGE.get() != null
        val sweepingEdgeReason = "Native Sweeping Edge enchantment is absent on this server"

        suspend fun ticks(count: Long) {
            suspendCancellableCoroutine<Unit> { continuation ->
                val task = Bukkit.getScheduler().runTaskLater(plugin, Runnable { continuation.resume(Unit) }, count)
                continuation.invokeOnCancellation { task.cancel() }
            }
        }

        class Fixture {
            val originalConfig = ocm.config.saveToString()
            val fake = FakePlayer(plugin)
            val world = checkNotNull(Bukkit.getWorld("world"))
            val location = Location(world, 48.0, 150.0, 0.0)
            val wasLoaded = world.isChunkLoaded(location.blockX shr 4, location.blockZ shr 4)
            val chunk = location.chunk
            val listeners = mutableListOf<Listener>()
            val targets = mutableListOf<Cow>()
            lateinit var attacker: Player
            val damages = mutableListOf<Double>()
            val rawDamages = mutableListOf<Double>()
            var cancelled = false
            var foreignAdjustment = 0.0
            var diagnosticLabel: String? = null

            fun diagnostic(
                phase: String,
                event: OCMEntityDamageByEntityEvent? = null,
            ) {
                val label = diagnosticLabel ?: return
                File(plugin.dataFolder, "weapon-weakness-diagnostics.txt").appendText(
                    "$label phase=$phase attribute=${attacker.getAttribute(
                        checkNotNull(XAttribute.ATTACK_DAMAGE.get()),
                    )?.value} " +
                        "compensated=${WeaknessCompensation.hasModifier(attacker)} " +
                        "amplifier=${PotionEffects.get(
                            attacker,
                            org.bukkit.potion.PotionEffectType.WEAKNESS,
                        ).orElse(null)?.amplifier} " +
                        "raw=${event?.rawDamage} base=${event?.baseDamage} weakness=${event?.weaknessModifier}\n",
                )
            }

            fun configure(
                enabled: Boolean = true,
                extra: List<String> = emptyList(),
            ) {
                val modules = (if (enabled) listOf("old-tool-damage") else emptyList()) + extra
                ocm.config.set("always_enabled_modules", emptyList<String>())
                ocm.config.set(
                    "disabled_modules",
                    ModuleLoader.getConfigurableModuleNames().filterNot { it in modules },
                )
                ocm.config.set("modesets", null)
                ocm.config.set("modesets.old", modules)
                ocm.config.set("modesets.new", emptyList<String>())
                ocm.config.set("old-tool-damage.tooltip.enabled", false)
                reload()
            }

            fun reload() {
                ocm.saveConfig()
                Config.reload()
            }

            fun modeset(name: String) {
                checkNotNull(Bukkit.getServicesManager().load(OldCombatMechanicsAPI::class.java))
                    .setModesetForPlayer(attacker, name)
            }

            fun start() {
                configure()
                chunk.load()
                fake.spawn(location)
                attacker = checkNotNull(Bukkit.getPlayer(fake.uuid))
                attacker.gameMode = GameMode.SURVIVAL
                attacker.inventory.clear()
                attacker.isSprinting = false
                attacker.fallDistance = 0f
                attacker.isFlying = false
                attacker.activePotionEffects.forEach { attacker.removePotionEffect(it.type) }
                checkNotNull(attacker.getAttribute(checkNotNull(XAttribute.ATTACK_SPEED.get()))).baseValue = 1000.0
                modeset("old")
                val listener =
                    object : Listener {
                        @EventHandler(priority = EventPriority.LOWEST)
                        fun beforeOcm(event: OCMEntityDamageByEntityEvent) {
                            if (event.damager.uniqueId == attacker.uniqueId && event.damagee in targets) {
                                diagnostic("before OCM modules", event)
                            }
                        }

                        @EventHandler(priority = EventPriority.MONITOR)
                        fun afterOcm(event: OCMEntityDamageByEntityEvent) {
                            if (event.damager.uniqueId == attacker.uniqueId && event.damagee in targets) {
                                diagnostic("after OCM modules", event)
                            }
                        }

                        @EventHandler
                        fun captureRaw(event: OCMEntityDamageByEntityEvent) {
                            if (event.damager.uniqueId == attacker.uniqueId && event.damagee in targets) {
                                rawDamages.add(event.rawDamage)
                            }
                        }

                        @EventHandler(priority = EventPriority.HIGHEST)
                        fun adjust(event: EntityDamageByEntityEvent) {
                            if (event.damager.uniqueId != attacker.uniqueId || event.entity !in targets) return
                            if (cancelled) event.isCancelled = true
                            if (foreignAdjustment != 0.0) event.damage += foreignAdjustment
                        }

                        @EventHandler(priority = EventPriority.MONITOR)
                        fun capture(event: EntityDamageByEntityEvent) {
                            if (event.damager.uniqueId == attacker.uniqueId && event.entity in targets) {
                                damages.add(event.finalDamage)
                            }
                        }
                    }
                listeners.add(listener)
                Bukkit.getPluginManager().registerEvents(listener, plugin)
            }

            fun target(): Cow =
                world.spawn(location.clone().add(1.0, 0.0, 0.0), Cow::class.java).apply {
                    setAI(false)
                    try {
                        setGravity(false)
                    } catch (_: NoSuchMethodError) {
                        // Older cows remain native entities; attacks complete before their next tick.
                    }
                    checkNotNull(getAttribute(checkNotNull(XAttribute.MAX_HEALTH.get()))).baseValue = 100.0
                    health = 40.0
                    isInvulnerable = false
                    targets.add(this)
                }

            fun attack(
                target: LivingEntity,
                source: Player = attacker,
            ) {
                source.isSprinting = false
                source.fallDistance = 0f
                try {
                    source.attack(target)
                } catch (_: NoSuchMethodError) {
                    // Resolve one native method before invoking it. Never retry a suppressed attack.
                    val handle = source.javaClass.getMethod("getHandle").invoke(source)
                    val victim = target.javaClass.getMethod("getHandle").invoke(target)
                    val method =
                        Reflector.getMethodAssignable(handle.javaClass, "attack", victim.javaClass)
                            ?: error("Native player attack method unavailable")
                    method.invoke(handle, victim)
                }
            }

            suspend fun equip(item: ItemStack) {
                checkNotNull(attacker.getAttribute(checkNotNull(XAttribute.ATTACK_SPEED.get()))).baseValue = 1000.0
                attacker.inventory.setItemInMainHand(item)
                // Native entity ticking applies held-item attributes. No test attribute injection.
                ticks(3)
                withClue("Full native recharge required for the ordinary damage fixture") {
                    DamageUtils.getAttackCooldown.apply(attacker).toDouble() shouldBe (1.0 plusOrMinus 0.001)
                }
            }

            suspend fun damage(item: ItemStack): Double {
                equip(item)
                val victim = target()
                val before = victim.health
                val count = damages.size
                diagnostic("before native attack")
                attack(victim)
                damages.size shouldBe count + 1
                val loss = before - victim.health
                diagnosticLabel?.let {
                    File(plugin.dataFolder, "weapon-weakness-diagnostics.txt").appendText(
                        "$it final=${damages.last()} healthLoss=$loss\n",
                    )
                }
                withClue("weapon=${item.type} captured=${damages.last()} native health loss=$loss") {
                    loss shouldBe (damages.last() plusOrMinus 0.0001)
                }
                return loss
            }

            fun close() {
                listeners.forEach { HandlerList.unregisterAll(it) }
                targets.forEach { it.remove() }
                if (Bukkit.getPlayer(fake.uuid) != null) fake.removePlayer()
                if (!wasLoaded) chunk.unload()
                ocm.config.loadFromString(originalConfig)
                reload()
            }
        }

        suspend fun fixture(block: suspend Fixture.() -> Unit) {
            val fixture = Fixture()
            try {
                fixture.start()
                fixture.block()
            } finally {
                fixture.close()
            }
        }

        fun weapon(name: String): ItemStack = checkNotNull(XMaterial.matchXMaterial(name).orElseThrow().parseItem())

        fun absorption(entity: LivingEntity): Double =
            try {
                entity.absorptionAmount
            } catch (_: NoSuchMethodError) {
                VersionCompatUtils.getAbsorptionAmount(entity).toDouble()
            }

        fun setAbsorption(
            entity: LivingEntity,
            amount: Double,
        ) {
            // Newer native entities clamp absorption to this optional attribute.
            XAttribute.MAX_ABSORPTION.get()?.let {
                checkNotNull(entity.getAttribute(it)) { "Native absorption capacity attribute unavailable" }.baseValue =
                    amount
            }
            try {
                entity.absorptionAmount = amount
            } catch (_: NoSuchMethodError) {
                val handle = Reflector.invokeMethod<Any>(Reflector.getMethod(entity.javaClass, "getHandle"), entity)
                val setter = checkNotNull(Reflector.getMethod(handle.javaClass, "setAbsorptionHearts", 1))
                Reflector.invokeMethod<Any?>(setter, handle, amount.toFloat())
            }
        }

        data class SweepHit(
            val incoming: Double,
            val previousDamage: Double,
            val immunityTicks: Int,
            val appliedBase: Double,
            val bukkitBase: Double,
            val nativeOverdamageReduction: Double,
            val finalDamage: Double,
            val cancelled: Boolean,
            val health: Double,
            val absorption: Double,
        )

        val nativeInvulnerabilityModifier =
            EntityDamageEvent.DamageModifier
                .values()
                .firstOrNull { it.name == "INVULNERABILITY_REDUCTION" }

        data class SweepResult(
            val primary: Double,
            val secondary: Double,
            val nativeAttribute: Double,
            val hits: List<SweepHit>,
            val healthAfterAttempts: List<Double>,
            val lastDamageBeforeAttempts: List<Double>,
        )

        suspend fun Fixture.sweep(
            item: ItemStack = weapon("DIAMOND_SWORD"),
            speed: Double = 4.0,
            base: Double = 1.0,
            effect: PotionEffect? = null,
            matchingAttribute: Double? = null,
            victimFactory: () -> LivingEntity = { target() },
            prepare: suspend (LivingEntity) -> Unit = {},
            custom: (OCMEntityDamageByEntityEvent) -> Unit = {},
            foreign: (EntityDamageByEntityEvent) -> Unit = {},
            repeat: Boolean = false,
            followUpItems: List<ItemStack> = emptyList(),
            nativeWeakerSuppression: Boolean = false,
        ): SweepResult {
            targets.forEach { it.remove() }
            targets.clear()
            attacker.activePotionEffects.forEach { attacker.removePotionEffect(it.type) }
            val floor = location.clone().subtract(0.0, 1.0, 0.0).block
            val previousFloor = floor.state
            val attribute = checkNotNull(attacker.getAttribute(checkNotNull(XAttribute.ATTACK_DAMAGE.get())))
            val originalBase = attribute.baseValue
            var secondary: LivingEntity? = null
            var observed = 0
            var incoming: Double? = null
            var previousDamage = 0.0
            var immunityTicks = 0
            val hits = mutableListOf<SweepHit>()
            val healthAfterAttempts = mutableListOf<Double>()
            val lastDamageBeforeAttempts = mutableListOf<Double>()
            val listener =
                object : Listener {
                    @EventHandler(priority = EventPriority.LOWEST)
                    fun nativeEvent(event: EntityDamageByEntityEvent) {
                        if (event.entity != secondary || event.damager != attacker || incoming != null) return
                        // With every configurable damage module disabled, the native control has no custom OCM event.
                        val victim = checkNotNull(secondary)
                        previousDamage = victim.lastDamage
                        immunityTicks = victim.noDamageTicks
                        incoming =
                            event.damage +
                            if ((
                                    nativeInvulnerabilityModifier == null ||
                                        !event.isApplicable(nativeInvulnerabilityModifier)
                                ) &&
                                immunityTicks > victim.maximumNoDamageTicks / 2
                            ) {
                                previousDamage
                            } else {
                                0.0
                            }
                    }

                    @EventHandler(priority = EventPriority.HIGHEST)
                    fun customEvent(event: OCMEntityDamageByEntityEvent) {
                        if (event.damagee != secondary || event.damager != attacker) return
                        event.isNativeSweepAttack shouldBe true
                        event.strengthModifier shouldBe 0.0
                        event.weaknessModifier shouldBe 0.0
                        incoming = event.rawDamage
                        previousDamage = checkNotNull(secondary).lastDamage
                        immunityTicks = checkNotNull(secondary).noDamageTicks
                        custom(event)
                    }

                    @EventHandler(priority = EventPriority.HIGHEST)
                    fun foreignEvent(event: EntityDamageByEntityEvent) {
                        if (event.entity == secondary && event.damager == attacker) foreign(event)
                    }

                    @EventHandler(priority = EventPriority.MONITOR)
                    fun capture(event: EntityDamageByEntityEvent) {
                        if (event.entity != secondary || event.damager != attacker) return
                        event.cause.name shouldBe "ENTITY_SWEEP_ATTACK"
                        observed++
                        val victim = checkNotNull(secondary)
                        val nativeReduction =
                            nativeInvulnerabilityModifier
                                ?.takeIf { event.isApplicable(it) }
                                ?.let { event.getDamage(it) } ?: 0.0
                        hits.add(
                            SweepHit(
                                checkNotNull(incoming),
                                previousDamage,
                                immunityTicks,
                                event.damage + nativeReduction,
                                event.damage,
                                nativeReduction,
                                event.finalDamage,
                                event.isCancelled,
                                victim.health,
                                absorption(victim),
                            ),
                        )
                    }
                }
            Bukkit.getPluginManager().registerEvents(listener, plugin)
            try {
                floor.type = Material.STONE
                attacker.teleport(location)
                attribute.baseValue = base
                equip(item)
                if (effect != null) attacker.addPotionEffect(effect)
                checkNotNull(attacker.getAttribute(checkNotNull(XAttribute.ATTACK_SPEED.get()))).baseValue = speed
                ticks(30)
                if (matchingAttribute != null) {
                    // Compare real native attacks with the same source attribute, including live potion compensation.
                    attribute.baseValue += matchingAttribute - attribute.value
                    ticks(1)
                }
                check(attacker.isOnGround) { "Native sweep requires a grounded attacker" }
                val primary = target()
                val victim = victimFactory()
                secondary = victim
                prepare(victim)
                ticks(3)
                val nativeAttribute = attribute.value
                lastDamageBeforeAttempts.add(victim.lastDamage)
                attack(primary)
                observed shouldBe 1
                hits[hits.lastIndex] = hits.last().copy(health = victim.health, absorption = absorption(victim))
                healthAfterAttempts.add(victim.health)
                if (repeat) {
                    ticks(2)
                    incoming = null
                    lastDamageBeforeAttempts.add(victim.lastDamage)
                    attack(target())
                    observed shouldBe 2
                    hits[hits.lastIndex] = hits.last().copy(health = victim.health, absorption = absorption(victim))
                    healthAfterAttempts.add(victim.health)
                }
                for ((index, nextItem) in followUpItems.withIndex()) {
                    equip(nextItem)
                    check(
                        victim.noDamageTicks > victim.maximumNoDamageTicks / 2,
                    ) { "Follow-up must remain inside immunity" }
                    val count = observed
                    victim.teleport(location.clone().add(1.0, 0.0, 0.0))
                    victim.velocity = Vector()
                    incoming = null
                    lastDamageBeforeAttempts.add(victim.lastDamage)
                    attack(target())
                    withClue("Follow-up ${nextItem.enchantments} hits=$hits victim=${victim.location}") {
                        observed shouldBe
                            count + if (nativeWeakerSuppression && index == followUpItems.lastIndex) 0 else 1
                    }
                    if (observed >
                        count
                    ) {
                        hits[hits.lastIndex] =
                            hits.last().copy(health = victim.health, absorption = absorption(victim))
                    }
                    healthAfterAttempts.add(victim.health)
                }
                return SweepResult(
                    40.0 - primary.health,
                    40.0 - victim.health,
                    nativeAttribute,
                    hits.toList(),
                    healthAfterAttempts.toList(),
                    lastDamageBeforeAttempts.toList(),
                )
            } finally {
                HandlerList.unregisterAll(listener)
                attribute.baseValue = originalBase
                attacker.activePotionEffects.forEach { attacker.removePotionEffect(it.type) }
                previousFloor.update(true, false)
            }
        }

        fun configKey(item: ItemStack): String =
            item.type.name
                .replace("GOLDEN", "GOLD")
                .replace("WOODEN", "WOOD")
                .replace("SHOVEL", "SPADE")

        fun customWeapon(
            operation: AttributeModifier.Operation,
            amount: Double,
            slot: EquipmentSlot = EquipmentSlot.HAND,
        ): ItemStack {
            val item = weapon("DIAMOND_SWORD")
            val meta = checkNotNull(item.itemMeta)
            val attribute = checkNotNull(XAttribute.ATTACK_DAMAGE.get())
            addAttributeModifierCompat(
                meta,
                attribute,
                createAttributeModifier("foreign-damage", amount, operation, slot),
            )
            addAttributeModifierCompat(
                meta,
                checkNotNull(XAttribute.ATTACK_SPEED.get()),
                createAttributeModifier(
                    "foreign-speed",
                    2.0,
                    AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlot.HAND,
                ),
            )
            item.itemMeta = meta
            // Unsupported ItemMeta APIs must not turn a custom-weapon test into a vanilla-weapon pass.
            check(
                item.itemMeta!!
                    .getAttributeModifiers(
                        attribute,
                    )?.any { it.amount == amount && it.operation == operation } ==
                    true,
            ) {
                "Custom item attribute fixture unavailable on this Bukkit API"
            }
            return item
        }

        val families =
            listOf(
                "WOODEN_SWORD",
                "GOLDEN_SWORD",
                "STONE_SWORD",
                "IRON_SWORD",
                "DIAMOND_SWORD",
                "IRON_AXE",
                "DIAMOND_AXE",
                "IRON_PICKAXE",
                "DIAMOND_PICKAXE",
                "IRON_SHOVEL",
                "DIAMOND_SHOVEL",
                "DIAMOND_HOE",
            )
        for (name in families) {
            for (configured in listOf(4.0, 4.625)) {
                test("native $name uses configured $configured damage") {
                    fixture {
                        val item = weapon(name)
                        ocm.config.set("old-tool-damage.damages.${configKey(item)}", configured)
                        reload()
                        damage(item) shouldBe (configured plusOrMinus 0.001)
                    }
                }
            }
        }

        test("disabled tool damage retains native plain weapon damage") {
            fixture {
                configure(false)
                val item = weapon("DIAMOND_SWORD")
                ocm.config.set("old-tool-damage.damages.DIAMOND_SWORD", 4.625)
                reload()
                damage(item) shouldBe (7.0 plusOrMinus 0.001)
            }
        }

        for ((operation, amount, expected) in listOf(
            Triple(AttributeModifier.Operation.ADD_NUMBER, 11.0, 12.0),
            Triple(AttributeModifier.Operation.ADD_SCALAR, 2.0, 3.0),
            Triple(AttributeModifier.Operation.MULTIPLY_SCALAR_1, 2.0, 3.0),
        )) {
            for (enabled in listOf(true, false)) {
                test("custom $operation item retains damage and modifiers with module enabled $enabled") {
                    fixture {
                        configure(enabled)
                        val item = customWeapon(operation, amount)
                        val originalMeta = item.itemMeta!!.clone()
                        ocm.config.set("old-tool-damage.damages.DIAMOND_SWORD", 4.625)
                        reload()
                        damage(item) shouldBe (expected plusOrMinus 0.001)
                        val heldMeta = attacker.inventory.itemInMainHand.itemMeta!!
                        heldMeta.getAttributeModifiers(checkNotNull(XAttribute.ATTACK_DAMAGE.get())) shouldBe
                            originalMeta.getAttributeModifiers(checkNotNull(XAttribute.ATTACK_DAMAGE.get()))
                        heldMeta.getAttributeModifiers(checkNotNull(XAttribute.ATTACK_SPEED.get())) shouldBe
                            originalMeta.getAttributeModifiers(checkNotNull(XAttribute.ATTACK_SPEED.get()))
                    }
                }
            }
        }

        test("custom offhand damage modifier does not affect a main-hand attack") {
            fixture {
                val item = customWeapon(AttributeModifier.Operation.ADD_NUMBER, 11.0, EquipmentSlot.OFF_HAND)
                damage(item) shouldBe (1.0 plusOrMinus 0.001)
            }
        }

        test("configured damage reload changes native damage without accumulating") {
            fixture {
                val item = weapon("DIAMOND_SWORD")
                for (configured in listOf(4.625, 8.125, 4.625, 4.625)) {
                    ocm.config.set("old-tool-damage.damages.DIAMOND_SWORD", configured)
                    reload()
                    damage(item) shouldBe (configured plusOrMinus 0.001)
                }
            }
        }

        test("native attacks follow attacker modeset across repeated toggles") {
            fixture {
                ocm.config.set("old-tool-damage.damages.DIAMOND_SWORD", 4.625)
                reload()
                for (name in listOf("old", "new", "old", "new", "old")) {
                    modeset(name)
                    damage(weapon("DIAMOND_SWORD")) shouldBe ((if (name == "old") 4.625 else 7.0) plusOrMinus 0.001)
                }
            }
        }

        test("hotbar transitions preserve foreign metadata and custom attributes") {
            fixture {
                val item = customWeapon(AttributeModifier.Operation.ADD_NUMBER, 11.0)
                val meta = item.itemMeta!!
                meta.setDisplayName("Foreign weapon")
                meta.lore = listOf("Foreign lore", "Colour: blue")
                item.itemMeta = meta
                item.addUnsafeEnchantment(checkNotNull(XEnchantment.UNBREAKING.get()), 2)
                @Suppress("DEPRECATION")
                item.durability = 5
                val original = item.clone()
                attacker.inventory.setItem(0, item)
                attacker.inventory.setItem(1, ItemStack(Material.STICK))
                for (slot in listOf(1, 0, 1, 0)) {
                    val previous = attacker.inventory.heldItemSlot
                    attacker.inventory.heldItemSlot = slot
                    // Synthetic notification, followed by native inventory ticking and attacks.
                    Bukkit.getPluginManager().callEvent(PlayerItemHeldEvent(attacker, previous, slot))
                    reload()
                    ticks(3)
                    attacker.inventory.getItem(0) shouldBe original
                }
                damage(attacker.inventory.itemInMainHand) shouldBe (12.0 plusOrMinus 0.001)
            }
        }

        for ((first, second) in listOf(4.0 to 4.0, 8.0 to 4.0, 4.0 to 8.0)) {
            test("native immunity retains correct health for configured hits $first then $second") {
                fixture {
                    val victim = target()
                    val item = weapon("DIAMOND_SWORD")
                    ocm.config.set("old-tool-damage.damages.DIAMOND_SWORD", first)
                    reload()
                    equip(item)
                    attack(victim)
                    victim.health shouldBe ((40.0 - first) plusOrMinus 0.001)
                    check(victim.noDamageTicks > victim.maximumNoDamageTicks / 2)
                    ocm.config.set("old-tool-damage.damages.DIAMOND_SWORD", second)
                    reload()
                    // A different material forces a different native attack amount without waiting out immunity.
                    ocm.config.set("old-tool-damage.damages.IRON_AXE", second)
                    reload()
                    checkNotNull(attacker.getAttribute(checkNotNull(XAttribute.ATTACK_SPEED.get()))).baseValue = 1000.0
                    attacker.inventory.setItemInMainHand(weapon("IRON_AXE"))
                    ticks(2)
                    check(victim.noDamageTicks > victim.maximumNoDamageTicks / 2)
                    attack(victim)
                    victim.health shouldBe ((40.0 - maxOf(first, second)) plusOrMinus 0.001)
                }
            }
        }

        test("cancelled native hit causes no health loss and does not poison the next attack") {
            fixture {
                val victim = target()
                ocm.config.set("old-tool-damage.damages.DIAMOND_SWORD", 4.625)
                reload()
                equip(weapon("DIAMOND_SWORD"))
                cancelled = true
                attack(victim)
                victim.health shouldBe 40.0
                cancelled = false
                ticks(2)
                attack(victim)
                victim.health shouldBe (35.375 plusOrMinus 0.001)
            }
        }

        for (adjustment in listOf(-1.0, 2.0)) {
            test("foreign HIGHEST damage adjustment $adjustment survives configured weapon damage") {
                fixture {
                    ocm.config.set("old-tool-damage.damages.DIAMOND_SWORD", 4.625)
                    reload()
                    foreignAdjustment = adjustment
                    damage(weapon("DIAMOND_SWORD")) shouldBe ((4.625 + adjustment) plusOrMinus 0.001)
                }
            }
        }

        for ((effect, expected) in listOf("strength" to 7.625, "weakness" to 0.625)) {
            test("native $effect composes with fractional configured weapon damage") {
                fixture {
                    diagnosticLabel = effect
                    ocm.config.set("old-tool-damage.damages.DIAMOND_SWORD", 4.625)
                    reload()
                    val type =
                        if (effect ==
                            "strength"
                        ) {
                            checkNotNull(XPotion.STRENGTH.get())
                        } else {
                            org.bukkit.potion.PotionEffectType.WEAKNESS
                        }
                    attacker.addPotionEffect(PotionEffect(type, 200, 0))
                    val actual = damage(weapon("DIAMOND_SWORD"))
                    withClue("effect=$effect OCM raw=$rawDamages final=$damages health loss=$actual") {
                        actual shouldBe (expected plusOrMinus 0.001)
                    }
                }
            }
        }

        data class WeaknessControl(
            val label: String,
            val enabled: Boolean,
            val extra: List<String>,
            val expected: Double,
        )

        for ((label, enabled, extra, expected) in listOf(
            WeaknessControl("disabled modules", false, emptyList(), 3.0),
            WeaknessControl("shared damage listener", false, listOf("old-critical-hits"), 3.0),
            WeaknessControl("legacy potion effects", true, listOf("old-potion-effects"), 4.125),
        )) {
            test("native weakness diagnostic control $label") {
                fixture {
                    diagnosticLabel = label
                    configure(enabled, extra)
                    ocm.config.set("old-tool-damage.damages.DIAMOND_SWORD", 4.625)
                    ocm.config.set("old-potion-effects.weakness.modifier", -0.5)
                    ocm.config.set("old-potion-effects.weakness.multiplier", false)
                    reload()
                    attacker.addPotionEffect(PotionEffect(org.bukkit.potion.PotionEffectType.WEAKNESS, 200, 0))
                    damage(weapon("DIAMOND_SWORD")) shouldBe (expected plusOrMinus 0.001)
                }
            }
        }

        test("native sharpness composes with fractional configured weapon damage") {
            fixture {
                ocm.config.set("old-tool-damage.damages.DIAMOND_SWORD", 4.625)
                ocm.config.set("old-tool-damage.old-sharpness", true)
                reload()
                val item = weapon("DIAMOND_SWORD")
                item.addUnsafeEnchantment(checkNotNull(XEnchantment.SHARPNESS.get()), 2)
                damage(item) shouldBe (7.125 plusOrMinus 0.001)
            }
        }
        for (effectName in listOf("plain", "default Strength", "additive Strength", "Weakness")) {
            for (enchanted in listOf(false, true)) {
                test(
                    "native sweep preserves $effectName damage with ${if (enchanted) "enchanted fast" else "plain slow"} attacks",
                ) {
                    fixture {
                        configure(true, listOf("old-potion-effects", "old-critical-hits"))
                        ocm.config.set("old-tool-damage.damages.DIAMOND_SWORD", 4.625)
                        ocm.config.set("old-tool-damage.old-sharpness", true)
                        ocm.config.set(
                            "old-potion-effects.strength.modifier",
                            if (effectName ==
                                "additive Strength"
                            ) {
                                4.0
                            } else {
                                1.3
                            },
                        )
                        ocm.config.set("old-potion-effects.strength.multiplier", effectName != "additive Strength")
                        ocm.config.set("old-potion-effects.strength.addend", true)
                        ocm.config.set("old-potion-effects.weakness.modifier", -0.5)
                        ocm.config.set("old-potion-effects.weakness.multiplier", false)
                        reload()
                        val item = weapon("DIAMOND_SWORD")
                        if (enchanted) {
                            item.addUnsafeEnchantment(checkNotNull(XEnchantment.SWEEPING_EDGE.get()), 2)
                            item.addUnsafeEnchantment(checkNotNull(XEnchantment.SHARPNESS.get()), 2)
                        }
                        val effect =
                            when (effectName) {
                                "default Strength", "additive Strength" -> {
                                    PotionEffect(
                                        checkNotNull(XPotion.STRENGTH.get()),
                                        200,
                                        0,
                                    )
                                }

                                "Weakness" -> {
                                    PotionEffect(checkNotNull(XPotion.WEAKNESS.get()), 200, 0)
                                }

                                else -> {
                                    null
                                }
                            }
                        val speed = if (enchanted) 16.0 else 4.0
                        val enabled = sweep(item, speed, effect = effect)
                        val primary =
                            when (effectName) {
                                "default Strength" -> 4.625 * 2.3
                                "additive Strength" -> 8.625
                                "Weakness" -> 4.125
                                else -> 4.625
                            } + if (enchanted) 2.5 else 0.0
                        enabled.primary shouldBe (primary plusOrMinus 0.001)
                        configure(false)
                        val control = sweep(item, speed, effect = effect, matchingAttribute = enabled.nativeAttribute)
                        withClue("Native control=$control configured=$enabled") {
                            enabled.secondary shouldBe (control.secondary plusOrMinus 0.001)
                            if (!enchanted) enabled.secondary shouldBe (1.0 plusOrMinus 0.001)
                        }
                    }
                }
            }
        }

        test("native sweep equal to a weapon baseline retains the incoming amount") {
            fixture {
                configure()
                ocm.config.set("old-tool-damage.damages.DIAMOND_SWORD", 4.625)
                reload()
                val item = weapon("DIAMOND_SWORD")
                item.addUnsafeEnchantment(checkNotNull(XEnchantment.SWEEPING_EDGE.get()), 3)
                val enabled = sweep(item, base = 2.0)
                configure(false)
                val control = sweep(item, base = 2.0)
                enabled.secondary shouldBe (control.secondary plusOrMinus 0.001)
                enabled.secondary shouldBe (7.0 plusOrMinus 0.001)
                enabled.primary shouldBe (8.0 plusOrMinus 0.001)
            }
        }

        for (adjustment in listOf(
            "custom base",
            "custom cancellation",
            "Bukkit damage",
            "Bukkit cancellation",
            "sword cancellation",
        )) {
            test("native sweep preserves $adjustment handling") {
                fixture {
                    configure(
                        true,
                        if (adjustment ==
                            "sword cancellation"
                        ) {
                            listOf("disable-sword-sweep")
                        } else {
                            emptyList()
                        },
                    )
                    ocm.config.set("old-tool-damage.damages.DIAMOND_SWORD", 4.625)
                    reload()
                    val result =
                        sweep(
                            custom = {
                                if (adjustment == "custom base") it.baseDamage += 2.0
                                if (adjustment == "custom cancellation") {
                                    it.baseDamage += 2.0
                                    // Cancelling the custom event leaves the original Bukkit damage unchanged.
                                    it.isCancelled = true
                                }
                            },
                            foreign = {
                                if (adjustment == "Bukkit damage") it.damage += 2.0
                                if (adjustment == "Bukkit cancellation") it.isCancelled = true
                            },
                        )
                    result.primary shouldBe (4.625 plusOrMinus 0.001)
                    val expected =
                        when (adjustment) {
                            "custom base", "Bukkit damage" -> 3.0
                            "Bukkit cancellation", "sword cancellation" -> 0.0
                            else -> 1.0
                        }
                    result.secondary shouldBe (expected plusOrMinus 0.001)
                }
            }
        }

        test("native sweep retains armour and Resistance defence") {
            fixture {
                configure(true, listOf("old-armour-strength"))
                val result =
                    sweep(prepare = {
                        it.equipment!!.chestplate = weapon("DIAMOND_CHESTPLATE")
                        it.addPotionEffect(PotionEffect(checkNotNull(XPotion.RESISTANCE.get()), 200, 0))
                    })
                result.secondary shouldBe (0.544 plusOrMinus 0.001)
            }
        }

        test("native sweep retains equal-hit immunity") {
            fixture {
                configure()
                sweep(speed = 1000.0, repeat = true).secondary shouldBe (1.0 plusOrMinus 0.001)
            }
        }

        for (amount in listOf(0.5, 2.0)) {
            test("native sweep consumes absorption $amount before health") {
                fixture {
                    val results = mutableListOf<SweepResult>()
                    for (enabled in listOf(false, true)) {
                        configure(enabled)
                        val result =
                            sweep(prepare = {
                                setAbsorption(it, amount)
                                absorption(it) shouldBe (amount plusOrMinus 0.001)
                            })
                        result.hits.size shouldBe 1
                        val hit = result.hits.single()
                        withClue("enabled=$enabled absorption=$amount result=$result") {
                            hit.incoming shouldBe (1.0 plusOrMinus 0.001)
                        }
                        hit.cancelled shouldBe false
                        hit.absorption shouldBe (maxOf(0.0, amount - 1.0) plusOrMinus 0.001)
                        val expectedLoss = maxOf(0.0, 1.0 - amount)
                        hit.finalDamage shouldBe (expectedLoss plusOrMinus 0.001)
                        result.secondary shouldBe (expectedLoss plusOrMinus 0.001)
                        results.add(result)
                    }
                    results[1].secondary shouldBe (results[0].secondary plusOrMinus 0.001)
                    results[1].hits.single().absorption shouldBe (results[0].hits.single().absorption plusOrMinus 0.001)
                }
            }
        }

        test("native sweep applies increasing overdamage then rejects a weaker hit")
            .config(enabledOrReasonIf = { Enabled(sweepingEdgeAvailable, sweepingEdgeReason) }) {
                fixture {
                    val stronger =
                        weapon("DIAMOND_SWORD").apply {
                            addUnsafeEnchantment(checkNotNull(XEnchantment.SWEEPING_EDGE.get()), 3)
                        }
                    val results = mutableListOf<SweepResult>()
                    for (enabled in listOf(false, true)) {
                        configure(enabled)
                        val result =
                            sweep(
                                speed = 1000.0,
                                followUpItems = listOf(stronger, weapon("DIAMOND_SWORD")),
                                nativeWeakerSuppression = !enabled,
                            )
                        withClue("enabled=$enabled hits=${result.hits}") {
                            result.hits.size shouldBe if (enabled) 3 else 2
                            val first = result.hits[0]
                            val second = result.hits[1]
                            first.incoming shouldBe (1.0 plusOrMinus 0.001)
                            first.appliedBase shouldBe (1.0 plusOrMinus 0.001)
                            first.health shouldBe (39.0 plusOrMinus 0.001)
                            first.cancelled shouldBe false
                            second.incoming shouldBe (6.25 plusOrMinus 0.001)
                            second.previousDamage shouldBe (1.0 plusOrMinus 0.001)
                            (second.immunityTicks > 10) shouldBe true
                            second.appliedBase shouldBe (5.25 plusOrMinus 0.001)
                            second.finalDamage shouldBe (5.25 plusOrMinus 0.001)
                            second.health shouldBe (33.75 plusOrMinus 0.001)
                            second.cancelled shouldBe false
                            if (enabled) {
                                val weaker = result.hits[2]
                                weaker.incoming shouldBe (1.0 plusOrMinus 0.001)
                                weaker.previousDamage shouldBe (6.25 plusOrMinus 0.001)
                                (weaker.immunityTicks > 10) shouldBe true
                                weaker.cancelled shouldBe true
                            } else {
                                // Vanilla rejects the weaker native attack before emitting a Bukkit damage event.
                                result.lastDamageBeforeAttempts[2] shouldBe (6.25 plusOrMinus 0.001)
                            }
                            result.healthAfterAttempts.size shouldBe 3
                            result.healthAfterAttempts[2] shouldBe (second.health plusOrMinus 0.001)
                            result.secondary shouldBe (6.25 plusOrMinus 0.001)
                        }
                        results.add(result)
                    }
                    results[1].secondary shouldBe (results[0].secondary plusOrMinus 0.001)
                }
            }

        test("native sweep retains real shield blocking") {
            fixture {
                configure(true, listOf("shield-damage-reduction"))
                ocm.config.set("shield-damage-reduction.generalDamageReductionAmount", 0)
                ocm.config.set("shield-damage-reduction.generalDamageReductionPercentage", 100)
                reload()
                val defender = FakePlayer(plugin)
                try {
                    defender.spawn(location.clone().add(0.0, 0.0, -1.0))
                    val player = checkNotNull(Bukkit.getPlayer(defender.uuid))
                    player.gameMode = GameMode.SURVIVAL
                    player.setGravity(false)
                    player.foodLevel = 10
                    player.saturation = 0f
                    checkNotNull(player.getAttribute(checkNotNull(XAttribute.MAX_HEALTH.get()))).baseValue = 100.0
                    player.health = 40.0
                    // Native players retain join protection briefly after spawning.
                    ticks(65)
                    var blocked = false
                    var blockingDiagnostic = ""
                    val result =
                        sweep(
                            victimFactory = { player },
                            prepare = {
                                player.teleport(location.clone().add(0.0, 0.0, -1.0))
                                player.noDamageTicks = 0
                                player.health = 40.0
                                // Raise a native shield; the modern helper sets its use counter directly.
                                defender.doBlocking()
                                // Legacy offhand use needs the native shield activation delay.
                                ticks(6)
                                player.isBlocking shouldBe true
                            },
                            foreign = {
                                blocked = it.isApplicable(EntityDamageEvent.DamageModifier.BLOCKING) &&
                                    it.getDamage(EntityDamageEvent.DamageModifier.BLOCKING) < 0.0
                                blockingDiagnostic =
                                    "blocking=${player.isBlocking} position=${player.location} attacker=${attacker.location} damage=${it.damage} final=${it.finalDamage}"
                            },
                        )
                    withClue("$result $blockingDiagnostic") {
                        blocked shouldBe true
                        result.secondary shouldBe (0.0 plusOrMinus 0.001)
                    }
                } finally {
                    if (Bukkit.getPlayer(defender.uuid) != null) defender.removePlayer()
                }
            }
        }

        test("constructed melee damage does not consume native attack recharge") {
            fixture {
                for (enabled in listOf(false, true)) {
                    configure(enabled)
                    ocm.config.set("old-tool-damage.damages.DIAMOND_SWORD", 4.625)
                    reload()
                    equip(weapon("DIAMOND_SWORD"))
                    checkNotNull(attacker.getAttribute(checkNotNull(XAttribute.ATTACK_SPEED.get()))).baseValue = 4.0
                    ticks(30)
                    Bukkit.getPluginManager().callEvent(
                        EntityDamageByEntityEvent(attacker, target(), EntityDamageEvent.DamageCause.ENTITY_ATTACK, 7.0),
                    )
                    val victim = target()
                    attack(victim)
                    victim.health shouldBe ((40.0 - if (enabled) 4.625 else 7.0) plusOrMinus 0.001)
                }
            }
        }

        test("partial native cooldown scales configured damage like the disabled control") {
            fixture {
                suspend fun partial(
                    enabled: Boolean,
                    cancelFirst: Boolean,
                ): Double {
                    configure(enabled)
                    ocm.config.set("old-tool-damage.damages.DIAMOND_SWORD", 4.625)
                    reload()
                    equip(weapon("DIAMOND_SWORD"))
                    checkNotNull(attacker.getAttribute(checkNotNull(XAttribute.ATTACK_SPEED.get()))).baseValue = 4.0
                    ticks(30)
                    cancelled = cancelFirst
                    val first = target()
                    attack(first)
                    if (cancelFirst) first.health shouldBe 40.0
                    cancelled = false
                    val victim = target()
                    attack(victim)
                    return 40.0 - victim.health
                }
                for (cancelFirst in listOf(false, true)) {
                    val vanilla = partial(false, cancelFirst)
                    check(vanilla > 0.0 && vanilla <= 7.0 && (cancelFirst || vanilla < 7.0)) {
                        "Control must exercise native recharge after cancellation=$cancelFirst: $vanilla"
                    }
                    val configured = partial(true, cancelFirst)
                    configured shouldBe ((vanilla * 4.625 / 7.0) plusOrMinus 0.001)
                }
            }
        }

        test("later foreign attribute edits remain authoritative after reload and modeset transitions") {
            fixture {
                ocm.config.set("old-tool-damage.damages.DIAMOND_SWORD", 4.625)
                reload()
                damage(weapon("DIAMOND_SWORD")) shouldBe (4.625 plusOrMinus 0.001)
                val item = customWeapon(AttributeModifier.Operation.ADD_NUMBER, 13.0)
                val foreignMeta = item.itemMeta!!
                foreignMeta.setDisplayName("Later foreign edit")
                foreignMeta.lore = listOf("Later foreign lore")
                item.itemMeta = foreignMeta
                equip(item)
                val originalModifiers =
                    item.itemMeta!!.getAttributeModifiers(
                        checkNotNull(XAttribute.ATTACK_DAMAGE.get()),
                    )
                for (mode in listOf("new", "old", "new", "old")) {
                    modeset(mode)
                    reload()
                    damage(attacker.inventory.itemInMainHand) shouldBe (14.0 plusOrMinus 0.001)
                    attacker.inventory.itemInMainHand.itemMeta!!
                        .getAttributeModifiers(checkNotNull(XAttribute.ATTACK_DAMAGE.get())) shouldBe originalModifiers
                    attacker.inventory.itemInMainHand.itemMeta!!
                        .displayName shouldBe "Later foreign edit"
                    attacker.inventory.itemInMainHand.itemMeta!!
                        .lore shouldBe listOf("Later foreign lore")
                }
            }
        }

        test("foreign persistent data survives held item reload and modeset transitions") {
            fixture {
                ocm.config.set("old-tool-damage.damages.DIAMOND_SWORD", 4.625)
                reload()
                val item = weapon("DIAMOND_SWORD")
                val meta = item.itemMeta!!
                meta.setDisplayName("Foreign persistent weapon")
                meta.lore = listOf("Foreign persistent lore")
                // Resolve optional Bukkit classes here, keeping the rest of this spec linkable on legacy APIs.
                val keyClass = Class.forName("org.bukkit.NamespacedKey")
                val key =
                    keyClass
                        .getConstructor(org.bukkit.plugin.Plugin::class.java, String::class.java)
                        .newInstance(plugin, "foreign-weapon-baseline")
                val typeClass = Class.forName("org.bukkit.persistence.PersistentDataType")
                val stringType = typeClass.getField("STRING").get(null)
                val containerClass = Class.forName("org.bukkit.persistence.PersistentDataContainer")
                val container = ItemMeta::class.java.getMethod("getPersistentDataContainer").invoke(meta)
                containerClass
                    .getMethod("set", keyClass, typeClass, Any::class.java)
                    .invoke(container, key, stringType, "foreign-value")
                item.itemMeta = meta
                equip(item)
                for (mode in listOf("new", "old", "new", "old")) {
                    modeset(mode)
                    reload()
                    val held = attacker.inventory.itemInMainHand.itemMeta!!
                    held.displayName shouldBe "Foreign persistent weapon"
                    held.lore shouldBe listOf("Foreign persistent lore")
                    val heldContainer = ItemMeta::class.java.getMethod("getPersistentDataContainer").invoke(held)
                    containerClass
                        .getMethod("get", keyClass, typeClass)
                        .invoke(heldContainer, key, stringType) shouldBe "foreign-value"
                }
                damage(attacker.inventory.itemInMainHand) shouldBe (4.625 plusOrMinus 0.001)
            }
        }

        for (custom in listOf(true, false)) {
            test("native transfer to new-mode player preserves metadata for custom weapon $custom") {
                fixture {
                    val recipientFake = FakePlayer(plugin)
                    var dropped: Item? = null
                    val pickupHistory = mutableListOf<java.util.UUID>()
                    val pickupLocation = location.clone().add(5.0, 0.0, 0.0)
                    val floor = pickupLocation.clone().add(0.0, -1.0, 0.0).block
                    val originalFloor = floor.state
                    try {
                        floor.type = Material.STONE
                        ocm.config.set("old-tool-damage.damages.DIAMOND_SWORD", 4.625)
                        reload()
                        val item =
                            if (custom) {
                                customWeapon(AttributeModifier.Operation.ADD_NUMBER, 11.0)
                            } else {
                                weapon("DIAMOND_SWORD")
                            }
                        val meta = item.itemMeta!!
                        meta.setDisplayName("Transferred foreign weapon")
                        meta.lore = listOf("Foreign transfer lore")
                        item.itemMeta = meta
                        item.addUnsafeEnchantment(checkNotNull(XEnchantment.UNBREAKING.get()), 2)
                        @Suppress("DEPRECATION")
                        item.durability = 5
                        equip(item)
                        val original = attacker.inventory.itemInMainHand.clone()
                        val listener =
                            object : Listener {
                                @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
                                fun onDrop(event: PlayerDropItemEvent) {
                                    if (event.player.uniqueId == attacker.uniqueId) dropped = event.itemDrop
                                }

                                @Suppress("DEPRECATION")
                                @EventHandler(priority = EventPriority.MONITOR)
                                fun onPickup(event: org.bukkit.event.player.PlayerPickupItemEvent) {
                                    if (event.item.uniqueId == dropped?.uniqueId) {
                                        if (!event.isCancelled &&
                                            event.remaining == 0
                                        ) {
                                            pickupHistory.add(event.player.uniqueId)
                                        }
                                    }
                                }
                            }
                        listeners.add(listener)
                        Bukkit.getPluginManager().registerEvents(listener, plugin)
                        attacker.dropItem(true) shouldBe true
                        attacker.inventory.itemInMainHand.type shouldBe Material.AIR
                        val entity = checkNotNull(dropped) { "Native drop did not emit PlayerDropItemEvent" }
                        if (custom) entity.itemStack shouldBe original
                        entity.itemStack.type shouldBe original.type
                        entity.itemStack.itemMeta!!.displayName shouldBe original.itemMeta!!.displayName
                        entity.itemStack.itemMeta!!.lore shouldBe original.itemMeta!!.lore
                        entity.itemStack.enchantments shouldBe original.enchantments
                        attacker.canPickupItems = false
                        attacker.teleport(location.clone().add(16.0, 0.0, 0.0)) shouldBe true
                        recipientFake.spawn(pickupLocation)
                        val recipient = checkNotNull(Bukkit.getPlayer(recipientFake.uuid))
                        recipient.gameMode = GameMode.SURVIVAL
                        recipient.inventory.clear()
                        recipient.canPickupItems = true
                        entity.teleport(pickupLocation) shouldBe true
                        entity.velocity = Vector(0.0, 0.0, 0.0)
                        entity.pickupDelay = 0
                        ticks(5)
                        entity.isValid shouldBe false
                        pickupHistory shouldBe listOf(recipient.uniqueId)
                        val matching =
                            recipient.inventory.contents
                                .filterNotNull()
                                .filter { it.type == original.type }
                        withClue("custom=$custom pickup=$pickupHistory recipient=${recipient.uniqueId}") {
                            matching.size shouldBe 1
                        }
                        val received = matching.single()
                        if (custom) received shouldBe original
                        recipient.inventory.setItemInMainHand(received)
                        checkNotNull(Bukkit.getServicesManager().load(OldCombatMechanicsAPI::class.java))
                            .setModesetForPlayer(recipient, "new")
                        reload()
                        val receivedMeta = recipient.inventory.itemInMainHand.itemMeta!!
                        receivedMeta.displayName shouldBe original.itemMeta!!.displayName
                        receivedMeta.lore shouldBe original.itemMeta!!.lore
                        recipient.inventory.itemInMainHand.enchantments shouldBe original.enchantments
                        @Suppress("DEPRECATION")
                        recipient.inventory.itemInMainHand.durability shouldBe original.durability
                        if (custom) recipient.inventory.itemInMainHand shouldBe original
                        val speedAttribute = checkNotNull(XAttribute.ATTACK_SPEED.get())
                        checkNotNull(recipient.getAttribute(speedAttribute)).baseValue = 1000.0
                        ticks(3)
                        val victim = target()
                        attack(victim, recipient)
                        victim.health shouldBe ((40.0 - if (custom) 12.0 else 7.0) plusOrMinus 0.001)
                    } finally {
                        dropped?.remove()
                        if (Bukkit.getPlayer(recipientFake.uuid) != null) recipientFake.removePlayer()
                        originalFloor.update(true, false)
                    }
                }
            }
        }
        for (name in listOf("DIAMOND_SWORD", "IRON_AXE", "IRON_SHOVEL")) {
            test("native implicit attack speed for $name survives tool damage transitions") {
                fixture {
                    val speed = checkNotNull(attacker.getAttribute(checkNotNull(XAttribute.ATTACK_SPEED.get())))
                    configure(false)
                    equip(weapon(name))
                    val control = speed.value - speed.baseValue
                    check(control < 0.0) { "Disabled native weapon must contribute attack speed: $name delta=$control" }
                    if (name == "DIAMOND_SWORD") control shouldBe (-2.4 plusOrMinus 0.001)
                    configure(true)
                    for (configured in listOf(4.625, 8.125, 4.625)) {
                        ocm.config.set("old-tool-damage.damages.${configKey(weapon(name))}", configured)
                        reload()
                        equip(weapon(name))
                        (speed.value - speed.baseValue) shouldBe (control plusOrMinus 0.001)
                    }
                    modeset("new")
                    ticks(3)
                    (speed.value - speed.baseValue) shouldBe (control plusOrMinus 0.001)
                    modeset("old")
                    ticks(3)
                    (speed.value - speed.baseValue) shouldBe (control plusOrMinus 0.001)
                }
            }
        }
    })
