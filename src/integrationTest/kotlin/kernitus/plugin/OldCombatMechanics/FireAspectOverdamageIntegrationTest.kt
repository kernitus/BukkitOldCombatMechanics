/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics

import com.cryptomorin.xseries.XAttribute
import com.cryptomorin.xseries.XEnchantment
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.kotest.assertions.withClue
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector
import kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage.getPlayerData
import kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage.setPlayerData
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.attribute.AttributeModifier
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Vector
import kotlinx.coroutines.delay
import java.util.concurrent.Callable
import kotlin.math.abs

@OptIn(ExperimentalKotest::class)
class FireAspectOverdamageIntegrationTest : FunSpec({
    val plugin = JavaPlugin.getPlugin(OCMTestMain::class.java)

    extensions(MainThreadDispatcherExtension(plugin))

    fun runSync(action: () -> Unit) {
        if (Bukkit.isPrimaryThread()) {
            action()
        } else {
            Bukkit.getScheduler().callSyncMethod(plugin, Callable {
                action()
                null
            }).get()
        }
    }

    suspend fun delayTicks(ticks: Long) {
        delay(ticks * 50L)
    }

    val isLegacyServer = !kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector.versionIsNewerOrEqualTo(1, 13, 0)

    fun needsAttackWarmup(attacker: Player): Boolean = isLegacyServer

    data class AttackSample(
        val cancelled: Boolean,
        val damage: Double,
        val finalDamage: Double,
        val noDamageTicks: Int,
        val lastDamage: Double
    )

    data class FireTickSample(val cancelled: Boolean, val finalDamage: Double)

    suspend fun waitForFireTick(samples: List<FireTickSample>, timeoutTicks: Long = 120) {
        repeat(timeoutTicks.toInt()) {
            if (samples.isNotEmpty()) return
            delayTicks(1)
        }
        error("Expected a fire tick event within $timeoutTicks ticks, but none fired.")
    }

    fun prepareWeapon(item: ItemStack) {
        val meta = item.itemMeta ?: return
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

    fun equip(player: Player, item: ItemStack) {
        prepareWeapon(item)
        val attackSpeedAttribute = XAttribute.ATTACK_SPEED.get()
        if (attackSpeedAttribute != null) {
            player.getAttribute(attackSpeedAttribute)?.baseValue = 1000.0
        }
        if (isLegacyServer) {
            // Legacy fake players can miss item-based attack damage modifiers until after the first swing.
            // Force a stable baseline so overdamage tests are not dependent on attribute refresh timing.
            val attackDamageAttribute = XAttribute.ATTACK_DAMAGE.get()
            val attribute = attackDamageAttribute?.let { player.getAttribute(it) }
            if (attribute != null) {
                attribute.baseValue = when (item.type) {
                    Material.DIAMOND_SWORD -> 7.0
                    else -> attribute.baseValue
                }
            }
        }
        player.inventory.setItemInMainHand(item)
        player.updateInventory()
    }

    fun spawnPlayer(location: Location): Pair<FakePlayer, Player> {
        val fake = FakePlayer(plugin)
        fake.spawn(location)
        val player = checkNotNull(Bukkit.getPlayer(fake.uuid))
        player.gameMode = GameMode.SURVIVAL
        player.isInvulnerable = false
        player.inventory.clear()
        player.activePotionEffects.forEach { player.removePotionEffect(it.type) }
        val playerData = getPlayerData(player.uniqueId)
        playerData.setModesetForWorld(player.world.uid, "old")
        setPlayerData(player.uniqueId, playerData)
        return fake to player
    }

    fun spawnVictim(location: Location): LivingEntity {
        val world = location.world ?: error("World missing for victim spawn")
        return world.spawn(location, org.bukkit.entity.Zombie::class.java).apply {
            maximumNoDamageTicks = 100
            noDamageTicks = 0
            isInvulnerable = false
            health = maxHealth
        }
    }

    fun prepareVictimState(victim: LivingEntity, maxHealthOverride: Double? = null) {
        if (maxHealthOverride != null) {
            val maxHealthAttribute = XAttribute.MAX_HEALTH.get()
            if (maxHealthAttribute != null) {
                victim.getAttribute(maxHealthAttribute)?.baseValue = maxHealthOverride
            } else {
                victim.maxHealth = maxHealthOverride
            }
        }
        victim.maximumNoDamageTicks = 100
        victim.noDamageTicks = 0
        victim.lastDamage = 0.0
        victim.fireTicks = 0
        victim.isInvulnerable = false
        victim.health = victim.maxHealth
    }

    fun ensureBurning(victim: LivingEntity, minTicks: Int = 200) {
        if (victim.fireTicks < minTicks) {
            victim.fireTicks = minTicks
        }
    }

    fun requireInvulnerabilityWindow(victim: LivingEntity) {
        val maxTicks = victim.maximumNoDamageTicks
        if (victim.noDamageTicks.toDouble() <= maxTicks / 2.0) {
            error(
                "Expected to still be inside the invulnerability window, but noDamageTicks=" +
                    "${victim.noDamageTicks} maxNoDamageTicks=$maxTicks"
            )
        }
    }

    fun applyProtectionArmour(entity: LivingEntity) {
        val protection = XEnchantment.PROTECTION.get()
        val armour = arrayOf(
            ItemStack(Material.DIAMOND_BOOTS),
            ItemStack(Material.DIAMOND_LEGGINGS),
            ItemStack(Material.DIAMOND_CHESTPLATE),
            ItemStack(Material.DIAMOND_HELMET)
        )
        if (protection != null) {
            armour.forEach { it.addUnsafeEnchantment(protection, 4) }
        }
        if (entity is Player) {
            entity.inventory.setArmorContents(armour)
            return
        }
        entity.equipment?.armorContents = armour
    }

    fun applyFireProtectionArmour(entity: LivingEntity) {
        val fireProtection = XEnchantment.FIRE_PROTECTION.get()
        val armour = arrayOf(
            ItemStack(Material.DIAMOND_BOOTS),
            ItemStack(Material.DIAMOND_LEGGINGS),
            ItemStack(Material.DIAMOND_CHESTPLATE),
            ItemStack(Material.DIAMOND_HELMET)
        )
        if (fireProtection != null) {
            armour.forEach { it.addUnsafeEnchantment(fireProtection, 4) }
        }
        if (entity is Player) {
            entity.inventory.setArmorContents(armour)
            return
        }
        entity.equipment?.armorContents = armour
    }

    fun applyFireResistance(entity: LivingEntity, durationTicks: Int = 20 * 60) {
        entity.addPotionEffect(PotionEffect(PotionEffectType.FIRE_RESISTANCE, durationTicks, 0), true)
    }

    fun disableAiIfPossible(entity: LivingEntity) {
        runCatching {
            val method = entity.javaClass.methods.firstOrNull { m ->
                m.name == "setAI" &&
                    m.parameterTypes.size == 1 &&
                    (m.parameterTypes[0] == java.lang.Boolean.TYPE || m.parameterTypes[0] == java.lang.Boolean::class.java)
            } ?: return
            method.invoke(entity, false)
        }
    }

    fun stabilise(entity: Entity, location: Location) {
        entity.fallDistance = 0.0f
        entity.teleport(location)
        entity.velocity = Vector(0, 0, 0)
    }

    data class BlockTypeSnapshot(val location: Location, val type: Material)

    fun placeWaterColumn(world: org.bukkit.World, base: Location, height: Int = 2): List<BlockTypeSnapshot> {
        val x = base.blockX
        val y = base.blockY
        val z = base.blockZ
        val snapshots = mutableListOf<BlockTypeSnapshot>()
        repeat(height) { dy ->
            val block = world.getBlockAt(x, y + dy, z)
            snapshots.add(BlockTypeSnapshot(block.location, block.type))
            block.type = Material.WATER
        }
        return snapshots
    }

    fun restoreBlocks(snapshots: List<BlockTypeSnapshot>) {
        snapshots.forEach { snap ->
            val world = snap.location.world ?: return@forEach
            world.getBlockAt(snap.location.blockX, snap.location.blockY, snap.location.blockZ).type = snap.type
        }
    }

    suspend fun countSuccessfulAttacks(
        attacker: Player,
        victim: LivingEntity,
        weapon: ItemStack,
        attempts: Int,
        tickDelay: Long
    ): Int {
        var count = 0
        val listener = object : Listener {
            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
            fun onDamage(event: EntityDamageByEntityEvent) {
                if (event.entity.uniqueId != victim.uniqueId) return
                if (event.damager.uniqueId != attacker.uniqueId) return
                if (event.cause != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return
                if (!event.isCancelled) {
                    count++
                }
            }
        }

        Bukkit.getPluginManager().registerEvents(listener, plugin)
        try {
            equip(attacker, weapon)
            repeat(attempts) {
                runSync {
                    attackCompat(attacker, victim)
                }
                delayTicks(tickDelay)
            }
        } finally {
            HandlerList.unregisterAll(listener)
        }
        return count
    }

    suspend fun collectFireTickDamages(
        victim: LivingEntity,
        expectedCount: Int,
        trigger: suspend () -> Unit
    ): List<Double> {
        val samples = mutableListOf<FireTickSample>()
        val listener = object : Listener {
            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
            fun onFireTick(event: EntityDamageEvent) {
                if (event.entity.uniqueId != victim.uniqueId) return
                val cause = event.cause
                if (cause != EntityDamageEvent.DamageCause.FIRE_TICK &&
                    cause != EntityDamageEvent.DamageCause.FIRE
                ) return
                samples.add(FireTickSample(event.isCancelled, event.finalDamage))
            }
        }

        Bukkit.getPluginManager().registerEvents(listener, plugin)
        try {
            trigger()
            repeat(200) {
                if (samples.size >= expectedCount) return@repeat
                delayTicks(1)
            }
        } finally {
            HandlerList.unregisterAll(listener)
        }

        if (samples.size < expectedCount) {
            error("Expected $expectedCount fire tick events, got ${samples.size}")
        }

        val nonCancelled = samples.filterNot { it.cancelled }
        if (nonCancelled.size < expectedCount) {
            error(
                "Expected $expectedCount non-cancelled fire tick events, got ${nonCancelled.size} " +
                    "(cancelled=${samples.count { it.cancelled }})"
            )
        }
        return nonCancelled.take(expectedCount).map { it.finalDamage }
    }

    fun snapshotOverdamageState(
        attacker: Player,
        victim: LivingEntity,
        attackSamples: List<AttackSample>,
        fireTickSamples: List<FireTickSample>
    ): String {
        var snapshot = ""
        runSync {
            val meta = victim.getMetadata("ocm-last-damage")
                .joinToString(prefix = "[", postfix = "]") { m ->
                    "${m.owningPlugin?.name ?: "?"}=${runCatching { m.asDouble() }.getOrNull()}"
                }
            val attackDetails = attackSamples.joinToString(prefix = "[", postfix = "]") {
                "{c=${it.cancelled}, dmg=${it.damage}, final=${it.finalDamage}, ndt=${it.noDamageTicks}, ld=${it.lastDamage}}"
            }
            snapshot =
                "attacks(total=${attackSamples.size}, ok=${attackSamples.count { !it.cancelled }}, " +
                    "cancelled=${attackSamples.count { it.cancelled }}, details=$attackDetails) " +
                    "fireTicks(total=${fireTickSamples.size}, ok=${fireTickSamples.count { !it.cancelled }}, " +
                    "cancelled=${fireTickSamples.count { it.cancelled }}) " +
                    "attacker(onGround=${attacker.isOnGround}, fallDistance=${attacker.fallDistance}, " +
                    "sprinting=${attacker.isSprinting}, velocity=${attacker.velocity}) " +
                    "victim(noDamageTicks=${victim.noDamageTicks}, maxNoDamageTicks=${victim.maximumNoDamageTicks}, " +
                    "lastDamage=${victim.lastDamage}, health=${victim.health}/${victim.maxHealth}, " +
                    "fireTicks=${victim.fireTicks}, meta=$meta)"
        }
        return snapshot
    }

    test("fire aspect does not bypass invulnerability cancellation") {
        if (!Reflector.versionIsNewerOrEqualTo(1, 12, 0)) return@test
        val attackSamples = mutableListOf<AttackSample>()
        val fireTickSamples = mutableListOf<FireTickSample>()
        lateinit var attacker: Player
        var victim: LivingEntity? = null
        var fakeAttacker: FakePlayer? = null

        val listener = object : Listener {
            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
            fun onDamage(event: EntityDamageByEntityEvent) {
                val currentVictim = victim ?: return
                if (event.entity.uniqueId == currentVictim.uniqueId &&
                    event.damager.uniqueId == attacker.uniqueId
                ) {
                    attackSamples.add(
                        AttackSample(
                            cancelled = event.isCancelled,
                            damage = event.damage,
                            finalDamage = event.finalDamage,
                            noDamageTicks = currentVictim.noDamageTicks,
                            lastDamage = currentVictim.lastDamage
                        )
                    )
                }
            }

            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
            fun onFireTick(event: EntityDamageEvent) {
                val cause = event.cause
                val currentVictim = victim ?: return
                if (event.entity.uniqueId == currentVictim.uniqueId &&
                    (cause == EntityDamageEvent.DamageCause.FIRE_TICK || cause == EntityDamageEvent.DamageCause.FIRE)
                ) {
                    fireTickSamples.add(FireTickSample(event.isCancelled, event.finalDamage))
                }
            }
        }

        try {
            runSync {
                val world = checkNotNull(Bukkit.getWorld("world"))
                val attackerLocation = Location(world, 0.0, 100.0, 0.0)
                val victimLocation = Location(world, 1.2, 100.0, 0.0)

                val (fakeA, playerA) = spawnPlayer(attackerLocation)
                fakeAttacker = fakeA
                attacker = playerA
                victim = spawnVictim(victimLocation)
                prepareVictimState(checkNotNull(victim))

                Bukkit.getPluginManager().registerEvents(listener, plugin)

                val weapon = ItemStack(Material.DIAMOND_SWORD)
                val fireAspect = XEnchantment.FIRE_ASPECT.get()
                if (fireAspect != null) {
                    weapon.addUnsafeEnchantment(fireAspect, 2)
                }
                equip(attacker, weapon)
            }

            // Vanilla 1.12 scales attack damage by cooldown *before* the Bukkit damage event is fired.
            // Fake players can start with an incomplete cooldown, so wait a few ticks to make the first hit stable.
            if (needsAttackWarmup(attacker)) {
                delayTicks(6)
            }
            runSync {
                attackCompat(attacker, checkNotNull(victim))
            }

            delayTicks(2)
            runSync {
                val fireEvent = EntityDamageEvent(
                    checkNotNull(victim),
                    EntityDamageEvent.DamageCause.FIRE_TICK,
                    1.0
                )
                Bukkit.getPluginManager().callEvent(fireEvent)
            }

            waitForFireTick(fireTickSamples, timeoutTicks = 5)
            runSync {
                requireInvulnerabilityWindow(checkNotNull(victim))
            }

            runSync {
                attackCompat(attacker, checkNotNull(victim))
            }

            delayTicks(2)

            val state = snapshotOverdamageState(attacker, checkNotNull(victim), attackSamples, fireTickSamples)
            withClue(state) {
                attackSamples.count { !it.cancelled }.shouldBeExactly(1)
            }
        } finally {
            HandlerList.unregisterAll(listener)
            runSync {
                fakeAttacker?.removePlayer()
                victim?.remove()
            }
        }
    }

    test("fire tick does not clear overdamage baseline") {
        val attackSamples = mutableListOf<AttackSample>()
        val fireTickSamples = mutableListOf<FireTickSample>()
        lateinit var attacker: Player
        var victim: LivingEntity? = null
        var fakeAttacker: FakePlayer? = null

        val listener = object : Listener {
            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
            fun onDamage(event: EntityDamageByEntityEvent) {
                val currentVictim = victim ?: return
                if (event.entity.uniqueId == currentVictim.uniqueId &&
                    event.damager.uniqueId == attacker.uniqueId
                ) {
                    attackSamples.add(
                        AttackSample(
                            cancelled = event.isCancelled,
                            damage = event.damage,
                            finalDamage = event.finalDamage,
                            noDamageTicks = currentVictim.noDamageTicks,
                            lastDamage = currentVictim.lastDamage
                        )
                    )
                }
            }

            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
            fun onFireTick(event: EntityDamageEvent) {
                val cause = event.cause
                val currentVictim = victim ?: return
                if (event.entity.uniqueId == currentVictim.uniqueId &&
                    (cause == EntityDamageEvent.DamageCause.FIRE_TICK || cause == EntityDamageEvent.DamageCause.FIRE)
                ) {
                    fireTickSamples.add(FireTickSample(event.isCancelled, event.finalDamage))
                }
            }
        }

        try {
            runSync {
                val world = checkNotNull(Bukkit.getWorld("world"))
                val attackerLocation = Location(world, 0.0, 100.0, 0.0)
                val victimLocation = Location(world, 1.2, 100.0, 0.0)

                val (fakeA, playerA) = spawnPlayer(attackerLocation)
                fakeAttacker = fakeA
                attacker = playerA
                victim = spawnVictim(victimLocation)
                prepareVictimState(checkNotNull(victim))

                Bukkit.getPluginManager().registerEvents(listener, plugin)

                val weapon = ItemStack(Material.DIAMOND_SWORD)
                equip(attacker, weapon)
            }

            if (needsAttackWarmup(attacker)) {
                delayTicks(6)
            }
            runSync {
                attackCompat(attacker, checkNotNull(victim))
            }

            delayTicks(2)
            runSync {
                val fireEvent = EntityDamageEvent(
                    checkNotNull(victim),
                    EntityDamageEvent.DamageCause.FIRE_TICK,
                    1.0
                )
                Bukkit.getPluginManager().callEvent(fireEvent)
            }

            waitForFireTick(fireTickSamples, timeoutTicks = 5)
            runSync {
                requireInvulnerabilityWindow(checkNotNull(victim))
            }

            runSync {
                attackCompat(attacker, checkNotNull(victim))
            }

            delayTicks(2)

            val state = snapshotOverdamageState(attacker, checkNotNull(victim), attackSamples, fireTickSamples)
            withClue(state) {
                attackSamples.count { !it.cancelled }.shouldBeExactly(1)
            }
        } finally {
            HandlerList.unregisterAll(listener)
            runSync {
                fakeAttacker?.removePlayer()
                victim?.remove()
            }
        }
    }

    test("fire aspect afterburn matches environmental fire tick damage (zombie)") {
        lateinit var attacker: Player
        lateinit var victim: LivingEntity
        var fakeAttacker: FakePlayer? = null

        try {
            runSync {
                val world = checkNotNull(Bukkit.getWorld("world"))
                val attackerLocation = Location(world, 0.0, 100.0, 0.0)
                val victimLocation = Location(world, 1.2, 100.0, 0.0)

                val (fakeA, playerA) = spawnPlayer(attackerLocation)
                fakeAttacker = fakeA
                attacker = playerA
                victim = spawnVictim(victimLocation)
                prepareVictimState(victim)
            }

            val environmental = collectFireTickDamages(victim, 1) {
                runSync {
                    victim.maximumNoDamageTicks = 0
                    victim.noDamageTicks = 0
                    victim.lastDamage = 0.0
                    ensureBurning(victim, minTicks = 200)
                }
            }

            val afterburn = collectFireTickDamages(victim, 1) {
                runSync {
                    victim.maximumNoDamageTicks = 0
                    victim.noDamageTicks = 0
                    victim.lastDamage = 0.0
                    val weapon = ItemStack(Material.DIAMOND_SWORD)
                    val fireAspect = XEnchantment.FIRE_ASPECT.get()
                    if (fireAspect != null) {
                        weapon.addUnsafeEnchantment(fireAspect, 2)
                    }
                    equip(attacker, weapon)
                    attackCompat(attacker, victim)
                    ensureBurning(victim, minTicks = 200)
                }
                delayTicks(12)
            }

            val environmentalAvg = environmental.average()
            val afterburnAvg = afterburn.average()
            abs(afterburnAvg - environmentalAvg).shouldBeLessThan(0.25)
        } finally {
            runSync {
                fakeAttacker?.removePlayer()
                victim.remove()
            }
        }
    }

    test("fire aspect afterburn matches environmental fire tick damage (player)") {
        if (isLegacyServer) return@test

        lateinit var attacker: Player
        lateinit var victim: Player
        var fakeAttacker: FakePlayer? = null
        var fakeVictim: FakePlayer? = null

        try {
            runSync {
                val world = checkNotNull(Bukkit.getWorld("world"))
                val attackerLocation = Location(world, 0.0, 100.0, 0.0)
                val victimLocation = Location(world, 1.2, 100.0, 0.0)

                val (fakeA, playerA) = spawnPlayer(attackerLocation)
                fakeAttacker = fakeA
                attacker = playerA

                val (fakeV, playerV) = spawnPlayer(victimLocation)
                fakeVictim = fakeV
                victim = playerV
                prepareVictimState(victim)
            }

            val environmental = collectFireTickDamages(victim, 1) {
                runSync {
                    victim.maximumNoDamageTicks = 0
                    victim.noDamageTicks = 0
                    victim.lastDamage = 0.0
                    ensureBurning(victim, minTicks = 200)
                }
            }

            val afterburn = collectFireTickDamages(victim, 1) {
                runSync {
                    victim.maximumNoDamageTicks = 0
                    victim.noDamageTicks = 0
                    victim.lastDamage = 0.0
                    val weapon = ItemStack(Material.DIAMOND_SWORD)
                    val fireAspect = XEnchantment.FIRE_ASPECT.get()
                    if (fireAspect != null) {
                        weapon.addUnsafeEnchantment(fireAspect, 2)
                    }
                    equip(attacker, weapon)
                    attackCompat(attacker, victim)
                    ensureBurning(victim, minTicks = 200)
                }
                delayTicks(12)
            }

            val environmentalAvg = environmental.average()
            val afterburnAvg = afterburn.average()
            abs(afterburnAvg - environmentalAvg).shouldBeLessThan(0.25)
        } finally {
            runSync {
                fakeAttacker?.removePlayer()
                fakeVictim?.removePlayer()
            }
        }
    }

    test("fire aspect afterburn matches environmental fire tick damage with protection armour (zombie)") {
        // Use an armoured mob victim to keep fire tick sampling stable across versions.
        lateinit var attacker: Player
        lateinit var victim: LivingEntity
        var fakeAttacker: FakePlayer? = null

        try {
            runSync {
                val world = checkNotNull(Bukkit.getWorld("world"))
                val attackerLocation = Location(world, 0.0, 100.0, 0.0)
                val victimLocation = Location(world, 1.2, 100.0, 0.0)

                val (fakeA, playerA) = spawnPlayer(attackerLocation)
                fakeAttacker = fakeA
                attacker = playerA
                victim = spawnVictim(victimLocation)
                prepareVictimState(victim)
                applyProtectionArmour(victim)
            }

            val environmental = collectFireTickDamages(victim, 1) {
                runSync {
                    victim.maximumNoDamageTicks = 0
                    victim.noDamageTicks = 0
                    victim.lastDamage = 0.0
                    ensureBurning(victim, minTicks = 200)
                }
            }

            val afterburn = collectFireTickDamages(victim, 1) {
                runSync {
                    victim.maximumNoDamageTicks = 0
                    victim.noDamageTicks = 0
                    victim.lastDamage = 0.0
                    val weapon = ItemStack(Material.DIAMOND_SWORD)
                    val fireAspect = XEnchantment.FIRE_ASPECT.get()
                    if (fireAspect != null) {
                        weapon.addUnsafeEnchantment(fireAspect, 2)
                    }
                    equip(attacker, weapon)
                    attackCompat(attacker, victim)
                    victim.noDamageTicks = 0
                    victim.lastDamage = 0.0
                    ensureBurning(victim, minTicks = 200)
                }
                delayTicks(12)
            }

            val environmentalAvg = environmental.average()
            val afterburnAvg = afterburn.average()
            abs(afterburnAvg - environmentalAvg).shouldBeLessThan(0.25)
        } finally {
            runSync {
                fakeAttacker?.removePlayer()
                victim.remove()
            }
        }
    }

    test("fire aspect afterburn matches environmental fire tick damage with protection armour (player)") {
        if (isLegacyServer) return@test

        lateinit var attacker: Player
        lateinit var victim: Player
        var fakeAttacker: FakePlayer? = null
        var fakeVictim: FakePlayer? = null

        try {
            runSync {
                val world = checkNotNull(Bukkit.getWorld("world"))
                val attackerLocation = Location(world, 0.0, 100.0, 0.0)
                val victimLocation = Location(world, 1.2, 100.0, 0.0)

                val (fakeA, playerA) = spawnPlayer(attackerLocation)
                fakeAttacker = fakeA
                attacker = playerA

                val (fakeV, playerV) = spawnPlayer(victimLocation)
                fakeVictim = fakeV
                victim = playerV
                prepareVictimState(victim)
                applyProtectionArmour(victim)
            }

            val environmental = collectFireTickDamages(victim, 1) {
                runSync {
                    victim.maximumNoDamageTicks = 0
                    victim.noDamageTicks = 0
                    victim.lastDamage = 0.0
                    ensureBurning(victim, minTicks = 200)
                }
            }

            val afterburn = collectFireTickDamages(victim, 1) {
                runSync {
                    victim.maximumNoDamageTicks = 0
                    victim.noDamageTicks = 0
                    victim.lastDamage = 0.0
                    val weapon = ItemStack(Material.DIAMOND_SWORD)
                    val fireAspect = XEnchantment.FIRE_ASPECT.get()
                    if (fireAspect != null) {
                        weapon.addUnsafeEnchantment(fireAspect, 2)
                    }
                    equip(attacker, weapon)
                    attackCompat(attacker, victim)
                    ensureBurning(victim, minTicks = 200)
                }
                delayTicks(12)
            }

            val environmentalAvg = environmental.average()
            val afterburnAvg = afterburn.average()
            abs(afterburnAvg - environmentalAvg).shouldBeLessThan(0.25)
        } finally {
            runSync {
                fakeAttacker?.removePlayer()
                fakeVictim?.removePlayer()
            }
        }
    }

    test("fire aspect does not increase successful hits during rapid clicking") {
        lateinit var attacker: Player
        lateinit var victim: LivingEntity
        var fakeAttacker: FakePlayer? = null
        var fireTickCount = 0
        val fireTickSamples = mutableListOf<FireTickSample>()
        val fireTickListener = object : Listener {
            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
            fun onFireTick(event: EntityDamageEvent) {
                if (event.entity.uniqueId != victim.uniqueId) return
                val cause = event.cause
                if (cause == EntityDamageEvent.DamageCause.FIRE ||
                    cause == EntityDamageEvent.DamageCause.FIRE_TICK
                ) {
                    fireTickCount++
                    fireTickSamples.add(FireTickSample(event.isCancelled, event.finalDamage))
                }
            }
        }

        try {
            runSync {
                val world = checkNotNull(Bukkit.getWorld("world"))
                val attackerLocation = Location(world, 0.0, 100.0, 0.0)
                val victimLocation = Location(world, 1.2, 100.0, 0.0)

                val (fakeA, playerA) = spawnPlayer(attackerLocation)
                fakeAttacker = fakeA
                attacker = playerA
                victim = spawnVictim(victimLocation)
                prepareVictimState(victim, maxHealthOverride = 200.0)
            }

            val baselineWeapon = ItemStack(Material.DIAMOND_SWORD)
            val fireWeapon = ItemStack(Material.DIAMOND_SWORD).also { item ->
                val fireAspect = XEnchantment.FIRE_ASPECT.get()
                if (fireAspect != null) {
                    item.addUnsafeEnchantment(fireAspect, 2)
                }
            }

            Bukkit.getPluginManager().registerEvents(fireTickListener, plugin)

            runSync {
                prepareVictimState(victim, maxHealthOverride = 200.0)
            }
            val baselineHits = countSuccessfulAttacks(attacker, victim, baselineWeapon, attempts = 30, tickDelay = 1)

            runSync {
                prepareVictimState(victim, maxHealthOverride = 200.0)
            }
            val fireHits = countSuccessfulAttacks(attacker, victim, fireWeapon, attempts = 5, tickDelay = 1).also {
                runSync { ensureBurning(victim, minTicks = 200) }
            }
            waitForFireTick(fireTickSamples)
            val remainingHits = countSuccessfulAttacks(attacker, victim, fireWeapon, attempts = 25, tickDelay = 1)

            val totalFireHits = fireHits + remainingHits
            fireTickCount.shouldBeGreaterThan(0)
            abs(totalFireHits - baselineHits).shouldBeLessThanOrEqual(2)
        } finally {
            HandlerList.unregisterAll(fireTickListener)
            runSync {
                fakeAttacker?.removePlayer()
                victim.remove()
            }
        }
    }

    test("fire aspect does not increase successful hits for fire resistant or fire immune victims") {
        lateinit var attacker: Player
        var fakeAttacker: FakePlayer? = null
        var fakeVictim: FakePlayer? = null
        var blaze: LivingEntity? = null

        try {
            runSync {
                val world = checkNotNull(Bukkit.getWorld("world"))
                val attackerLocation = Location(world, 0.0, 100.0, 0.0)
                val victimLocation = Location(world, 1.2, 100.0, 0.0)

                val (fakeA, playerA) = spawnPlayer(attackerLocation)
                fakeAttacker = fakeA
                attacker = playerA

                val (fakeV, playerV) = spawnPlayer(victimLocation)
                fakeVictim = fakeV
                prepareVictimState(playerV, maxHealthOverride = 200.0)
                applyFireResistance(playerV)
            }

            val baselineWeapon = ItemStack(Material.DIAMOND_SWORD)
            val fireWeapon = ItemStack(Material.DIAMOND_SWORD).also { item ->
                val fireAspect = XEnchantment.FIRE_ASPECT.get()
                if (fireAspect != null) item.addUnsafeEnchantment(fireAspect, 2)
            }

            val fireResBaseline = countSuccessfulAttacks(
                attacker = attacker,
                victim = checkNotNull(Bukkit.getPlayer(checkNotNull(fakeVictim).uuid)),
                weapon = baselineWeapon,
                attempts = 30,
                tickDelay = 1
            )
            runSync {
                val victim = checkNotNull(Bukkit.getPlayer(checkNotNull(fakeVictim).uuid))
                prepareVictimState(victim, maxHealthOverride = 200.0)
                applyFireResistance(victim)
            }
            val fireResWithFireAspect = countSuccessfulAttacks(
                attacker = attacker,
                victim = checkNotNull(Bukkit.getPlayer(checkNotNull(fakeVictim).uuid)),
                weapon = fireWeapon,
                attempts = 30,
                tickDelay = 1
            )

            abs(fireResWithFireAspect - fireResBaseline).shouldBeLessThanOrEqual(2)

            runSync {
                val world = checkNotNull(Bukkit.getWorld("world"))
                val blazeLocation = Location(world, 1.2, 100.0, 2.0)
                blaze = world.spawn(blazeLocation, org.bukkit.entity.Blaze::class.java).apply {
                    maximumNoDamageTicks = 100
                    noDamageTicks = 0
                    isInvulnerable = false
                    health = maxHealth
                }
                disableAiIfPossible(checkNotNull(blaze))
                stabilise(checkNotNull(blaze), blazeLocation)
            }

            val blazeBaseline = countSuccessfulAttacks(
                attacker = attacker,
                victim = checkNotNull(blaze),
                weapon = baselineWeapon,
                attempts = 30,
                tickDelay = 1
            )
            runSync {
                prepareVictimState(checkNotNull(blaze), maxHealthOverride = 200.0)
                stabilise(checkNotNull(blaze), checkNotNull(blaze).location)
            }
            val blazeWithFireAspect = countSuccessfulAttacks(
                attacker = attacker,
                victim = checkNotNull(blaze),
                weapon = fireWeapon,
                attempts = 30,
                tickDelay = 1
            )

            abs(blazeWithFireAspect - blazeBaseline).shouldBeLessThanOrEqual(2)
        } finally {
            runSync {
                fakeAttacker?.removePlayer()
                fakeVictim?.removePlayer()
                blaze?.remove()
            }
        }
    }

    test("fire protection reduces fire tick damage and afterburn matches environmental") {
        lateinit var attacker: Player
        lateinit var victim: LivingEntity
        var fakeAttacker: FakePlayer? = null

        try {
            runSync {
                val world = checkNotNull(Bukkit.getWorld("world"))
                val attackerLocation = Location(world, 0.0, 100.0, 0.0)
                val victimLocation = Location(world, 1.2, 100.0, 0.0)

                val (fakeA, playerA) = spawnPlayer(attackerLocation)
                fakeAttacker = fakeA
                attacker = playerA

                victim = spawnVictim(victimLocation)
                prepareVictimState(victim)
            }

            runSync { applyProtectionArmour(victim) }
            val protEnvironmental = collectFireTickDamages(victim, 1) {
                runSync {
                    victim.maximumNoDamageTicks = 0
                    victim.noDamageTicks = 0
                    victim.lastDamage = 0.0
                    ensureBurning(victim, minTicks = 200)
                }
            }

            runSync {
                prepareVictimState(victim)
                applyFireProtectionArmour(victim)
            }
            val fireProtEnvironmental = collectFireTickDamages(victim, 1) {
                runSync {
                    victim.maximumNoDamageTicks = 0
                    victim.noDamageTicks = 0
                    victim.lastDamage = 0.0
                    ensureBurning(victim, minTicks = 200)
                }
            }

            val tolerance = if (Reflector.versionIsNewerOrEqualTo(1, 20, 0)) 0.35 else 0.5
            fireProtEnvironmental.average().shouldBeLessThan(protEnvironmental.average() + tolerance + 1e-6)

            runSync {
                prepareVictimState(victim)
                applyFireProtectionArmour(victim)
            }
            val afterburn = collectFireTickDamages(victim, 1) {
                runSync {
                    victim.maximumNoDamageTicks = 0
                    victim.noDamageTicks = 0
                    victim.lastDamage = 0.0
                    val weapon = ItemStack(Material.DIAMOND_SWORD)
                    val fireAspect = XEnchantment.FIRE_ASPECT.get()
                    if (fireAspect != null) {
                        weapon.addUnsafeEnchantment(fireAspect, 2)
                    }
                    equip(attacker, weapon)
                    attackCompat(attacker, victim)
                    ensureBurning(victim, minTicks = 200)
                }
                delayTicks(12)
            }

            abs(afterburn.average() - fireProtEnvironmental.average()).shouldBeLessThan(tolerance)
        } finally {
            runSync {
                fakeAttacker?.removePlayer()
                victim.remove()
            }
        }
    }

    test("water extinguishes fire without fire tick damage") {
        lateinit var victim: Player
        var fakeVictim: FakePlayer? = null
        var blockSnapshots: List<BlockTypeSnapshot> = emptyList()
        val samples = mutableListOf<FireTickSample>()

        val listener = object : Listener {
            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
            fun onFireTick(event: EntityDamageEvent) {
                if (event.entity.uniqueId != victim.uniqueId) return
                val cause = event.cause
                if (cause != EntityDamageEvent.DamageCause.FIRE_TICK &&
                    cause != EntityDamageEvent.DamageCause.FIRE
                ) return
                samples.add(FireTickSample(event.isCancelled, event.finalDamage))
            }
        }

        try {
            runSync {
                val world = checkNotNull(Bukkit.getWorld("world"))
                val victimLocation = Location(world, 0.5, 100.0, 0.5)
                val (fakeV, playerV) = spawnPlayer(victimLocation)
                fakeVictim = fakeV
                victim = playerV
                prepareVictimState(victim)
                blockSnapshots = placeWaterColumn(world, victimLocation, height = 2)
                stabilise(victim, victimLocation)
                Bukkit.getPluginManager().registerEvents(listener, plugin)
                ensureBurning(victim, minTicks = 200)
            }

            // Allow the initial tick or two to settle (some versions may still emit an early fire damage event).
            delayTicks(2)
            runSync { samples.clear() }
            delayTicks(20)

            val hadPositiveDamage = samples.any { !it.cancelled && it.finalDamage > 0.0 }
            hadPositiveDamage.shouldBe(false)

            runSync {
                victim.fireTicks.shouldBeLessThanOrEqual(1)
            }
        } finally {
            HandlerList.unregisterAll(listener)
            runSync {
                restoreBlocks(blockSnapshots)
                fakeVictim?.removePlayer()
            }
        }
    }

    test("fire tick does not let a second attacker bypass invulnerability") {
        lateinit var attackerA: Player
        lateinit var attackerB: Player
        lateinit var victim: LivingEntity
        var fakeA: FakePlayer? = null
        var fakeB: FakePlayer? = null

        val samples = mutableListOf<AttackSample>()
        val listener = object : Listener {
            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
            fun onDamage(event: EntityDamageByEntityEvent) {
                if (event.entity.uniqueId != victim.uniqueId) return
                if (event.cause != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return
                if (event.damager.uniqueId != attackerA.uniqueId &&
                    event.damager.uniqueId != attackerB.uniqueId
                ) return
                samples.add(
                    AttackSample(
                        cancelled = event.isCancelled,
                        damage = event.damage,
                        finalDamage = event.finalDamage,
                        noDamageTicks = victim.noDamageTicks,
                        lastDamage = victim.lastDamage
                    )
                )
            }
        }

        try {
            runSync {
                val world = checkNotNull(Bukkit.getWorld("world"))
                val aLocation = Location(world, 0.0, 100.0, 0.0)
                val bLocation = Location(world, 0.0, 100.0, 2.0)
                val victimLocation = Location(world, 1.2, 100.0, 0.0)

                val (fa, pa) = spawnPlayer(aLocation)
                fakeA = fa
                attackerA = pa

                val (fb, pb) = spawnPlayer(bLocation)
                fakeB = fb
                attackerB = pb

                victim = spawnVictim(victimLocation)
                prepareVictimState(victim)

                val fireWeapon = ItemStack(Material.DIAMOND_SWORD).also { item ->
                    val fireAspect = XEnchantment.FIRE_ASPECT.get()
                    if (fireAspect != null) item.addUnsafeEnchantment(fireAspect, 2)
                }
                equip(attackerA, fireWeapon)
                equip(attackerB, ItemStack(Material.DIAMOND_SWORD))

                Bukkit.getPluginManager().registerEvents(listener, plugin)
            }

            if (needsAttackWarmup(attackerA)) {
                delayTicks(6)
            }
            runSync { attackCompat(attackerA, victim) }

            delayTicks(2)
            runSync {
                val fireEvent = EntityDamageEvent(victim, EntityDamageEvent.DamageCause.FIRE_TICK, 1.0)
                Bukkit.getPluginManager().callEvent(fireEvent)
            }
            runSync { requireInvulnerabilityWindow(victim) }

            if (needsAttackWarmup(attackerB)) {
                delayTicks(6)
            }
            runSync { attackCompat(attackerB, victim) }

            delayTicks(2)

            samples.count { !it.cancelled }.shouldBeExactly(1)
        } finally {
            HandlerList.unregisterAll(listener)
            runSync {
                fakeA?.removePlayer()
                fakeB?.removePlayer()
                victim.remove()
            }
        }
    }
})
