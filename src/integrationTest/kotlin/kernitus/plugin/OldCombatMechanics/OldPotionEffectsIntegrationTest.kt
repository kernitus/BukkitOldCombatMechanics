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
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kernitus.plugin.OldCombatMechanics.module.ModuleOldPotionEffects
import kernitus.plugin.OldCombatMechanics.utilities.damage.OCMEntityDamageByEntityEvent
import kernitus.plugin.OldCombatMechanics.utilities.potions.PotionDurations
import kernitus.plugin.OldCombatMechanics.utilities.potions.PotionEffectTypeCompat
import kernitus.plugin.OldCombatMechanics.utilities.potions.PotionTypeCompat
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockDispenseEvent
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
        "UNCRAFTABLE"
    )

    data class PotionCase(
        val key: String,
        val typeCompat: PotionTypeCompat,
        val drinkableTicks: Int,
        val splashTicks: Int
    )

    data class PotionBaseSnapshot(
        val baseType: PotionType?,
        val isUpgraded: Boolean,
        val isExtended: Boolean
    )

    fun potionSupports(typeCompat: PotionTypeCompat): Boolean {
        val potionType = typeCompat.type ?: return false
        return runCatching {
            PotionData(potionType, typeCompat.isLong, typeCompat.isStrong)
            true
        }.getOrElse { false }
    }

    fun loadPotionCases(): List<PotionCase> {
        val drinkable = ocm.config.getConfigurationSection("old-potion-effects.potion-durations.drinkable")
            ?: return emptyList()
        val splash = ocm.config.getConfigurationSection("old-potion-effects.potion-durations.splash")
            ?: return emptyList()

        return drinkable.getKeys(false).mapNotNull { key ->
            if (!splash.isInt(key)) return@mapNotNull null
            val typeCompat = runCatching { PotionTypeCompat(key.uppercase()) }.getOrNull() ?: return@mapNotNull null
            if (excludedPotionTypes.contains(typeCompat.newName)) return@mapNotNull null
            if (!potionSupports(typeCompat)) return@mapNotNull null
            PotionCase(
                key = key,
                typeCompat = typeCompat,
                drinkableTicks = drinkable.getInt(key) * 20,
                splashTicks = splash.getInt(key) * 20
            )
        }
    }

    fun createPotionItem(material: Material, potionCase: PotionCase): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta as PotionMeta
        val potionType = potionCase.typeCompat.type ?: return item
        try {
            meta.basePotionType = potionType
        } catch (e: NoSuchMethodError) {
            meta.basePotionData = PotionData(
                potionType,
                potionCase.typeCompat.isLong,
                potionCase.typeCompat.isStrong
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

    fun expectedAmplifier(typeCompat: PotionTypeCompat): Int {
        return when {
            typeCompat.newName == "WEAKNESS" -> -1
            typeCompat.isStrong -> 1
            else -> 0
        }
    }

    fun assertAdjusted(item: ItemStack, typeCompat: PotionTypeCompat, expectedTicks: Int) {
        val meta = item.itemMeta as PotionMeta
        val potionType = typeCompat.type ?: error("Potion type missing for ${typeCompat.newName}")
        val expectedTypes = expectedEffectTypes(potionType)
        val expectedAmp = expectedAmplifier(typeCompat)

        meta.customEffects.shouldHaveSize(expectedTypes.size)
        expectedTypes.forEach { effectType ->
            val effect = meta.customEffects.firstOrNull { it.type == effectType }
            effect.shouldNotBe(null)
            effect!!.duration.shouldBeExactly(expectedTicks)
            effect.amplifier.shouldBeExactly(expectedAmp)
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

    fun currentDurations(): Map<PotionTypeCompat, PotionDurations> {
        val field = ModuleOldPotionEffects::class.java.getDeclaredField("durations")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return field.get(module) as? Map<PotionTypeCompat, PotionDurations> ?: emptyMap()
    }

    fun configuredDuration(typeCompat: PotionTypeCompat, splash: Boolean): Int? {
        val durations = currentDurations()
        val potionDurations = durations[typeCompat] ?: return null
        return if (splash) potionDurations.splash() else potionDurations.drinkable()
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
        baseType: PotionTypeCompat,
        originalBase: PotionBaseSnapshot,
        splash: Boolean
    ) {
        val expectedTicks = configuredDuration(baseType, splash)
        if (expectedTicks == null || excludedPotionTypes.contains(baseType.newName)) {
            assertUnchanged(adjusted, originalBase)
        } else {
            assertAdjusted(adjusted, baseType, expectedTicks)
        }
    }

    fun findSamplePotionCase(): PotionCase {
        val durations = currentDurations()
        return loadPotionCases().firstOrNull { durations.containsKey(it.typeCompat) }
            ?: error("No configured potions available for this server version.")
    }

    suspend fun TestScope.withConfig(block: suspend TestScope.() -> Unit) {
        val enabled = ocm.config.getBoolean("old-potion-effects.enabled")
        val strengthSection = ocm.config.getConfigurationSection("old-potion-effects.strength")
        val weaknessSection = ocm.config.getConfigurationSection("old-potion-effects.weakness")
        val drinkSection = ocm.config.getConfigurationSection("old-potion-effects.potion-durations.drinkable")
        val splashSection = ocm.config.getConfigurationSection("old-potion-effects.potion-durations.splash")

        val strengthSnapshot = strengthSection?.getValues(false) ?: emptyMap<String, Any>()
        val weaknessSnapshot = weaknessSection?.getValues(false) ?: emptyMap<String, Any>()
        val drinkSnapshot = drinkSection?.getKeys(false)?.associateWith { drinkSection.get(it) } ?: emptyMap()
        val splashSnapshot = splashSection?.getKeys(false)?.associateWith { splashSection.get(it) } ?: emptyMap()

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
            module.reload()
            ModuleLoader.toggleModules()
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
                val baseType = PotionTypeCompat.fromPotionMeta(meta)
                val originalBase = snapshotBase(meta)
                val adjusted = callConsume(item)
                assertAdjustedOrUnchanged(adjusted, baseType, originalBase, splash = false)
            }
        }
    }

    context("Weakness amplifier") {
        test("weakness stays at -1 on item and player") {
            val weaknessCase = loadPotionCases().firstOrNull { it.typeCompat.newName == "WEAKNESS" }
                ?: return@test
            val item = createPotionItem(Material.POTION, weaknessCase)
            val adjusted = callConsume(item)

            val meta = adjusted.itemMeta as PotionMeta
            val effect = meta.customEffects.firstOrNull { it.type == PotionEffectType.WEAKNESS }
                ?: error("Weakness effect missing from potion meta")
            effect.amplifier.shouldBeExactly(-1)

            player.addPotionEffect(effect)
            val applied = player.getPotionEffect(PotionEffectType.WEAKNESS)
            applied.shouldNotBe(null)
            applied!!.amplifier.shouldBeExactly(-1)
        }
    }

    context("Splash potions") {
        test("player throws splash potions with module durations") {
            val cases = loadPotionCases()
            cases.forEach { potionCase ->
                val item = createPotionItem(Material.SPLASH_POTION, potionCase)
                val meta = item.itemMeta as PotionMeta
                val baseType = PotionTypeCompat.fromPotionMeta(meta)
                val originalBase = snapshotBase(meta)
                val adjusted = callThrow(item)
                assertAdjustedOrUnchanged(adjusted, baseType, originalBase, splash = true)
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
                val baseType = PotionTypeCompat.fromPotionMeta(meta)
                val originalBase = snapshotBase(meta)
                val adjusted = callThrow(item)
                assertAdjustedOrUnchanged(adjusted, baseType, originalBase, splash = true)
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
                val typeCompat = runCatching { PotionTypeCompat(name) }.getOrNull() ?: return@forEach
                val potionType = typeCompat.type ?: return@forEach
                val item = ItemStack(Material.POTION)
                val meta = item.itemMeta as PotionMeta
                val originalBase = runCatching {
                    try {
                        meta.basePotionType = potionType
                    } catch (e: NoSuchMethodError) {
                        meta.basePotionData = PotionData(potionType, typeCompat.isLong, typeCompat.isStrong)
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
                ocm.config.set("old-potion-effects.enabled", false)
                module.reload()
                ModuleLoader.toggleModules()

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

                player.addPotionEffect(PotionEffect(PotionEffectTypeCompat.STRENGTH.get(), 200, 0))
                player.addPotionEffect(PotionEffect(PotionEffectType.WEAKNESS, 200, 0))
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
    }
})
