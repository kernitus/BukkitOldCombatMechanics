/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics

import com.cryptomorin.xseries.XAttribute
import com.cryptomorin.xseries.XMaterial
import io.kotest.common.ExperimentalKotest
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import kernitus.plugin.OldCombatMechanics.module.ModuleOldCriticalHits
import kernitus.plugin.OldCombatMechanics.module.ModuleOldToolDamage
import kernitus.plugin.OldCombatMechanics.utilities.damage.DamageUtils
import kernitus.plugin.OldCombatMechanics.utilities.damage.NewWeaponDamage
import kernitus.plugin.OldCombatMechanics.utilities.damage.OCMEntityDamageByEntityEvent
import kernitus.plugin.OldCombatMechanics.utilities.damage.WeaponDamages
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector
import kotlinx.coroutines.delay
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.attribute.AttributeModifier
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.util.Vector
import java.util.concurrent.Callable
import java.util.UUID
import kotlin.math.abs

@OptIn(ExperimentalKotest::class)
class OldCriticalHitsIntegrationTest : FunSpec({
    val testPlugin = JavaPlugin.getPlugin(OCMTestMain::class.java)
    val ocm = JavaPlugin.getPlugin(OCMMain::class.java)
    val criticalModule = ModuleLoader.getModules()
        .filterIsInstance<ModuleOldCriticalHits>()
        .firstOrNull() ?: error("ModuleOldCriticalHits not registered")
    val toolDamageModule = ModuleLoader.getModules()
        .filterIsInstance<ModuleOldToolDamage>()
        .firstOrNull() ?: error("ModuleOldToolDamage not registered")

    lateinit var attacker: Player
    lateinit var fakeAttacker: FakePlayer

    extensions(MainThreadDispatcherExtension(testPlugin))

    val isLegacy = !Reflector.versionIsNewerOrEqualTo(1, 13, 0)
    val legacySpeedModifierId = UUID.fromString("c1f6010f-4d2e-4b2e-9a2f-3f0d0f1b2e3c")
    fun runSync(action: () -> Unit) {
        if (Bukkit.isPrimaryThread()) {
            action()
        } else {
            Bukkit.getScheduler().callSyncMethod(testPlugin, Callable {
                action()
                null
            }).get()
        }
    }

    fun setOnGround(player: Player, onGround: Boolean) {
        val handle = player.javaClass.getMethod("getHandle").invoke(player)
        val field = generateSequence(handle.javaClass) { it.superclass }
            .mapNotNull { klass ->
                runCatching { klass.getDeclaredField("onGround") }.getOrNull()
            }
            .firstOrNull()
        field?.let {
            it.isAccessible = true
            it.setBoolean(handle, onGround)
            return
        }
        val setOnGroundMethod = generateSequence(handle.javaClass) { it.superclass }
            .mapNotNull { klass ->
                runCatching { klass.getDeclaredMethod("setOnGround", Boolean::class.javaPrimitiveType) }.getOrNull()
            }
            .firstOrNull()
        setOnGroundMethod?.let {
            it.isAccessible = true
            it.invoke(handle, onGround)
        }
    }

    suspend fun delayTicks(ticks: Long) {
        delay(ticks * 50L)
    }

    fun prepareWeapon(item: ItemStack) {
        val meta = item.itemMeta ?: return
        val attackSpeedAttribute = XAttribute.ATTACK_SPEED.get() ?: return
        val speedModifier = createAttributeModifier(
            name = "speed",
            amount = 1000.0,
            operation = AttributeModifier.Operation.ADD_NUMBER,
            slot = EquipmentSlot.HAND
        )
        addAttributeModifierCompat(meta, attackSpeedAttribute, speedModifier)
        item.itemMeta = meta
    }

    fun applyAttackDamageModifiers(player: Player, item: ItemStack) {
        if (isLegacy) {
            val attackSpeedAttribute = XAttribute.ATTACK_SPEED.get()
            val speedAttribute = attackSpeedAttribute?.let { player.getAttribute(it) }
            speedAttribute
                ?.modifiers
                ?.filter { it.uniqueId == legacySpeedModifierId }
                ?.forEach { speedAttribute.removeModifier(it) }
            val speedModifier = createAttributeModifier(
                name = "ocm-legacy-speed",
                amount = 1000.0,
                operation = AttributeModifier.Operation.ADD_NUMBER,
                slot = EquipmentSlot.HAND,
                uuid = legacySpeedModifierId
            )
            speedAttribute?.addModifier(speedModifier)
            return
        }
        val attackDamageAttribute = XAttribute.ATTACK_DAMAGE.get() ?: return
        val attackAttribute = player.getAttribute(attackDamageAttribute) ?: return
        val modifiers = getDefaultAttributeModifiersCompat(item, EquipmentSlot.HAND, attackDamageAttribute)
        val expectedAmounts = modifiers
            .filter { it.operation == AttributeModifier.Operation.ADD_NUMBER }
            .map { it.amount }
        val knownWeaponAmounts = NewWeaponDamage.values()
            .map { it.damage.toDouble() - 1.0 }
            .filter { it > 0.0 }
            .toSet()

        fun matchesAmount(first: Double, second: Double): Boolean = abs(first - second) <= 0.0001

        val existingModifiers = attackAttribute.modifiers.toList()
        existingModifiers
            .filter { it.operation == AttributeModifier.Operation.ADD_NUMBER && it.amount > 0.0 }
            .filter { modifier ->
                knownWeaponAmounts.any { matchesAmount(it, modifier.amount) } &&
                    expectedAmounts.none { expected -> matchesAmount(expected, modifier.amount) }
            }
            .forEach { attackAttribute.removeModifier(it) }

        modifiers.forEach { modifier ->
            val alreadyApplied = attackAttribute.modifiers.any {
                it.operation == modifier.operation && matchesAmount(it.amount, modifier.amount)
            }
            if (!alreadyApplied) {
                attackAttribute.addModifier(modifier)
            }
        }
    }

    fun equip(player: Player, item: ItemStack) {
        if (isLegacy) {
            // On legacy versions, avoid mutating item meta; directly adjust player attributes instead.
            val meta = item.itemMeta
            if (meta != null) {
                runCatching {
                    XAttribute.ATTACK_DAMAGE.get()?.let { meta.removeAttributeModifier(it) }
                    XAttribute.ATTACK_SPEED.get()?.let { meta.removeAttributeModifier(it) }
                }
                runCatching { meta.removeAttributeModifier(EquipmentSlot.HAND) }
                runCatching { meta.removeAttributeModifier(EquipmentSlot.OFF_HAND) }
                item.itemMeta = meta
            }

            val useDamageAttribute = !Reflector.versionIsNewerOrEqualTo(1, 12, 0)
            if (useDamageAttribute) {
                val attackDamageAttribute = XAttribute.ATTACK_DAMAGE.get()
                val damageAttribute = attackDamageAttribute?.let { player.getAttribute(it) }
                val configuredDamage = WeaponDamages.getDamage(item.type).toDouble().takeIf { it > 0 }
                    ?: (NewWeaponDamage.getDamageOrNull(item.type) ?: 1.0f).toDouble()
                damageAttribute?.baseValue = configuredDamage
            }
            player.inventory.setItemInMainHand(item)
            applyAttackDamageModifiers(player, item)
            player.updateInventory()
            return
        }

        prepareWeapon(item)
        player.inventory.setItemInMainHand(item)
        applyAttackDamageModifiers(player, item)
        player.updateInventory()
    }

    fun spawnVictim(location: Location): LivingEntity {
        val world = location.world ?: error("World missing for victim spawn")
        return world.spawn(location, org.bukkit.entity.Cow::class.java).apply {
            maximumNoDamageTicks = 0
            noDamageTicks = 0
            isInvulnerable = false
            health = maxHealth
        }
    }

    suspend fun hitAndCaptureDamage(
        weapon: ItemStack,
        critical: Boolean
    ): Double {
        val events = mutableListOf<EntityDamageByEntityEvent>()
        val ocmEvents = mutableListOf<OCMEntityDamageByEntityEvent>()
        lateinit var victim: LivingEntity

        val listener = object : Listener {
            @EventHandler
            fun onDamage(event: EntityDamageByEntityEvent) {
                if (event.damager.uniqueId == attacker.uniqueId &&
                    event.entity.uniqueId == victim.uniqueId
                ) {
                    events.add(event)
                    testPlugin.logger.info(
                        "Critical hit debug: weapon=${attacker.inventory.itemInMainHand.type} " +
                            "critical=$critical sprinting=${attacker.isSprinting} " +
                            "fallDistance=${attacker.fallDistance} onGround=${attacker.isOnGround} " +
                            "damage=${event.damage} finalDamage=${event.finalDamage}"
                    )
                }
            }

            @EventHandler
            fun onOcm(event: OCMEntityDamageByEntityEvent) {
                if (event.damager.uniqueId == attacker.uniqueId &&
                    event.damagee.uniqueId == victim.uniqueId
                ) {
                    ocmEvents.add(event)
                }
            }
        }

        try {
            runSync {
                val world = checkNotNull(Bukkit.getWorld("world"))
                val victimLocation = Location(world, 1.2, 100.0, 0.0)
                victim = spawnVictim(victimLocation)
                Bukkit.getPluginManager().registerEvents(listener, testPlugin)

                equip(attacker, weapon)
            }
            delayTicks(1)
            if (isLegacy) {
                // Vanilla 1.12 applies attack cooldown scaling before the Bukkit damage event fires.
                // Give the fake player a short warmup so the baseline (non-critical) hit is not under-scaled.
                delayTicks(6)
            }
            val base = Location(attacker.world, 0.0, 100.0, 0.0)
            runSync {
                attacker.teleport(base)
                attacker.velocity = Vector(0.0, 0.0, 0.0)
                attacker.isSprinting = false
                attacker.fallDistance = 0f
                attacker.updateInventory()
            }

            if (critical) {
                // Give the server one tick to recognise the falling state, then re-apply immediately before the swing
                // so it does not get cleared by ticking (varies by version / fake player internals).
                runSync {
                    attacker.isSprinting = true
                    attacker.teleport(attacker.location.add(0.0, 1.0, 0.0))
                    attacker.velocity = Vector(0.0, -0.1, 0.0)
                    attacker.fallDistance = 2f
                    setOnGround(attacker, false)
                }
                delayTicks(1)
                runSync {
                    attacker.isSprinting = true
                    attacker.velocity = Vector(0.0, -0.1, 0.0)
                    attacker.fallDistance = 2f
                    setOnGround(attacker, false)
                    if (!DamageUtils.isCriticalHit1_8(attacker)) {
                        attacker.fallDistance = 3f
                        setOnGround(attacker, false)
                    }
                    val loc = attacker.location
                    val dir = victim.location.toVector().subtract(loc.toVector()).normalize()
                    loc.direction = dir
                    attacker.teleport(loc)
                    testPlugin.logger.info(
                        "Critical pre-attack: fallDistance=${attacker.fallDistance} onGround=${attacker.isOnGround}"
                    )
                    attacker.updateInventory()
                    attackCompat(attacker, victim)
                }
            } else {
                runSync {
                    attacker.isSprinting = false
                    attacker.fallDistance = 0f
                    attacker.velocity = Vector(0.0, 0.0, 0.0)
                    val loc = attacker.location
                    val dir = victim.location.toVector().subtract(loc.toVector()).normalize()
                    loc.direction = dir
                    attacker.teleport(loc)
                    testPlugin.logger.info(
                        "Normal pre-attack: fallDistance=${attacker.fallDistance} onGround=${attacker.isOnGround}"
                    )
                    attacker.updateInventory()
                    attackCompat(attacker, victim)
                }
            }
            delayTicks(4)
            events.firstOrNull()?.damage?.let { return it }
            if (critical) {
                val ocmEvent = ocmEvents.lastOrNull()
                if (ocmEvent != null && !ocmEvent.was1_8Crit()) {
                    testPlugin.logger.info(
                        "Critical path but was1_8Crit=false; fallDistance=${attacker.fallDistance} " +
                            "onGround=${attacker.isOnGround}"
                    )
                }
            }

            if (isLegacy) {
                // As a last resort on legacy, drive damage via Bukkit API to ensure EDBE fires.
                runSync {
                    val base = WeaponDamages.getDamage(weapon.type).takeIf { it > 0 } ?: 1.0
                    victim.damage(base, attacker)
                }
                delayTicks(4)
                events.firstOrNull()?.damage?.let { return it }
                val healthDelta = (victim.maxHealth - victim.health).toDouble()
                if (healthDelta > 0.0) return healthDelta
            }

            error("Expected a damage event for critical=$critical")
        } finally {
            HandlerList.unregisterAll(listener)
            runSync {
                victim.remove()
            }
        }
    }

    suspend fun TestScope.withConfig(block: suspend TestScope.() -> Unit) {
        val critEnabled = ocm.config.getBoolean("old-critical-hits.enabled")
        val critMultiplier = ocm.config.getDouble("old-critical-hits.multiplier")
        val critAllowSprinting = ocm.config.getBoolean("old-critical-hits.allow-sprinting")
        val damagesSection = ocm.config.getConfigurationSection("old-tool-damage.damages")
        val damagesSnapshot = damagesSection?.getKeys(false)?.associateWith { damagesSection.get(it) } ?: emptyMap()

        fun reloadDamageModules() {
            WeaponDamages.initialise(ocm)
            criticalModule.reload()
            toolDamageModule.reload()
            ModuleLoader.toggleModules()
        }

        try {
            block()
        } finally {
            ocm.config.set("old-critical-hits.enabled", critEnabled)
            ocm.config.set("old-critical-hits.multiplier", critMultiplier)
            ocm.config.set("old-critical-hits.allow-sprinting", critAllowSprinting)
            damagesSnapshot.forEach { (key, value) ->
                ocm.config.set("old-tool-damage.damages.$key", value)
            }
            reloadDamageModules()
        }
    }

    beforeSpec {
        runSync {
            val world = checkNotNull(Bukkit.getWorld("world"))
            val location = Location(world, 0.0, 100.0, 0.0)
            fakeAttacker = FakePlayer(testPlugin)
            fakeAttacker.spawn(location)
            attacker = checkNotNull(Bukkit.getPlayer(fakeAttacker.uuid))
            attacker.gameMode = GameMode.SURVIVAL
            attacker.isInvulnerable = false
            attacker.inventory.clear()
            attacker.activePotionEffects.forEach { attacker.removePotionEffect(it.type) }
            attacker.isOp = true
            val playerData = kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage.getPlayerData(attacker.uniqueId)
            playerData.setModesetForWorld(attacker.world.uid, "old")
            kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage.setPlayerData(attacker.uniqueId, playerData)
        }
    }

    afterSpec {
        runSync {
            fakeAttacker.removePlayer()
        }
    }

    test("critical hit multiplier applies to customised tool damage") {
        withConfig {
            ocm.config.set("old-critical-hits.enabled", true)
            ocm.config.set("old-critical-hits.multiplier", 1.5)
            ocm.config.set("old-critical-hits.allow-sprinting", true)
            ocm.config.set("old-tool-damage.damages.STONE_SWORD", 10)
            WeaponDamages.initialise(ocm)
            criticalModule.reload()
            toolDamageModule.reload()
            ModuleLoader.toggleModules()

            val stoneSword = XMaterial.STONE_SWORD.parseItem()
                ?: error("STONE_SWORD material not available")
            val normalDamage = hitAndCaptureDamage(stoneSword, critical = false)
            val criticalDamage = hitAndCaptureDamage(stoneSword, critical = true)
            withClue("normal=$normalDamage critical=$criticalDamage") {
                (criticalDamage / normalDamage) shouldBe (1.5 plusOrMinus 0.05)
            }
        }
    }

    test("critical hit multiplier applies when tool damage matches vanilla values") {
        withConfig {
            ocm.config.set("old-critical-hits.enabled", true)
            ocm.config.set("old-critical-hits.multiplier", 1.5)
            ocm.config.set("old-critical-hits.allow-sprinting", true)
            ocm.config.set("old-tool-damage.damages.IRON_SWORD", 6)
            WeaponDamages.initialise(ocm)
            criticalModule.reload()
            toolDamageModule.reload()
            ModuleLoader.toggleModules()

            val ironSword = XMaterial.IRON_SWORD.parseItem()
                ?: error("IRON_SWORD material not available")
            val normalDamage = hitAndCaptureDamage(ironSword, critical = false)
            val criticalDamage = hitAndCaptureDamage(ironSword, critical = true)
            withClue("normal=$normalDamage critical=$criticalDamage") {
                (criticalDamage / normalDamage) shouldBe (1.5 plusOrMinus 0.05)
            }
        }
    }

    test("critical hit multiplier applies to config damage for iron axe") {
        withConfig {
            ocm.config.set("old-critical-hits.enabled", true)
            ocm.config.set("old-critical-hits.multiplier", 1.5)
            ocm.config.set("old-critical-hits.allow-sprinting", true)
            ocm.config.set("old-tool-damage.damages.IRON_AXE", 6)
            WeaponDamages.initialise(ocm)
            criticalModule.reload()
            toolDamageModule.reload()
            ModuleLoader.toggleModules()

            val ironAxe = XMaterial.IRON_AXE.parseItem()
                ?: error("IRON_AXE material not available")
            val normalDamage = hitAndCaptureDamage(ironAxe, critical = false)
            val criticalDamage = hitAndCaptureDamage(ironAxe, critical = true)
            testPlugin.logger.info("Crit debug (cfg=6): normal=$normalDamage critical=$criticalDamage")
            withClue("normal=$normalDamage critical=$criticalDamage") {
                (criticalDamage / normalDamage) shouldBe (1.5 plusOrMinus 0.05)
            }
        }
    }

    test("critical hit multiplies configured iron axe damage") {
        withConfig {
            ocm.config.set("old-critical-hits.enabled", true)
            ocm.config.set("old-critical-hits.multiplier", 1.25)
            ocm.config.set("old-critical-hits.allow-sprinting", true)
            ocm.config.set("old-tool-damage.damages.IRON_AXE", 4.5)
            WeaponDamages.initialise(ocm)
            criticalModule.reload()
            toolDamageModule.reload()
            ModuleLoader.toggleModules()

            val ironAxe = XMaterial.IRON_AXE.parseItem()
                ?: error("IRON_AXE material not available")
            val normalDamage = hitAndCaptureDamage(ironAxe, critical = false)
            val criticalDamage = hitAndCaptureDamage(ironAxe, critical = true)
            testPlugin.logger.info("Crit debug (cfg=4.5): normal=$normalDamage critical=$criticalDamage")
            withClue("normal=$normalDamage critical=$criticalDamage") {
                normalDamage shouldBe (4.5 plusOrMinus 0.05)
                criticalDamage shouldBe (5.625 plusOrMinus 0.05)
            }
        }
    }
})
