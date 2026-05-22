/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

@file:Suppress("ktlint:standard:package-name")

package kernitus.plugin.OldCombatMechanics

import com.cryptomorin.xseries.XPotion
import com.google.common.base.Function
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import kernitus.plugin.OldCombatMechanics.module.ModuleOldArmourStrength
import kernitus.plugin.OldCombatMechanics.module.ModuleOldCriticalHits
import kernitus.plugin.OldCombatMechanics.module.ModuleOldPotionEffects
import kernitus.plugin.OldCombatMechanics.module.ModuleShieldDamageReduction
import kernitus.plugin.OldCombatMechanics.utilities.Config
import kernitus.plugin.OldCombatMechanics.utilities.damage.OCMEntityDamageByEntityEvent
import kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage.getPlayerData
import kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage.setPlayerData
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerItemDamageEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionEffect
import java.util.EnumMap
import java.util.Locale
import java.util.concurrent.Callable

@OptIn(ExperimentalKotest::class)
class MixedModePvPIntegrationTest :
    FunSpec({
        val testPlugin = JavaPlugin.getPlugin(OCMTestMain::class.java)
        val ocm = JavaPlugin.getPlugin(OCMMain::class.java)
        val oldArmourStrengthModule =
            ModuleLoader
                .getModules()
                .filterIsInstance<ModuleOldArmourStrength>()
                .firstOrNull() ?: error("ModuleOldArmourStrength not registered")
        val shieldDamageReductionModule =
            ModuleLoader
                .getModules()
                .filterIsInstance<ModuleShieldDamageReduction>()
                .firstOrNull() ?: error("ModuleShieldDamageReduction not registered")
        val oldPotionEffectsModule =
            ModuleLoader
                .getModules()
                .filterIsInstance<ModuleOldPotionEffects>()
                .firstOrNull() ?: error("ModuleOldPotionEffects not registered")
        val oldCriticalHitsModule =
            ModuleLoader
                .getModules()
                .filterIsInstance<ModuleOldCriticalHits>()
                .firstOrNull() ?: error("ModuleOldCriticalHits not registered")

        lateinit var attacker: Player
        lateinit var defender: Player
        lateinit var fakeAttacker: FakePlayer
        lateinit var fakeDefender: FakePlayer

        extensions(MainThreadDispatcherExtension(testPlugin))

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
                        }
                    ).get()
            }
        }

        fun snapshotSection(path: String): Any? {
            val section = ocm.config.getConfigurationSection(path)
            return section?.getValues(false) ?: ocm.config.get(path)
        }

        fun restoreSection(
            path: String,
            value: Any?
        ) {
            ocm.config.set(path, null)
            when (value) {
                null -> {
                    Unit
                }

                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    ocm.config.createSection(path, value as Map<String, Any?>)
                }

                else -> {
                    ocm.config.set(path, value)
                }
            }
        }

        fun configurableModules(): List<String> {
            val internalModules =
                setOf(
                    "modeset-listener",
                    "attack-cooldown-tracker",
                    "entity-damage-listener"
                )
            val optionalModules =
                setOf(
                    "disable-attack-sounds",
                    "disable-sword-sweep-particles"
                )

            return (
                ModuleLoader
                    .getModules()
                    .map { it.configName.lowercase(Locale.ROOT) }
                    .filterNot { internalModules.contains(it) } + optionalModules
            ).distinct().sorted()
        }

        fun reloadConfigAndModules() {
            ocm.saveConfig()
            Config.reload()
            ModuleLoader.toggleModules()
            oldArmourStrengthModule.reload()
            shieldDamageReductionModule.reload()
            oldPotionEffectsModule.reload()
            oldCriticalHitsModule.reload()
        }

        fun applyMixedModeConfig(moduleUnderTest: String) {
            ocm.config.set("always_enabled_modules", emptyList<String>())
            ocm.config.set("disabled_modules", configurableModules().filterNot { it == moduleUnderTest })
            ocm.config.set("modesets", null)
            ocm.config.createSection(
                "modesets",
                mapOf(
                    "old" to listOf(moduleUnderTest),
                    "new" to emptyList<String>()
                )
            )
            ocm.config.set("worlds", null)
            ocm.config.createSection("worlds", mapOf("world" to listOf("old", "new")))
            reloadConfigAndModules()
        }

        suspend fun withMixedModeConfig(
            moduleUnderTest: String,
            block: suspend () -> Unit
        ) {
            val originalAlways = ocm.config.get("always_enabled_modules")
            val originalDisabled = ocm.config.get("disabled_modules")
            val originalModesets = snapshotSection("modesets")
            val originalWorlds = snapshotSection("worlds")

            try {
                runSync { applyMixedModeConfig(moduleUnderTest) }
                block()
            } finally {
                runSync {
                    ocm.config.set("always_enabled_modules", originalAlways)
                    ocm.config.set("disabled_modules", originalDisabled)
                    restoreSection("modesets", originalModesets)
                    restoreSection("worlds", originalWorlds)
                    reloadConfigAndModules()
                }
            }
        }

        suspend fun withMixedModeOldToolDamageConfig(block: suspend () -> Unit) {
            val originalDamages = snapshotSection("old-tool-damage.damages")

            try {
                withMixedModeConfig("old-tool-damage") {
                    runSync {
                        ocm.config.set("old-tool-damage.damages.DIAMOND_SWORD", 8)
                        reloadConfigAndModules()
                    }
                    block()
                }
            } finally {
                runSync {
                    restoreSection("old-tool-damage.damages", originalDamages)
                    reloadConfigAndModules()
                }
            }
        }

        suspend fun withMixedModeOldPotionEffectsAttackConfig(block: suspend () -> Unit) {
            val originalStrength = snapshotSection("old-potion-effects.strength")
            val originalWeakness = snapshotSection("old-potion-effects.weakness")

            try {
                withMixedModeConfig("old-potion-effects") {
                    runSync {
                        ocm.config.set("old-potion-effects.strength.modifier", 2.4)
                        ocm.config.set("old-potion-effects.strength.multiplier", false)
                        ocm.config.set("old-potion-effects.strength.addend", true)
                        ocm.config.set("old-potion-effects.weakness.modifier", -0.75)
                        ocm.config.set("old-potion-effects.weakness.multiplier", true)
                        reloadConfigAndModules()
                    }
                    block()
                }
            } finally {
                runSync {
                    restoreSection("old-potion-effects.strength", originalStrength)
                    restoreSection("old-potion-effects.weakness", originalWeakness)
                    reloadConfigAndModules()
                }
            }
        }

        suspend fun withMixedModeOldCriticalHitsConfig(block: suspend () -> Unit) {
            val originalMultiplier = ocm.config.get("old-critical-hits.multiplier")
            val originalAllowSprinting = ocm.config.get("old-critical-hits.allow-sprinting")

            try {
                withMixedModeConfig("old-critical-hits") {
                    runSync {
                        ocm.config.set("old-critical-hits.multiplier", 1.25)
                        ocm.config.set("old-critical-hits.allow-sprinting", true)
                        reloadConfigAndModules()
                    }
                    block()
                }
            } finally {
                runSync {
                    ocm.config.set("old-critical-hits.multiplier", originalMultiplier)
                    ocm.config.set("old-critical-hits.allow-sprinting", originalAllowSprinting)
                    reloadConfigAndModules()
                }
            }
        }

        fun setModeset(
            player: Player,
            modeset: String
        ) {
            val playerData = getPlayerData(player.uniqueId)
            playerData.setModesetForWorld(player.world.uid, modeset)
            setPlayerData(player.uniqueId, playerData)
        }

        @Suppress("DEPRECATION")
        fun createDirectPvPDamageEvent(): EntityDamageByEntityEvent {
            val modifiers =
                EnumMap<EntityDamageEvent.DamageModifier, Double>(
                    EntityDamageEvent.DamageModifier::class.java
                )
            modifiers[EntityDamageEvent.DamageModifier.BASE] = 20.0
            modifiers[EntityDamageEvent.DamageModifier.ARMOR] = -8.0
            modifiers[EntityDamageEvent.DamageModifier.MAGIC] = -2.0

            val identity =
                object : Function<Double, Double> {
                    override fun apply(input: Double): Double = input
                }
            val modifierFunctions =
                EnumMap<EntityDamageEvent.DamageModifier, Function<in Double, Double>>(
                    EntityDamageEvent.DamageModifier::class.java
                )
            modifiers.keys.forEach { modifierFunctions[it] = identity }

            return EntityDamageByEntityEvent(
                attacker,
                defender,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                modifiers,
                modifierFunctions
            )
        }

        @Suppress("DEPRECATION")
        fun createDirectPvPToolDamageEvent(baseDamage: Double): EntityDamageByEntityEvent {
            val modifiers =
                EnumMap<EntityDamageEvent.DamageModifier, Double>(
                    EntityDamageEvent.DamageModifier::class.java
                )
            modifiers[EntityDamageEvent.DamageModifier.BASE] = baseDamage

            val identity =
                object : Function<Double, Double> {
                    override fun apply(input: Double): Double = input
                }
            val modifierFunctions =
                EnumMap<EntityDamageEvent.DamageModifier, Function<in Double, Double>>(
                    EntityDamageEvent.DamageModifier::class.java
                )
            modifiers.keys.forEach { modifierFunctions[it] = identity }

            return EntityDamageByEntityEvent(
                attacker,
                defender,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                modifiers,
                modifierFunctions
            )
        }

        @Suppress("DEPRECATION")
        fun createShieldBlockedPvPDamageEvent(baseDamage: Double): EntityDamageByEntityEvent {
            val modifiers =
                EnumMap<EntityDamageEvent.DamageModifier, Double>(
                    EntityDamageEvent.DamageModifier::class.java
                )
            modifiers[EntityDamageEvent.DamageModifier.BASE] = baseDamage
            modifiers[EntityDamageEvent.DamageModifier.HARD_HAT] = 0.0
            modifiers[EntityDamageEvent.DamageModifier.BLOCKING] = -baseDamage
            modifiers[EntityDamageEvent.DamageModifier.ARMOR] = 0.0
            modifiers[EntityDamageEvent.DamageModifier.MAGIC] = 0.0

            val identity =
                object : Function<Double, Double> {
                    override fun apply(input: Double): Double = input
                }
            val modifierFunctions =
                EnumMap<EntityDamageEvent.DamageModifier, Function<in Double, Double>>(
                    EntityDamageEvent.DamageModifier::class.java
                )
            modifiers.keys.forEach { modifierFunctions[it] = identity }

            return EntityDamageByEntityEvent(
                attacker,
                defender,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                modifiers,
                modifierFunctions
            )
        }

        fun createItemDamageEvent(
            player: Player,
            item: ItemStack,
            damage: Int
        ): PlayerItemDamageEvent {
            val constructor =
                PlayerItemDamageEvent::class.java.constructors.firstOrNull { candidate ->
                    val params = candidate.parameterTypes
                    params.size == 4 &&
                        Player::class.java.isAssignableFrom(params[0]) &&
                        ItemStack::class.java.isAssignableFrom(params[1]) &&
                        params[2] == Int::class.javaPrimitiveType &&
                        params[3] == Int::class.javaPrimitiveType
                }
            return if (constructor != null) {
                constructor.newInstance(player, item, damage, damage) as PlayerItemDamageEvent
            } else {
                PlayerItemDamageEvent(player, item, damage)
            }
        }

        fun createOCMDirectPvPDamageEvent(rawDamage: Double): OCMEntityDamageByEntityEvent =
            OCMEntityDamageByEntityEvent(
                attacker,
                defender,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                rawDamage
            )

        beforeSpec {
            runSync {
                val world = checkNotNull(Bukkit.getServer().getWorld("world"))
                fakeAttacker = FakePlayer(testPlugin)
                fakeDefender = FakePlayer(testPlugin)
                fakeAttacker.spawn(Location(world, 0.0, 100.0, 0.0))
                fakeDefender.spawn(Location(world, 1.5, 100.0, 0.0))
                attacker = checkNotNull(Bukkit.getPlayer(fakeAttacker.uuid))
                defender = checkNotNull(Bukkit.getPlayer(fakeDefender.uuid))
            }
        }

        afterSpec {
            runSync {
                fakeAttacker.removePlayer()
                fakeDefender.removePlayer()
            }
        }

        beforeTest {
            runSync {
                val world = checkNotNull(Bukkit.getServer().getWorld("world"))
                attacker.teleport(Location(world, 0.0, 100.0, 0.0))
                defender.teleport(Location(world, 1.5, 100.0, 0.0))
                listOf(attacker, defender).forEach { player ->
                    player.gameMode = GameMode.SURVIVAL
                    player.isInvulnerable = false
                    player.noDamageTicks = 0
                    player.maximumNoDamageTicks = 0
                    player.inventory.clear()
                    player.activePotionEffects.forEach { player.removePotionEffect(it.type) }
                }
                defender.inventory.chestplate = ItemStack(Material.DIAMOND_CHESTPLATE)
                attacker.inventory.setItemInMainHand(ItemStack(Material.DIAMOND_SWORD))
            }
        }

        context("old armour strength direct PvP") {
            test("old armour strength uses new vanilla mitigation when only the attacker is old-mode") {
                withMixedModeConfig("old-armour-strength") {
                    runSync {
                        setModeset(attacker, "old")
                        setModeset(defender, "new")

                        val event = createDirectPvPDamageEvent()
                        val originalArmour = event.getDamage(EntityDamageEvent.DamageModifier.ARMOR)
                        val originalMagic = event.getDamage(EntityDamageEvent.DamageModifier.MAGIC)
                        val originalFinalDamage = event.finalDamage

                        Bukkit.getPluginManager().callEvent(event)

                        event.getDamage(EntityDamageEvent.DamageModifier.ARMOR) shouldBe
                            (originalArmour plusOrMinus 0.0001)
                        event.getDamage(EntityDamageEvent.DamageModifier.MAGIC) shouldBe
                            (originalMagic plusOrMinus 0.0001)
                        event.finalDamage shouldBe (originalFinalDamage plusOrMinus 0.0001)
                    }
                }
            }

            test("old armour strength uses old armour mitigation when only the defender is old-mode") {
                withMixedModeConfig("old-armour-strength") {
                    runSync {
                        setModeset(attacker, "new")
                        setModeset(defender, "old")

                        val event = createDirectPvPDamageEvent()
                        Bukkit.getPluginManager().callEvent(event)

                        event.getDamage(EntityDamageEvent.DamageModifier.ARMOR) shouldBe (-6.4 plusOrMinus 0.0001)
                        event.getDamage(EntityDamageEvent.DamageModifier.MAGIC) shouldBe (0.0 plusOrMinus 0.0001)
                        event.finalDamage shouldBe (13.6 plusOrMinus 0.0001)
                    }
                }
            }
        }

        context("shield damage reduction direct PvP") {
            test("shield damage reduction keeps vanilla blocking when only the attacker is old-mode") {
                withMixedModeConfig("shield-damage-reduction") {
                    runSync {
                        setModeset(attacker, "old")
                        setModeset(defender, "new")

                        val event = createShieldBlockedPvPDamageEvent(baseDamage = 20.0)

                        Bukkit.getPluginManager().callEvent(event)

                        event.isCancelled shouldBe false
                        event.getDamage(EntityDamageEvent.DamageModifier.BLOCKING) shouldBe (-20.0 plusOrMinus 0.0001)
                        event.finalDamage shouldBe (0.0 plusOrMinus 0.0001)
                    }
                }
            }

            test(
                "shield damage reduction uses old shield maths and armour suppression when only the defender is old-mode"
            ) {
                withMixedModeConfig("shield-damage-reduction") {
                    runSync {
                        setModeset(attacker, "new")
                        setModeset(defender, "old")
                        val originalAmount =
                            ocm.config.get("shield-damage-reduction.generalDamageReductionAmount")
                        val originalPercentage =
                            ocm.config.get("shield-damage-reduction.generalDamageReductionPercentage")
                        ocm.config.set("shield-damage-reduction.generalDamageReductionAmount", 0)
                        ocm.config.set("shield-damage-reduction.generalDamageReductionPercentage", 100)
                        shieldDamageReductionModule.reload()

                        try {
                            val event = createShieldBlockedPvPDamageEvent(baseDamage = 20.0)

                            Bukkit.getPluginManager().callEvent(event)

                            event.isCancelled shouldBe false
                            event.getDamage(EntityDamageEvent.DamageModifier.BLOCKING) shouldBe
                                (-20.0 plusOrMinus 0.0001)
                            event.finalDamage shouldBe (0.0 plusOrMinus 0.0001)

                            val chestplate = checkNotNull(defender.inventory.chestplate)
                            val itemDamageEvent = createItemDamageEvent(defender, chestplate, 1)
                            Bukkit.getPluginManager().callEvent(itemDamageEvent)

                            itemDamageEvent.isCancelled shouldBe true
                        } finally {
                            ocm.config.set("shield-damage-reduction.generalDamageReductionAmount", originalAmount)
                            ocm.config.set(
                                "shield-damage-reduction.generalDamageReductionPercentage",
                                originalPercentage
                            )
                            shieldDamageReductionModule.reload()
                        }
                    }
                }
            }
        }

        context("old tool damage direct PvP") {
            test("old tool damage applies old diamond sword damage when only the attacker is old-mode") {
                withMixedModeOldToolDamageConfig {
                    runSync {
                        setModeset(attacker, "old")
                        setModeset(defender, "new")

                        val event = createDirectPvPToolDamageEvent(baseDamage = 7.0)
                        Bukkit.getPluginManager().callEvent(event)

                        event.getDamage(EntityDamageEvent.DamageModifier.BASE) shouldBe (8.0 plusOrMinus 0.0001)
                        event.finalDamage shouldBe (8.0 plusOrMinus 0.0001)
                    }
                }
            }

            test("old tool damage keeps new diamond sword damage when only the defender is old-mode") {
                withMixedModeOldToolDamageConfig {
                    runSync {
                        setModeset(attacker, "new")
                        setModeset(defender, "old")

                        val event = createDirectPvPToolDamageEvent(baseDamage = 7.0)
                        Bukkit.getPluginManager().callEvent(event)

                        event.getDamage(EntityDamageEvent.DamageModifier.BASE) shouldBe (7.0 plusOrMinus 0.0001)
                        event.finalDamage shouldBe (7.0 plusOrMinus 0.0001)
                    }
                }
            }
        }

        context("old potion effects direct PvP") {
            test("old potion effects apply old strength and weakness modifiers when only the attacker is old-mode") {
                withMixedModeOldPotionEffectsAttackConfig {
                    runSync {
                        setModeset(attacker, "old")
                        setModeset(defender, "new")
                        attacker.addPotionEffect(PotionEffect(XPotion.STRENGTH.get()!!, 200, 0), true)
                        attacker.addPotionEffect(PotionEffect(XPotion.WEAKNESS.get()!!, 200, 0), true)

                        val event = createOCMDirectPvPDamageEvent(rawDamage = 10.0)
                        Bukkit.getPluginManager().callEvent(event)

                        event.strengthModifier shouldBe (2.4 plusOrMinus 0.0001)
                        event.isStrengthModifierMultiplier shouldBe false
                        event.isStrengthModifierAddend shouldBe true
                        event.weaknessModifier shouldBe (-0.75 plusOrMinus 0.0001)
                        event.isWeaknessModifierMultiplier shouldBe true
                        event.weaknessLevel shouldBe 1
                    }
                }
            }

            test("old potion effects keep new strength and weakness modifiers when only the defender is old-mode") {
                withMixedModeOldPotionEffectsAttackConfig {
                    runSync {
                        setModeset(attacker, "new")
                        setModeset(defender, "old")
                        attacker.addPotionEffect(PotionEffect(XPotion.STRENGTH.get()!!, 200, 0), true)
                        attacker.addPotionEffect(PotionEffect(XPotion.WEAKNESS.get()!!, 200, 0), true)

                        val event = createOCMDirectPvPDamageEvent(rawDamage = 10.0)
                        Bukkit.getPluginManager().callEvent(event)

                        event.strengthModifier shouldBe (3.0 plusOrMinus 0.0001)
                        event.isStrengthModifierMultiplier shouldBe false
                        event.isStrengthModifierAddend shouldBe true
                        event.weaknessModifier shouldBe (-4.0 plusOrMinus 0.0001)
                        event.isWeaknessModifierMultiplier shouldBe false
                        event.weaknessLevel shouldBe 1
                    }
                }
            }
        }

        context("old critical hits direct PvP") {
            test("old critical hits apply the old critical multiplier when only the attacker is old-mode") {
                withMixedModeOldCriticalHitsConfig {
                    runSync {
                        setModeset(attacker, "old")
                        setModeset(defender, "new")

                        val event = createOCMDirectPvPDamageEvent(rawDamage = 10.0)
                        event.setWas1_8Crit(true)
                        event.setWasSprinting(false)
                        Bukkit.getPluginManager().callEvent(event)

                        event.criticalMultiplier shouldBe (1.25 plusOrMinus 0.0001)
                    }
                }
            }

            test("old critical hits keep the new critical multiplier when only the defender is old-mode") {
                withMixedModeOldCriticalHitsConfig {
                    runSync {
                        setModeset(attacker, "new")
                        setModeset(defender, "old")

                        val event = createOCMDirectPvPDamageEvent(rawDamage = 10.0)
                        event.setWas1_8Crit(true)
                        event.setWasSprinting(false)
                        Bukkit.getPluginManager().callEvent(event)

                        event.criticalMultiplier shouldBe (1.0 plusOrMinus 0.0001)
                    }
                }
            }
        }
    })