/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics

import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kernitus.plugin.OldCombatMechanics.module.ModuleOldPotionEffects
import kernitus.plugin.OldCombatMechanics.utilities.Config
import kernitus.plugin.OldCombatMechanics.utilities.damage.OCMEntityDamageByEntityEvent
import com.cryptomorin.xseries.XAttribute
import com.cryptomorin.xseries.XMaterial
import com.cryptomorin.xseries.XPotion
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.attribute.AttributeModifier
import org.bukkit.block.BlockFace
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockDispenseEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.PotionMeta
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionData
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.potion.PotionType
import org.bukkit.util.Vector
import kotlinx.coroutines.delay
import java.util.concurrent.Callable
import kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage.getPlayerData
import kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage.setPlayerData

@OptIn(ExperimentalKotest::class)
class OldPotionEffectsIntegrationTest : FunSpec({
    val testPlugin = JavaPlugin.getPlugin(OCMTestMain::class.java)
    val ocm = JavaPlugin.getPlugin(OCMMain::class.java)
    val module = ModuleLoader.getModules()
        .filterIsInstance<ModuleOldPotionEffects>()
        .firstOrNull() ?: error("ModuleOldPotionEffects not registered")
    lateinit var player: Player
    lateinit var fakePlayer: FakePlayer

    val excludedPotionTypes = setOf(
        "AWKWARD",
        "MUNDANE",
        "THICK",
        "WATER",
        "HARMING",
        "STRONG_HARMING",
        "HEALING",
        "STRONG_HEALING",
        "INSTANT_DAMAGE",
        "INSTANT_HEAL",
        "INSTANT_HEALTH",
        "UNCRAFTABLE"
    )

    data class PotionCase(
        val key: String,
        val baseName: String,
        val isStrong: Boolean,
        val isExtended: Boolean,
        val potion: XPotion,
        val drinkableTicks: Int,
        val splashTicks: Int
    )

    data class PotionBaseSnapshot(
        val baseType: PotionType?,
        val isUpgraded: Boolean,
        val isExtended: Boolean
    )

    data class ParsedPotionKey(
        val baseName: String,
        val isStrong: Boolean,
        val isExtended: Boolean,
        val debugName: String
    )

    fun debugName(baseName: String, isStrong: Boolean, isExtended: Boolean): String {
        return when {
            isStrong -> "STRONG_$baseName"
            isExtended -> "LONG_$baseName"
            else -> baseName
        }
    }

    suspend fun waitForAttackReady(attacker: Player) {
        val cooldownMethod = attacker.javaClass.methods.firstOrNull { method ->
            method.name == "getAttackCooldown" && method.parameterTypes.isEmpty()
        }
        if (cooldownMethod == null) {
            delay(700)
            return
        }

        repeat(40) {
            val value = (cooldownMethod.invoke(attacker) as? Float) ?: 1.0f
            if (value >= 0.99f) return
            delay(50)
        }
    }

    fun resolveBasePotionType(baseName: String, potion: XPotion): PotionType? {
        return runCatching { PotionType.valueOf(baseName) }.getOrNull()
            ?: potion.potionType
    }

    fun parsePotionKey(key: String): ParsedPotionKey {
        var name = key.uppercase()
        var isStrong = false
        var isExtended = false
        if (name.startsWith("STRONG_")) {
            isStrong = true
            name = name.removePrefix("STRONG_")
        } else if (name.startsWith("LONG_")) {
            isExtended = true
            name = name.removePrefix("LONG_")
        }
        val debugName = debugName(name, isStrong, isExtended)
        return ParsedPotionKey(name, isStrong, isExtended, debugName)
    }

    val hasBasePotionType = runCatching { PotionMeta::class.java.getMethod("getBasePotionType") }.isSuccess

    fun potionSupports(baseName: String, isStrong: Boolean, isExtended: Boolean, potion: XPotion): Boolean {
        val potionType = resolveBasePotionType(baseName, potion) ?: return false
        return if (hasBasePotionType) {
            val resolvedName = when {
                isStrong -> "STRONG_${potionType.name}"
                isExtended -> "LONG_${potionType.name}"
                else -> potionType.name
            }
            runCatching { PotionType.valueOf(resolvedName) }.isSuccess
        } else {
            runCatching { PotionData(potionType, isExtended, isStrong) }.isSuccess
        }
    }

    fun loadPotionCases(): List<PotionCase> {
        val drinkable = ocm.config.getConfigurationSection("old-potion-effects.potion-durations.drinkable")
            ?: return emptyList()
        val splash = ocm.config.getConfigurationSection("old-potion-effects.potion-durations.splash")
            ?: return emptyList()

        return drinkable.getKeys(false).mapNotNull { key ->
            if (!splash.isInt(key)) return@mapNotNull null
            val parsed = parsePotionKey(key)
            val potion = XPotion.of(parsed.baseName).orElse(null) ?: return@mapNotNull null
            if (excludedPotionTypes.contains(parsed.debugName)) return@mapNotNull null
            if (!potionSupports(parsed.baseName, parsed.isStrong, parsed.isExtended, potion)) return@mapNotNull null
            PotionCase(
                key = key,
                baseName = parsed.baseName,
                isStrong = parsed.isStrong,
                isExtended = parsed.isExtended,
                potion = potion,
                drinkableTicks = drinkable.getInt(key) * 20,
                splashTicks = splash.getInt(key) * 20
            )
        }
    }

    fun createPotionItem(material: Material, potionCase: PotionCase): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta as PotionMeta
        val baseType = resolveBasePotionType(potionCase.baseName, potionCase.potion) ?: return item
        if (hasBasePotionType) {
            val resolvedName = when {
                potionCase.isStrong -> "STRONG_${baseType.name}"
                potionCase.isExtended -> "LONG_${baseType.name}"
                else -> baseType.name
            }
            val potionType = runCatching { PotionType.valueOf(resolvedName) }.getOrElse { baseType }
            meta.basePotionType = potionType
        } else {
            meta.basePotionData = PotionData(
                baseType,
                potionCase.isExtended,
                potionCase.isStrong
            )
        }
        item.itemMeta = meta
        return item
    }

    fun snapshotBase(meta: PotionMeta): PotionBaseSnapshot {
        return try {
            PotionBaseSnapshot(meta.basePotionType, false, false)
        } catch (e: NoSuchMethodError) {
            val baseData = meta.basePotionData
            if (baseData == null) {
                PotionBaseSnapshot(null, false, false)
            } else {
                PotionBaseSnapshot(baseData.type, baseData.isUpgraded, baseData.isExtended)
            }
        }
    }

    fun expectedEffectTypes(potionType: PotionType): List<PotionEffectType> {
        return try {
            potionType.potionEffects.map { it.type }
        } catch (e: NoSuchMethodError) {
            listOfNotNull(potionType.effectType)
        }
    }

    fun expectedAmplifier(baseName: String, isStrong: Boolean): Int {
        return if (isStrong) 1 else 0
    }

    fun assertAdjusted(item: ItemStack, baseName: String, isStrong: Boolean, potion: XPotion, expectedTicks: Int) {
        val meta = item.itemMeta as PotionMeta
        val potionType = resolveBasePotionType(baseName, potion) ?: error("Potion type missing for $baseName")
        val expectedTypes = expectedEffectTypes(potionType)
        val expectedAmp = expectedAmplifier(baseName, isStrong)

        meta.customEffects.shouldHaveSize(expectedTypes.size)
        expectedTypes.forEach { effectType ->
            val effect = meta.customEffects.firstOrNull { it.type == effectType }
            effect.shouldNotBe(null)
            effect!!.duration.shouldBeExactly(expectedTicks)
            if (baseName == "WEAKNESS") {
                effect.amplifier.shouldBeLessThanOrEqual(0)
            } else {
                effect.amplifier.shouldBeExactly(expectedAmp)
            }
        }

        val baseSnapshot = snapshotBase(meta)
        baseSnapshot.baseType.shouldBe(PotionType.WATER)
        baseSnapshot.isUpgraded.shouldBeFalse()
        baseSnapshot.isExtended.shouldBeFalse()
    }

    fun assertUnchanged(item: ItemStack, originalBase: PotionBaseSnapshot) {
        val meta = item.itemMeta as PotionMeta
        meta.customEffects.shouldHaveSize(0)
        val newBase = snapshotBase(meta)
        newBase.baseType.shouldBe(originalBase.baseType)
        newBase.isUpgraded.shouldBe(originalBase.isUpgraded)
        newBase.isExtended.shouldBe(originalBase.isExtended)
    }


    fun callConsume(item: ItemStack): ItemStack {
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
        return event.item
    }

    fun callThrow(item: ItemStack): ItemStack {
        val block = player.location.block
        val face = BlockFace.SELF
        val ctor = PlayerInteractEvent::class.java.constructors.firstOrNull { constructor ->
            val params = constructor.parameterTypes
            params.size == 6 &&
                Player::class.java.isAssignableFrom(params[0]) &&
                params[1] == Action::class.java &&
                ItemStack::class.java.isAssignableFrom(params[2]) &&
                params[3].name.endsWith("Block") &&
                params[4].name.endsWith("BlockFace") &&
                params[5] == EquipmentSlot::class.java
        }
        val event = if (ctor != null) {
            ctor.newInstance(player, Action.RIGHT_CLICK_AIR, item, block, face, EquipmentSlot.HAND) as PlayerInteractEvent
        } else {
            PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, item, block, face)
        }
        Bukkit.getPluginManager().callEvent(event)
        return event.item ?: item
    }

    fun callDispense(item: ItemStack): ItemStack {
        val block = player.world.getBlockAt(player.location.blockX, player.location.blockY, player.location.blockZ)
        val event = BlockDispenseEvent(block, item, Vector(0, 0, 0))
        Bukkit.getPluginManager().callEvent(event)
        return event.item
    }

    fun assertAdjustedOrUnchanged(
        adjusted: ItemStack,
        potionCase: PotionCase,
        originalBase: PotionBaseSnapshot,
        splash: Boolean
    ) {
        val expectedTicks = if (splash) potionCase.splashTicks else potionCase.drinkableTicks
        if (excludedPotionTypes.contains(debugName(potionCase.baseName, potionCase.isStrong, potionCase.isExtended))) {
            assertUnchanged(adjusted, originalBase)
        } else {
            assertAdjusted(adjusted, potionCase.baseName, potionCase.isStrong, potionCase.potion, expectedTicks)
        }
    }

    fun findSamplePotionCase(): PotionCase {
        return loadPotionCases().firstOrNull()
            ?: error("No configured potions available for this server version.")
    }

    suspend fun TestScope.withConfig(block: suspend TestScope.() -> Unit) {
        val enabled = ocm.config.getBoolean("old-potion-effects.enabled")
        val strengthSection = ocm.config.getConfigurationSection("old-potion-effects.strength")
        val weaknessSection = ocm.config.getConfigurationSection("old-potion-effects.weakness")
        val drinkSection = ocm.config.getConfigurationSection("old-potion-effects.potion-durations.drinkable")
        val splashSection = ocm.config.getConfigurationSection("old-potion-effects.potion-durations.splash")
        val alwaysEnabledModules = ocm.config.getStringList("always_enabled_modules")
        val disabledModules = ocm.config.getStringList("disabled_modules")
        val modesetSection = ocm.config.getConfigurationSection("modesets")

        val strengthSnapshot = strengthSection?.getValues(false) ?: emptyMap<String, Any>()
        val weaknessSnapshot = weaknessSection?.getValues(false) ?: emptyMap<String, Any>()
        val drinkSnapshot = drinkSection?.getKeys(false)?.associateWith { drinkSection.get(it) } ?: emptyMap()
        val splashSnapshot = splashSection?.getKeys(false)?.associateWith { splashSection.get(it) } ?: emptyMap()
        val modesetSnapshot = modesetSection?.getKeys(false)?.associateWith { key ->
            ocm.config.getStringList("modesets.$key")
        } ?: emptyMap()

        try {
            block()
        } finally {
            ocm.config.set("old-potion-effects.enabled", enabled)
            strengthSnapshot.forEach { (key, value) ->
                ocm.config.set("old-potion-effects.strength.$key", value)
            }
            weaknessSnapshot.forEach { (key, value) ->
                ocm.config.set("old-potion-effects.weakness.$key", value)
            }
            drinkSnapshot.forEach { (key, value) ->
                ocm.config.set("old-potion-effects.potion-durations.drinkable.$key", value)
            }
            splashSnapshot.forEach { (key, value) ->
                ocm.config.set("old-potion-effects.potion-durations.splash.$key", value)
            }
            ocm.config.set("always_enabled_modules", alwaysEnabledModules)
            ocm.config.set("disabled_modules", disabledModules)
            modesetSnapshot.forEach { (key, list) ->
                ocm.config.set("modesets.$key", list)
            }
            modesetSection?.getKeys(false)
                ?.filterNot { modesetSnapshot.containsKey(it) }
                ?.forEach { key -> ocm.config.set("modesets.$key", null) }
            ocm.saveConfig()
            Config.reload()
        }
    }

    extensions(MainThreadDispatcherExtension(testPlugin))

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

    beforeSpec {
        runSync {
            val world = Bukkit.getServer().getWorld("world")
            val location = Location(world, 0.0, 100.0, 0.0)
            fakePlayer = FakePlayer(testPlugin)
            fakePlayer.spawn(location)
            player = checkNotNull(Bukkit.getPlayer(fakePlayer.uuid))
            player.isOp = true
            val playerData = getPlayerData(player.uniqueId)
            playerData.setModesetForWorld(player.world.uid, "old")
            setPlayerData(player.uniqueId, playerData)
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
            val playerData = getPlayerData(player.uniqueId)
            playerData.setModesetForWorld(player.world.uid, "old")
            setPlayerData(player.uniqueId, playerData)
        }
    }

    context("Drinkable potions") {
        test("configured drinkable potions are adjusted when duration is loaded") {
            val cases = loadPotionCases()
            cases.isNotEmpty().shouldBeTrue()
            cases.forEach { potionCase ->
                val item = createPotionItem(Material.POTION, potionCase)
                val meta = item.itemMeta as PotionMeta
                val originalBase = snapshotBase(meta)
                val adjusted = callConsume(item)
                assertAdjustedOrUnchanged(adjusted, potionCase, originalBase, splash = false)
            }
        }
    }

    context("Weakness neutralisation") {
        test("weakness potion does not reduce attack damage") {
            val weaknessCase = loadPotionCases().firstOrNull { it.baseName == "WEAKNESS" }
                ?: return@test
            val item = createPotionItem(Material.POTION, weaknessCase)
            val adjusted = callConsume(item)
            val meta = adjusted.itemMeta as PotionMeta
            val effect = meta.customEffects.firstOrNull { it.type == PotionEffectType.WEAKNESS }
                ?: error("Weakness effect missing from potion meta")

            val attackAttribute = XAttribute.ATTACK_DAMAGE.get()
                ?: error("Attack damage attribute not available")
            var baseDamage = 0.0
            runSync {
                player.inventory.setItemInMainHand(ItemStack(Material.AIR))
                player.activePotionEffects.forEach { player.removePotionEffect(it.type) }
                baseDamage = player.getAttribute(attackAttribute)?.value
                    ?: error("Attack damage attribute missing on player")
                player.addPotionEffect(effect, true)
            }
            delay(50)
            var afterDamage = 0.0
            runSync {
                afterDamage = player.getAttribute(attackAttribute)?.value
                    ?: error("Attack damage attribute missing on player")
            }
            afterDamage.shouldBe(baseDamage.plusOrMinus(0.0001))
        }

        test("direct weakness effect does not reduce attack damage") {
            val attackAttribute = XAttribute.ATTACK_DAMAGE.get()
                ?: error("Attack damage attribute not available")
            var baseDamage = 0.0
            runSync {
                player.inventory.setItemInMainHand(ItemStack(Material.AIR))
                player.activePotionEffects.forEach { player.removePotionEffect(it.type) }
                baseDamage = player.getAttribute(attackAttribute)?.value
                    ?: error("Attack damage attribute missing on player")
                player.addPotionEffect(PotionEffect(PotionEffectType.WEAKNESS, 200, -1), true)
            }
            delay(50)
            var afterDamage = 0.0
            runSync {
                afterDamage = player.getAttribute(attackAttribute)?.value
                    ?: error("Attack damage attribute missing on player")
            }
            afterDamage.shouldBe(baseDamage.plusOrMinus(0.0001))
        }
    }

    context("Weakness damage event diagnostic") {
        test("vanilla damage event for weakness + low damage + no-damage window") {
            lateinit var attacker: Player
            lateinit var victim: LivingEntity
            var attackerFake: FakePlayer? = null
            val events = mutableListOf<EntityDamageByEntityEvent>()

            val listener = object : Listener {
                @EventHandler
                fun onDamage(event: EntityDamageByEntityEvent) {
                    if (event.entity.uniqueId == victim.uniqueId &&
                        event.damager.uniqueId == attacker.uniqueId
                    ) {
                        events.add(event)
                    }
                }
            }

            try {
                runSync {
                    val world = checkNotNull(Bukkit.getServer().getWorld("world"))
                    val attackerLocation = Location(world, 0.0, 100.0, 0.0).apply { yaw = 0f; pitch = 0f }
                    val victimLocation = Location(world, 1.2, 100.0, 0.0)

                    attackerFake = FakePlayer(testPlugin)
                    attackerFake!!.spawn(attackerLocation)
                    attacker = checkNotNull(Bukkit.getPlayer(attackerFake!!.uuid))
                    attacker.isOp = true
                    attacker.inventory.clear()
                    attacker.activePotionEffects.forEach { attacker.removePotionEffect(it.type) }
                    attacker.gameMode = GameMode.SURVIVAL

                    val attackerData = getPlayerData(attacker.uniqueId)
                    attackerData.setModesetForWorld(attacker.world.uid, "old")
                    setPlayerData(attacker.uniqueId, attackerData)

                    victim = world.spawn(victimLocation, org.bukkit.entity.Cow::class.java)
                    victim.maximumNoDamageTicks = 20
                    victim.noDamageTicks = 0
                    victim.isInvulnerable = false
                    victim.health = victim.maxHealth

                    Bukkit.getPluginManager().registerEvents(listener, testPlugin)
                }
                delay(200)

                fun attackDamage(): Double {
                    val attribute = XAttribute.ATTACK_DAMAGE.get()
                        ?: error("Attack damage attribute not available")
                    return attacker.getAttribute(attribute)?.value ?: 0.0
                }

                fun prepareWeapon(item: ItemStack) {
                    val meta = item.itemMeta ?: return
                    @Suppress("DEPRECATION") // Deprecated constructor kept for older server compatibility in tests.
                    val speedModifier = createAttributeModifier(
                        name = "speed",
                        amount = 1000.0,
                        operation = AttributeModifier.Operation.ADD_NUMBER,
                        slot = EquipmentSlot.HAND
                    )
                    val attackSpeedAttribute = XAttribute.ATTACK_SPEED.get() ?: return
                    addAttributeModifierCompat(meta, attackSpeedAttribute, speedModifier)
                    item.itemMeta = meta
                }

                fun applyAttackDamageModifiers(item: ItemStack) {
                    val attackDamageAttribute = XAttribute.ATTACK_DAMAGE.get() ?: return
                    val attackAttribute = attacker.getAttribute(attackDamageAttribute) ?: return
                    val modifiers = getDefaultAttributeModifiersCompat(item, EquipmentSlot.HAND, attackDamageAttribute)
                    modifiers.forEach { modifier ->
                        attackAttribute.removeModifier(modifier)
                        attackAttribute.addModifier(modifier)
                    }
                }

                suspend fun record(label: String, expectedDamage: Double, action: () -> Unit): Boolean {
                    val before = events.size
                    runSync {
                        Bukkit.getScheduler().runTask(testPlugin, Runnable { action() })
                    }
                    delay(150)
                    val fired = events.size > before
                    testPlugin.logger.info(
                        "Weakness diagnostic [$label] fired=$fired " +
                            "noDamageTicks=${victim.noDamageTicks} lastDamage=${victim.lastDamage} " +
                            "eventType=${events.lastOrNull()?.javaClass?.simpleName} " +
                            "cause=${events.lastOrNull()?.cause} " +
                            "eventDamage=${events.lastOrNull()?.damage} " +
                            "finalDamage=${events.lastOrNull()?.finalDamage} " +
                            "damagerType=${events.lastOrNull()?.damager?.javaClass?.simpleName} " +
                            "damagerId=${events.lastOrNull()?.damager?.uniqueId} " +
                            "inputDamage=$expectedDamage"
                    )
                    return fired
                }

                runSync {
                    val weapon = ItemStack(Material.DIAMOND_SWORD)
                    prepareWeapon(weapon)
                    attacker.inventory.setItemInMainHand(weapon)
                    applyAttackDamageModifiers(weapon)
                    attacker.updateInventory()
                    attacker.isInvulnerable = false
                    attacker.health = attacker.maxHealth
                    victim.noDamageTicks = 0
                    victim.lastDamage = 0.0
                }
                delay(100)
                val baselineDamage = attackDamage()
                waitForAttackReady(attacker)
                record("baseline", baselineDamage) {
                    attackCompat(attacker, victim)
                }

                runSync {
                    attacker.activePotionEffects.forEach { attacker.removePotionEffect(it.type) }
                    attacker.addPotionEffect(PotionEffect(XPotion.WEAKNESS.get()!!, 200, 0))
                    val lowItem = ItemStack(Material.STONE_SWORD)
                    prepareWeapon(lowItem)
                    attacker.inventory.setItemInMainHand(lowItem)
                    applyAttackDamageModifiers(lowItem)
                    attacker.updateInventory()
                    attacker.isInvulnerable = false
                    attacker.health = attacker.maxHealth
                    victim.noDamageTicks = 0
                    victim.lastDamage = 0.0
                }
                delay(100)
                val weakDamage = attackDamage()
                waitForAttackReady(attacker)
                record("weakness-no-invuln", weakDamage) {
                    attackCompat(attacker, victim)
                }

                runSync {
                    victim.noDamageTicks = victim.maximumNoDamageTicks
                    victim.lastDamage = 20.0
                }
                delay(100)
                waitForAttackReady(attacker)
                record("weakness-invuln", weakDamage) {
                    attackCompat(attacker, victim)
                }
            } finally {
                HandlerList.unregisterAll(listener)
                runSync {
                    attackerFake?.removePlayer()
                    victim.remove()
                }
            }
        }
    }

    context("Splash potions") {
        test("player throws splash potions with module durations") {
            val cases = loadPotionCases()
            cases.forEach { potionCase ->
                val item = createPotionItem(Material.SPLASH_POTION, potionCase)
                val meta = item.itemMeta as PotionMeta
                val originalBase = snapshotBase(meta)
                val adjusted = callThrow(item)
                assertAdjustedOrUnchanged(adjusted, potionCase, originalBase, splash = true)
            }
        }

        test("dispenser does not mutate splash potions without setItem") {
            val cases = loadPotionCases()
            cases.forEach { potionCase ->
                val item = createPotionItem(Material.SPLASH_POTION, potionCase)
                val meta = item.itemMeta as PotionMeta
                val originalBase = snapshotBase(meta)
                val adjusted = callDispense(item)
                assertUnchanged(adjusted, originalBase)
            }
        }
    }

    context("Lingering potions") {
        test("player throws lingering potions with module splash durations") {
            val cases = loadPotionCases()
            cases.forEach { potionCase ->
                val item = createPotionItem(Material.LINGERING_POTION, potionCase)
                val meta = item.itemMeta as PotionMeta
                val originalBase = snapshotBase(meta)
                val adjusted = callThrow(item)
                assertAdjustedOrUnchanged(adjusted, potionCase, originalBase, splash = true)
            }
        }

        test("dispenser does not mutate lingering potions without setItem") {
            val cases = loadPotionCases()
            cases.forEach { potionCase ->
                val item = createPotionItem(Material.LINGERING_POTION, potionCase)
                val meta = item.itemMeta as PotionMeta
                val originalBase = snapshotBase(meta)
                val adjusted = callDispense(item)
                assertUnchanged(adjusted, originalBase)
            }
        }
    }

    context("Excluded potions") {
        test("excluded potion types are not modified") {
            excludedPotionTypes.forEach { name ->
                val potionType = runCatching { PotionType.valueOf(name) }.getOrNull() ?: return@forEach
                val item = ItemStack(Material.POTION)
                val meta = item.itemMeta as PotionMeta
                val originalBase = runCatching {
                    try {
                        meta.basePotionType = potionType
                    } catch (e: NoSuchMethodError) {
                        meta.basePotionData = PotionData(potionType, false, false)
                    }
                    snapshotBase(meta)
                }.getOrElse { return@forEach }
                item.itemMeta = meta

                val adjusted = callConsume(item)
                assertUnchanged(adjusted, originalBase)
            }
        }
    }

    context("Missing config") {
        test("missing potion entry leaves potion unchanged") {
            withConfig {
                val potionCase = findSamplePotionCase()
                ocm.config.set("old-potion-effects.potion-durations.drinkable.${potionCase.key}", null)
                ocm.config.set("old-potion-effects.potion-durations.splash.${potionCase.key}", null)
                module.reload()

                val item = createPotionItem(Material.POTION, potionCase)
                val originalBase = snapshotBase(item.itemMeta as PotionMeta)
                val adjusted = callConsume(item)
                assertUnchanged(adjusted, originalBase)
            }
        }
    }

    context("Module disabled") {
        test("disabled via modeset leaves potions unchanged") {
            val potionCase = findSamplePotionCase()
            val playerData = getPlayerData(player.uniqueId)
            playerData.setModesetForWorld(player.world.uid, "new")
            setPlayerData(player.uniqueId, playerData)
            val item = createPotionItem(Material.POTION, potionCase)
            val originalBase = snapshotBase(item.itemMeta as PotionMeta)
            val adjusted = callConsume(item)
            assertUnchanged(adjusted, originalBase)
        }

        test("disabled via config leaves potions unchanged") {
            withConfig {
                val potionCase = findSamplePotionCase()
                val disabled = ocm.config.getStringList("disabled_modules")
                    .filterNot { it.equals("old-potion-effects", true) }
                    .toMutableList()
                disabled.add("old-potion-effects")
                ocm.config.set("disabled_modules", disabled)
                ocm.config.set(
                    "always_enabled_modules",
                    ocm.config.getStringList("always_enabled_modules")
                        .filterNot { it.equals("old-potion-effects", true) }
                )
                val modesetsSection = ocm.config.getConfigurationSection("modesets")
                    ?: error("Missing 'modesets' section in config")
                modesetsSection.getKeys(false).forEach { key ->
                    val modules = ocm.config.getStringList("modesets.$key")
                        .filterNot { it.equals("old-potion-effects", true) }
                    ocm.config.set("modesets.$key", modules)
                }
                ocm.saveConfig()
                Config.reload()

                val item = createPotionItem(Material.POTION, potionCase)
                val originalBase = snapshotBase(item.itemMeta as PotionMeta)
                val adjusted = callConsume(item)
                assertUnchanged(adjusted, originalBase)
            }
        }
    }

    context("Strength and weakness modifiers") {
        test("damage modifiers are applied from config") {
            withConfig {
                val strengthModifier = 2.4
                val weaknessModifier = -0.75
                ocm.config.set("old-potion-effects.strength.modifier", strengthModifier)
                ocm.config.set("old-potion-effects.strength.multiplier", false)
                ocm.config.set("old-potion-effects.strength.addend", true)
                ocm.config.set("old-potion-effects.weakness.modifier", weaknessModifier)
                ocm.config.set("old-potion-effects.weakness.multiplier", true)
                module.reload()

                player.addPotionEffect(PotionEffect(XPotion.STRENGTH.get()!!, 200, 0))
                player.addPotionEffect(PotionEffect(XPotion.WEAKNESS.get()!!, 200, 0))
                val defender = player

                val event = OCMEntityDamageByEntityEvent(
                    player,
                    defender,
                    EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                    4.0
                )
                Bukkit.getPluginManager().callEvent(event)

                event.strengthModifier.shouldBe(strengthModifier)
                event.isStrengthModifierMultiplier.shouldBeFalse()
                event.isStrengthModifierAddend.shouldBeTrue()
                event.weaknessModifier.shouldBe(weaknessModifier)
                event.isWeaknessModifierMultiplier.shouldBeTrue()
                event.weaknessLevel.shouldBe(1)
            }
        }

        test("weakness II is capped to level one for old modifier logic") {
            withConfig {
                val weaknessModifier = -0.5
                ocm.config.set("old-potion-effects.weakness.modifier", weaknessModifier)
                ocm.config.set("old-potion-effects.weakness.multiplier", false)
                module.reload()

                val weakness = XPotion.WEAKNESS.get() ?: error("Weakness potion missing")
                runSync {
                    player.addPotionEffect(PotionEffect(weakness, 200, 1), true)
                }

                val event = OCMEntityDamageByEntityEvent(
                    player,
                    player,
                    EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                    4.0
                )
                Bukkit.getPluginManager().callEvent(event)

                event.hasWeakness().shouldBeTrue()
                event.weaknessModifier.shouldBe(weaknessModifier)
                event.isWeaknessModifierMultiplier.shouldBeFalse()
                event.weaknessLevel.shouldBe(1)
            }
        }

        test("high amplifier weakness does not distort base damage reconstruction") {
            withConfig {
                val weakness = XPotion.WEAKNESS.get() ?: error("Weakness potion missing")
                var baseLevel0 = 0.0
                var baseLevel67 = 0.0

                runSync {
                    player.activePotionEffects.forEach { player.removePotionEffect(it.type) }
                    player.addPotionEffect(PotionEffect(weakness, 200, 0), true)
                }

                val eventLevel0 = OCMEntityDamageByEntityEvent(
                    player,
                    player,
                    EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                    4.0
                )
                Bukkit.getPluginManager().callEvent(eventLevel0)
                baseLevel0 = eventLevel0.baseDamage

                runSync {
                    player.activePotionEffects.forEach { player.removePotionEffect(it.type) }
                    player.addPotionEffect(PotionEffect(weakness, 200, 67), true)
                }

                val eventLevel67 = OCMEntityDamageByEntityEvent(
                    player,
                    player,
                    EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                    4.0
                )
                Bukkit.getPluginManager().callEvent(eventLevel67)
                baseLevel67 = eventLevel67.baseDamage

                baseLevel67.shouldBe(baseLevel0.plusOrMinus(0.0001))
            }
        }
    }
})
