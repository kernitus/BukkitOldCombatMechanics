/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics

import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import kernitus.plugin.OldCombatMechanics.module.ModulePlayerKnockback
import com.cryptomorin.xseries.XAttribute
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.attribute.AttributeModifier
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerVelocityEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.util.Vector
import java.util.UUID
import java.util.concurrent.Callable

@OptIn(ExperimentalKotest::class)
class PlayerKnockbackIntegrationTest : FunSpec({
    val testPlugin = JavaPlugin.getPlugin(OCMTestMain::class.java)
    val ocm = JavaPlugin.getPlugin(OCMMain::class.java)
    val module = ModuleLoader.getModules()
        .filterIsInstance<ModulePlayerKnockback>()
        .firstOrNull() ?: error("ModulePlayerKnockback not registered")

    lateinit var attacker: Player
    lateinit var victim: Player
    lateinit var fakeAttacker: FakePlayer
    lateinit var fakeVictim: FakePlayer

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

    suspend fun TestScope.withConfig(block: suspend TestScope.() -> Unit) {
        val horizontal = ocm.config.getDouble("old-player-knockback.knockback-horizontal")
        val vertical = ocm.config.getDouble("old-player-knockback.knockback-vertical")
        val verticalLimit = ocm.config.getDouble("old-player-knockback.knockback-vertical-limit")
        val extraHorizontal = ocm.config.getDouble("old-player-knockback.knockback-extra-horizontal")
        val extraVertical = ocm.config.getDouble("old-player-knockback.knockback-extra-vertical")
        val resistanceEnabled = ocm.config.getBoolean("old-player-knockback.enable-knockback-resistance")

        try {
            block()
        } finally {
            ocm.config.set("old-player-knockback.knockback-horizontal", horizontal)
            ocm.config.set("old-player-knockback.knockback-vertical", vertical)
            ocm.config.set("old-player-knockback.knockback-vertical-limit", verticalLimit)
            ocm.config.set("old-player-knockback.knockback-extra-horizontal", extraHorizontal)
            ocm.config.set("old-player-knockback.knockback-extra-vertical", extraVertical)
            ocm.config.set("old-player-knockback.enable-knockback-resistance", resistanceEnabled)
            module.reload()
            ModuleLoader.toggleModules()
        }
    }

    fun setModeset(player: Player, modeset: String) {
        val playerData = kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage.getPlayerData(player.uniqueId)
        playerData.setModesetForWorld(player.world.uid, modeset)
        kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage.setPlayerData(player.uniqueId, playerData)
    }

    fun pendingKnockbackField(): java.lang.reflect.Field {
        val names = listOf("pendingKnockback", "playerKnockbackHashMap")
        for (name in names) {
            val f = runCatching { ModulePlayerKnockback::class.java.getDeclaredField(name) }.getOrNull() ?: continue
            f.isAccessible = true
            return f
        }
        error("No pending knockback field found on ModulePlayerKnockback (tried: $names)")
    }

    fun pendingKnockbackMap(): MutableMap<UUID, Any> {
        val field = pendingKnockbackField()
        @Suppress("UNCHECKED_CAST")
        return field.get(module) as MutableMap<UUID, Any>
    }

    fun getPendingVector(uuid: UUID): Vector? {
        val map = pendingKnockbackMap()
        val value = map[uuid] ?: return null
        return when (value) {
            is Vector -> value
            else -> {
                val vf = value.javaClass.getDeclaredField("velocity")
                vf.isAccessible = true
                vf.get(value) as? Vector
            }
        }
    }

    fun removePending(uuid: UUID) {
        pendingKnockbackMap().remove(uuid)
    }

    fun putPending(uuid: UUID, vector: Vector) {
        val fieldName = pendingKnockbackField().name
        if (fieldName == "playerKnockbackHashMap") {
            @Suppress("UNCHECKED_CAST")
            (pendingKnockbackMap() as MutableMap<UUID, Vector>)[uuid] = vector
            return
        }

        val pendingClass = ModulePlayerKnockback::class.java.declaredClasses
            .firstOrNull { it.simpleName == "PendingKnockback" }
            ?: error("PendingKnockback inner class not found")
        val ctor = pendingClass.getDeclaredConstructor(Vector::class.java, Long::class.javaPrimitiveType)
        ctor.isAccessible = true
        val pending = ctor.newInstance(vector, Long.MAX_VALUE)
        pendingKnockbackMap()[uuid] = pending
    }

    fun damageEvent(): EntityDamageByEntityEvent {
        val event = EntityDamageByEntityEvent(attacker, victim, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 4.0)
        Bukkit.getPluginManager().callEvent(event)
        return event
    }

    fun velocityEvent(initial: Vector): PlayerVelocityEvent {
        val event = PlayerVelocityEvent(victim, initial)
        Bukkit.getPluginManager().callEvent(event)
        return event
    }

    extensions(MainThreadDispatcherExtension(testPlugin))

    beforeSpec {
        runSync {
            val world = Bukkit.getServer().getWorld("world")
            val attackerLocation = Location(world, 0.0, 100.0, 0.0, 0f, 0f)
            val victimLocation = Location(world, 1.0, 100.0, 0.0, 0f, 0f)

            fakeAttacker = FakePlayer(testPlugin)
            fakeVictim = FakePlayer(testPlugin)
            fakeAttacker.spawn(attackerLocation)
            fakeVictim.spawn(victimLocation)

            attacker = checkNotNull(Bukkit.getPlayer(fakeAttacker.uuid))
            victim = checkNotNull(Bukkit.getPlayer(fakeVictim.uuid))
            attacker.isOp = true
            victim.isOp = true
            attacker.inventory.setItemInMainHand(ItemStack(Material.DIAMOND_SWORD))
            setModeset(attacker, "old")
            setModeset(victim, "old")
        }
    }

    afterSpec {
        runSync {
            fakeAttacker.removePlayer()
            fakeVictim.removePlayer()
        }
    }

    beforeTest {
        runSync {
            val world = Bukkit.getServer().getWorld("world")
            val attackerLocation = Location(world, 0.0, 100.0, 0.0, 0f, 0f)
            val victimLocation = Location(world, 1.0, 100.0, 0.0, 0f, 0f)

            attacker.teleport(attackerLocation)
            victim.teleport(victimLocation)
            attacker.isSprinting = false
            attacker.inventory.setItemInMainHand(ItemStack(Material.DIAMOND_SWORD))
            attacker.velocity = Vector(0, 0, 0)
            victim.velocity = Vector(0, 0, 0)
            victim.noDamageTicks = 0
            victim.maximumNoDamageTicks = 0
            victim.isInvulnerable = false
            setModeset(attacker, "old")
            setModeset(victim, "old")
            module.reload()
        }
    }

    context("Knockback vectors") {
        test("base knockback is applied on velocity event") {
            withConfig {
                ocm.config.set("old-player-knockback.knockback-horizontal", 0.4)
                ocm.config.set("old-player-knockback.knockback-vertical", 0.4)
                ocm.config.set("old-player-knockback.knockback-vertical-limit", 0.4)
                ocm.config.set("old-player-knockback.knockback-extra-horizontal", 0.5)
                ocm.config.set("old-player-knockback.knockback-extra-vertical", 0.1)
                ocm.config.set("old-player-knockback.enable-knockback-resistance", false)
                module.reload()

                damageEvent()
                val vector = getPendingVector(victim.uniqueId) ?: error("No knockback stored")
                vector.x shouldBe (0.4 plusOrMinus 0.0001)
                vector.y shouldBe (0.4 plusOrMinus 0.0001)
                vector.z shouldBe (0.0 plusOrMinus 0.0001)
                removePending(victim.uniqueId)
            }
        }

        test("sprint adds extra knockback") {
            withConfig {
                ocm.config.set("old-player-knockback.knockback-horizontal", 0.4)
                ocm.config.set("old-player-knockback.knockback-vertical", 0.4)
                ocm.config.set("old-player-knockback.knockback-vertical-limit", 0.4)
                ocm.config.set("old-player-knockback.knockback-extra-horizontal", 0.5)
                ocm.config.set("old-player-knockback.knockback-extra-vertical", 0.1)
                ocm.config.set("old-player-knockback.enable-knockback-resistance", false)
                module.reload()

                attacker.isSprinting = true
                attacker.teleport(attacker.location.apply { yaw = 0f })

                val event = EntityDamageByEntityEvent(
                    attacker,
                    victim,
                    EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                    4.0
                )
                module.onEntityDamageEntity(event)
                val vector = getPendingVector(victim.uniqueId) ?: error("No knockback stored")
                vector.x shouldBe (0.4 plusOrMinus 0.0001)
                vector.y shouldBe (0.5 plusOrMinus 0.0001)
                vector.z shouldBe (0.5 plusOrMinus 0.0001)
                removePending(victim.uniqueId)
            }
        }

        test("velocity override only applies once") {
            withConfig {
                ocm.config.set("old-player-knockback.knockback-horizontal", 0.4)
                ocm.config.set("old-player-knockback.knockback-vertical", 0.4)
                ocm.config.set("old-player-knockback.knockback-vertical-limit", 0.4)
                ocm.config.set("old-player-knockback.knockback-extra-horizontal", 0.5)
                ocm.config.set("old-player-knockback.knockback-extra-vertical", 0.1)
                ocm.config.set("old-player-knockback.enable-knockback-resistance", false)
                module.reload()

                val expected = Vector(0.4, 0.4, 0.0)
                putPending(victim.uniqueId, expected)
                val first = velocityEvent(Vector(0, 0, 0))
                first.velocity.x shouldBe (0.4 plusOrMinus 0.0001)

                val secondInitial = Vector(1.0, 2.0, 3.0)
                val second = velocityEvent(secondInitial)
                second.velocity.x shouldBe (1.0 plusOrMinus 0.0001)
                second.velocity.y shouldBe (2.0 plusOrMinus 0.0001)
                second.velocity.z shouldBe (3.0 plusOrMinus 0.0001)
            }
        }
    }

    context("Knockback resistance") {
        test("modifiers are removed when resistance disabled") {
            withConfig {
                ocm.config.set("old-player-knockback.enable-knockback-resistance", false)
                module.reload()

                val attributeType = XAttribute.KNOCKBACK_RESISTANCE.get() ?: return@withConfig
                val attribute = victim.getAttribute(attributeType)
                val modifier = AttributeModifier(UUID.randomUUID(), "test", 0.5, AttributeModifier.Operation.ADD_NUMBER)
                attribute?.addModifier(modifier)

                val event = EntityDamageByEntityEvent(attacker, victim, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 4.0)
                Bukkit.getPluginManager().callEvent(event)

                attribute?.modifiers?.contains(modifier) shouldBe false
            }
        }

        test("modifiers remain when resistance enabled and supported") {
            if (!Reflector.versionIsNewerOrEqualTo(1, 16, 0)) return@test
            withConfig {
                ocm.config.set("old-player-knockback.enable-knockback-resistance", true)
                module.reload()

                val attributeType = XAttribute.KNOCKBACK_RESISTANCE.get() ?: return@withConfig
                val attribute = victim.getAttribute(attributeType)
                val modifier = AttributeModifier(UUID.randomUUID(), "test", 0.5, AttributeModifier.Operation.ADD_NUMBER)
                attribute?.addModifier(modifier)

                val event = EntityDamageByEntityEvent(attacker, victim, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 4.0)
                Bukkit.getPluginManager().callEvent(event)

                attribute?.modifiers?.contains(modifier) shouldBe true
            }
        }

        test("enabled resistance scales horizontal knockback") {
            if (!Reflector.versionIsNewerOrEqualTo(1, 16, 0)) return@test
            withConfig {
                ocm.config.set("old-player-knockback.knockback-horizontal", 0.4)
                ocm.config.set("old-player-knockback.knockback-vertical", 0.4)
                ocm.config.set("old-player-knockback.knockback-vertical-limit", 0.4)
                ocm.config.set("old-player-knockback.knockback-extra-horizontal", 0.5)
                ocm.config.set("old-player-knockback.knockback-extra-vertical", 0.1)
                ocm.config.set("old-player-knockback.enable-knockback-resistance", true)
                module.reload()

                val attributeType = XAttribute.KNOCKBACK_RESISTANCE.get() ?: return@withConfig
                val attribute = victim.getAttribute(attributeType) ?: return@withConfig
                val originalBase = attribute.baseValue
                attribute.baseValue = 0.5

                try {
                    val event = damageEvent()
                    event.isCancelled shouldBe false
                    val vector = getPendingVector(victim.uniqueId) ?: error("No knockback stored")
                    val expectedHorizontal = 0.4 * (1 - attribute.value)
                    vector.x shouldBe (expectedHorizontal plusOrMinus 0.0001)
                    vector.y shouldBe (0.4 plusOrMinus 0.0001)
                    removePending(victim.uniqueId)
                } finally {
                    attribute.baseValue = originalBase
                }
            }
        }
    }
})
