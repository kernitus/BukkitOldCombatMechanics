/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics

import com.cryptomorin.xseries.XPotion
import com.google.common.base.Function
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kernitus.plugin.OldCombatMechanics.module.ModuleOldArmourDurability
import kernitus.plugin.OldCombatMechanics.module.ModuleOldArmourStrength
import kernitus.plugin.OldCombatMechanics.module.ModulePlayerKnockback
import kernitus.plugin.OldCombatMechanics.module.ModuleShieldDamageReduction
import kernitus.plugin.OldCombatMechanics.utilities.Config
import kotlinx.coroutines.suspendCancellableCoroutine
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDamageEvent.DamageCause
import org.bukkit.event.entity.EntityDamageEvent.DamageModifier
import org.bukkit.event.player.PlayerItemDamageEvent
import org.bukkit.event.player.PlayerVelocityEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Vector
import java.util.EnumMap
import kotlin.coroutines.resume

/** Focused reproductions: constructed events unless a test explicitly calls LivingEntity.damage. */
@Suppress("DEPRECATION")
@OptIn(ExperimentalKotest::class)
class ModuleInteractionEdgeCasesIntegrationTest :
    FunSpec({
        val testPlugin = JavaPlugin.getPlugin(OCMTestMain::class.java)
        val ocm = JavaPlugin.getPlugin(OCMMain::class.java)
        extensions(MainThreadDispatcherExtension(testPlugin))
        lateinit var attackerFake: FakePlayer
        lateinit var victimFake: FakePlayer
        lateinit var attacker: Player
        lateinit var victim: Player
        lateinit var originalConfig: String
        val modules = ModuleLoader.getModules()
        val shield = modules.filterIsInstance<ModuleShieldDamageReduction>().single()
        val armour = modules.filterIsInstance<ModuleOldArmourStrength>().single()
        val knockback = modules.filterIsInstance<ModulePlayerKnockback>().single()
        val durability = modules.filterIsInstance<ModuleOldArmourDurability>().single()

        fun runSync(action: () -> Unit) {
            if (Bukkit.isPrimaryThread()) {
                action()
            } else {
                Bukkit.getScheduler().callSyncMethod(testPlugin, java.util.concurrent.Callable { action() }).get()
            }
        }

        fun configure(vararg enabled: String) {
            ocm.config.set("always_enabled_modules", enabled.toList())
            ocm.config.set("disabled_modules", ModuleLoader.getConfigurableModuleNames().filterNot { it in enabled })
            ocm.config.set("modesets", null)
            ocm.config.createSection("modesets", mapOf("test" to emptyList<String>()))
            ocm.config.set("worlds", null)
            ocm.config.createSection("worlds", mapOf("world" to listOf("test")))
            ocm.saveConfig()
            Config.reload()
        }

        suspend fun ticks(count: Long) {
            suspendCancellableCoroutine<Unit> { continuation ->
                Bukkit.getScheduler().runTaskLater(testPlugin, Runnable { continuation.resume(Unit) }, count)
            }
        }

        fun field(
            instance: Any,
            name: String,
        ): java.lang.reflect.Field = instance.javaClass.getDeclaredField(name).also { it.isAccessible = true }

        fun cache(
            instance: Any,
            name: String,
        ): MutableMap<*, *> = field(instance, name).get(instance) as MutableMap<*, *>

        fun resetCache(
            instance: Any,
            mapName: String,
            taskName: String,
            clockName: String,
        ) {
            (field(instance, taskName).get(instance) as? BukkitTask)?.cancel()
            field(instance, taskName).set(instance, null)
            cache(instance, mapName).clear()
            field(instance, clockName).setLong(instance, 0)
        }

        fun blockedHit(
            armourPoints: Double = 0.0,
            toughness: Double = 0.0,
        ): EntityDamageByEntityEvent {
            val modifiers = EnumMap<DamageModifier, Double>(DamageModifier::class.java)
            val functions = EnumMap<DamageModifier, Function<in Double, Double>>(DamageModifier::class.java)
            DamageModifier.values().forEach {
                modifiers[it] = 0.0
                functions[it] = Function { 0.0 }
            }
            modifiers[DamageModifier.BASE] = 10.0
            modifiers[DamageModifier.BLOCKING] = -10.0
            functions[DamageModifier.BLOCKING] = Function { damage -> -damage }
            val resistance = victim.getPotionEffect(checkNotNull(XPotion.RESISTANCE.get()))
            val resistanceFactor = if (resistance == null) 0.0 else minOf(1.0, (resistance.amplifier + 1) * 0.2)
            functions[DamageModifier.ARMOR] =
                Function { damage ->
                    val effectiveArmour =
                        minOf(
                            20.0,
                            maxOf(
                                armourPoints / 5.0,
                                armourPoints - damage / (2.0 + toughness / 4.0),
                            ),
                        )
                    -damage * effectiveArmour / 25.0
                }
            functions[DamageModifier.RESISTANCE] = Function { damage -> -damage * resistanceFactor }
            val absorption = victim.absorptionAmount
            functions[DamageModifier.ABSORPTION] = Function { damage -> -minOf(absorption, maxOf(0.0, damage)) }
            return EntityDamageByEntityEvent(attacker, victim, DamageCause.ENTITY_ATTACK, modifiers, functions)
        }

        beforeSpec {
            runSync {
                originalConfig = ocm.config.saveToString()
                val world = checkNotNull(Bukkit.getWorld("world"))
                attackerFake = FakePlayer(testPlugin)
                victimFake = FakePlayer(testPlugin)
                attackerFake.spawn(Location(world, 0.0, 100.0, 0.0))
                victimFake.spawn(Location(world, 3.0, 100.0, 0.0))
                attacker = checkNotNull(Bukkit.getPlayer(attackerFake.uuid))
                victim = checkNotNull(Bukkit.getPlayer(victimFake.uuid))
                attacker.setGravity(false)
                victim.setGravity(false)
            }
        }

        beforeTest {
            runSync {
                configure()
                resetCache(knockback, "pendingKnockback", "pendingCleanupTask", "pendingTickCounter")
                resetCache(shield, "fullyBlocked", "fullyBlockedCleanupTask", "fullyBlockedTickCounter")
                resetCache(durability, "explosionDamaged", "explosionCleanupTask", "explosionTickCounter")
                listOf(attacker, victim).forEach {
                    it.inventory.clear()
                    it.activePotionEffects.forEach { effect -> it.removePotionEffect(effect.type) }
                    it.noDamageTicks = 0
                    it.lastDamage = 0.0
                    it.velocity = Vector()
                    it.fireTicks = 0
                    it.absorptionAmount = 0.0
                    it.health = it.maxHealth
                }
            }
        }

        afterSpec {
            runSync {
                configure()
                resetCache(knockback, "pendingKnockback", "pendingCleanupTask", "pendingTickCounter")
                resetCache(shield, "fullyBlocked", "fullyBlockedCleanupTask", "fullyBlockedTickCounter")
                resetCache(durability, "explosionDamaged", "explosionCleanupTask", "explosionTickCounter")
                ocm.config.loadFromString(originalConfig)
                ocm.saveConfig()
                Config.reload()
                attackerFake.removePlayer()
                victimFake.removePlayer()
            }
        }

        for (kind in listOf("knockback", "shield", "explosion")) {
            test("$kind cache expires after one tick when its cleanup task restarts") {
                configure("old-player-knockback", "shield-damage-reduction", "old-armour-durability")
                ocm.config.set("shield-damage-reduction.generalDamageReductionAmount", 0)
                ocm.config.set("shield-damage-reduction.generalDamageReductionPercentage", 100)
                shield.reload()
                victim.inventory.chestplate = ItemStack(Material.DIAMOND_CHESTPLATE)
                val instance: Any
                val mapName: String
                val taskName: String
                val clockName: String
                val add: () -> Unit
                when (kind) {
                    "knockback" -> {
                        instance = knockback
                        mapName = "pendingKnockback"
                        taskName = "pendingCleanupTask"
                        clockName =
                            "pendingTickCounter"
                        add =
                            {
                                knockback.onEntityDamageEntity(
                                    EntityDamageByEntityEvent(attacker, victim, DamageCause.ENTITY_ATTACK, 4.0),
                                )
                            }
                    }

                    "shield" -> {
                        instance = shield
                        mapName = "fullyBlocked"
                        taskName = "fullyBlockedCleanupTask"
                        clockName =
                            "fullyBlockedTickCounter"
                        add = { shield.onHit(blockedHit()) }
                    }

                    else -> {
                        instance = durability
                        mapName = "explosionDamaged"
                        taskName = "explosionCleanupTask"
                        clockName =
                            "explosionTickCounter"
                        add =
                            {
                                durability.onPlayerExplosionDamage(
                                    EntityDamageEvent(victim, DamageCause.BLOCK_EXPLOSION, 4.0),
                                )
                            }
                    }
                }
                // Exercise populate and cleanup cycles to advance the clock naturally; never seed its value.
                repeat(5) {
                    add()
                    ticks(1)
                }
                ticks(2)
                cache(instance, mapName).isEmpty() shouldBe true
                field(instance, taskName).get(instance) shouldBe null
                val previousClock = field(instance, clockName).getLong(instance)
                (previousClock > 0) shouldBe true
                add()
                cache(instance, mapName).containsKey(victim.uniqueId) shouldBe true
                ticks(1)
                val retained = cache(instance, mapName).containsKey(victim.uniqueId)
                when (kind) {
                    "knockback" -> {
                        val externalVelocity = Vector(1.0, 2.0, 3.0)
                        val event = PlayerVelocityEvent(victim, externalVelocity.clone())
                        knockback.onPlayerVelocityEvent(event)
                        withClue("clock=$previousClock; retained=$retained; velocity=${event.velocity}") {
                            event.velocity shouldBe externalVelocity
                        }
                    }

                    "shield" -> {
                        val event = PlayerItemDamageEvent(victim, checkNotNull(victim.inventory.chestplate), 4)
                        shield.onItemDamage(event)
                        withClue("clock=$previousClock; retained=$retained; cancelled=${event.isCancelled}") {
                            event.isCancelled shouldBe false
                        }
                    }

                    else -> {
                        ocm.config.set("old-armour-durability.reduction", 1)
                        val event = PlayerItemDamageEvent(victim, checkNotNull(victim.inventory.chestplate), 4)
                        durability.onItemDamage(event)
                        withClue("clock=$previousClock; retained=$retained; wear=${event.damage}, expected=1") {
                            event.damage shouldBe 1
                        }
                    }
                }
                cache(instance, mapName).isEmpty() shouldBe true
            }
        }
    })
