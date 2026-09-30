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
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import kernitus.plugin.OldCombatMechanics.api.OldCombatMechanicsAPI
import kernitus.plugin.OldCombatMechanics.utilities.Config
import kernitus.plugin.OldCombatMechanics.utilities.damage.DamageUtils
import kernitus.plugin.OldCombatMechanics.utilities.damage.OCMEntityDamageByEntityEvent
import kernitus.plugin.OldCombatMechanics.utilities.potions.WeaknessCompensation
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector
import kotlinx.coroutines.suspendCancellableCoroutine
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.attribute.AttributeModifier
import org.bukkit.entity.Cow
import org.bukkit.entity.Item
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
                        "amplifier=${attacker.getPotionEffect(
                            org.bukkit.potion.PotionEffectType.WEAKNESS,
                        )?.amplifier} " +
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
                target: Cow,
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
        test("native sweep preserves configured potion damage after the primary attack") {
            fixture {
                val floor = location.clone().subtract(0.0, 1.0, 0.0).block
                val originalFloor = floor.state
                val cooldowns = mutableMapOf<String, Double>()
                val listener =
                    object : Listener {
                        @EventHandler
                        fun capture(event: OCMEntityDamageByEntityEvent) {
                            if (event.damager.uniqueId == attacker.uniqueId && event.damagee in targets) {
                                cooldowns[event.cause.name] = DamageUtils.getAttackCooldown.apply(attacker).toDouble()
                            }
                        }
                    }
                listeners.add(listener)
                Bukkit.getPluginManager().registerEvents(listener, plugin)
                try {
                    floor.type = Material.STONE
                    for (enabled in listOf(false, true)) {
                        targets.forEach { it.remove() }
                        targets.clear()
                        configure(false, if (enabled) listOf("old-potion-effects") else emptyList())
                        ocm.config.set("old-potion-effects.strength.modifier", 4.0)
                        ocm.config.set("old-potion-effects.strength.multiplier", false)
                        reload()
                        attacker.teleport(location)
                        equip(weapon("DIAMOND_SWORD"))
                        attacker.addPotionEffect(PotionEffect(checkNotNull(XPotion.STRENGTH.get()), 200, 0))
                        checkNotNull(attacker.getAttribute(checkNotNull(XAttribute.ATTACK_SPEED.get()))).baseValue = 4.0
                        ticks(30)
                        check(attacker.isOnGround) { "Native sweep requires a grounded attacker" }
                        val primary = target()
                        val secondary = target()
                        attack(primary)
                        withClue("enabled=$enabled health=${secondary.health} cooldowns=$cooldowns") {
                            // Native sweep damage is 1.0. The API path already exposes reset recharge for sweeps;
                            // the legacy tracker retains the primary sample. Preserve both existing semantics.
                            val hasCooldownApi = Reflector.getMethod(Player::class.java, "getAttackCooldown", 0) != null
                            val configuredLoss = if (hasCooldownApi) 1.20128 else 2.0
                            secondary.health shouldBe ((40.0 - if (enabled) configuredLoss else 1.0) plusOrMinus 0.001)
                        }
                    }
                } finally {
                    originalFloor.update(true, false)
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
                    val floor = location.clone().add(0.0, -1.0, 0.0).block
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
                        attacker.teleport(location.clone().add(5.0, 0.0, 0.0))
                        recipientFake.spawn(location)
                        val recipient = checkNotNull(Bukkit.getPlayer(recipientFake.uuid))
                        recipient.gameMode = GameMode.SURVIVAL
                        recipient.inventory.clear()
                        recipient.canPickupItems = true
                        entity.teleport(location)
                        entity.velocity = Vector(0.0, 0.0, 0.0)
                        entity.pickupDelay = 0
                        ticks(5)
                        entity.isValid shouldBe false
                        val received =
                            recipient.inventory.contents
                                .filterNotNull()
                                .single { it.type == original.type }
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
