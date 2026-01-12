/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics

import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kernitus.plugin.OldCombatMechanics.module.ModuleFishingRodVelocity
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector
import kotlinx.coroutines.delay
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Entity
import org.bukkit.entity.FishHook
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerFishEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.util.Vector
import java.util.Random
import java.util.UUID
import java.util.concurrent.Callable
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import kotlin.math.cos
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

@OptIn(ExperimentalKotest::class)
class FishingRodVelocityIntegrationTest : FunSpec({
    val testPlugin = JavaPlugin.getPlugin(OCMTestMain::class.java)
    val module = ModuleLoader.getModules()
        .filterIsInstance<ModuleFishingRodVelocity>()
        .firstOrNull() ?: error("ModuleFishingRodVelocity not registered")

    lateinit var player: Player
    lateinit var fakePlayer: FakePlayer

    fun <T> runSync(action: () -> T): T {
        return if (Bukkit.isPrimaryThread()) action() else Bukkit.getScheduler().callSyncMethod(testPlugin, Callable {
            action()
        }).get()
    }

    fun setModeset(player: Player, modeset: String) {
        val playerData = kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage.getPlayerData(player.uniqueId)
        playerData.setModesetForWorld(player.world.uid, modeset)
        kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage.setPlayerData(player.uniqueId, playerData)
    }

    fun setModuleRandomSeed(seed: Long) {
        val field = module.javaClass.getDeclaredField("random")
        field.isAccessible = true
        field.set(module, Random(seed))
    }

    fun assertVectorClose(actual: Vector, expected: Vector, tolerance: Double) {
        abs(actual.x - expected.x) shouldBeLessThan tolerance
        abs(actual.y - expected.y) shouldBeLessThan tolerance
        abs(actual.z - expected.z) shouldBeLessThan tolerance
    }

    fun createFishEvent(player: Player, hook: FishHook, state: PlayerFishEvent.State): PlayerFishEvent {
        val ctors = PlayerFishEvent::class.java.constructors
        for (ctor in ctors) {
            val paramTypes = ctor.parameterTypes
            val hasFishHookParam = paramTypes.any { FishHook::class.java.isAssignableFrom(it) }
            val args = arrayOfNulls<Any>(paramTypes.size)
            var ok = true
            var hookAssigned = false

            for (i in paramTypes.indices) {
                val t = paramTypes[i]
                args[i] = when {
                    Player::class.java.isAssignableFrom(t) -> player
                    FishHook::class.java.isAssignableFrom(t) -> {
                        hookAssigned = true
                        hook
                    }
                    Entity::class.java.isAssignableFrom(t) -> {
                        if (hasFishHookParam) {
                            // Treat as "caught" entity slot in modern signatures
                            null
                        } else if (!hookAssigned) {
                            // Legacy signatures sometimes use Entity for the hook
                            hookAssigned = true
                            hook
                        } else {
                            null
                        }
                    }
                    PlayerFishEvent.State::class.java.isAssignableFrom(t) -> state
                    t == Int::class.javaPrimitiveType -> 0
                    t == Boolean::class.javaPrimitiveType -> false
                    t.isEnum && t.name.endsWith("EquipmentSlot") -> {
                        // Prefer main-hand if present (modern signature)
                        runCatching { java.lang.Enum.valueOf(t as Class<out Enum<*>>, "HAND") }.getOrNull()
                    }
                    ItemStack::class.java.isAssignableFrom(t) -> ItemStack(Material.FISHING_ROD)
                    else -> null
                }

                // If we failed to provide a value for a primitive, this ctor won't work
                if (args[i] == null && t.isPrimitive) {
                    ok = false
                    break
                }
            }

            if (!ok) continue
            try {
                @Suppress("UNCHECKED_CAST")
                val event = ctor.newInstance(*args) as PlayerFishEvent

                // Validate that the event reports the expected hook; legacy signatures vary.
                val hookObj = runCatching {
                    PlayerFishEvent::class.java.getMethod("getHook").invoke(event)
                }.getOrNull()
                val hookFromEvent = hookObj as? FishHook ?: continue
                if (hookFromEvent.uniqueId != hook.uniqueId) continue

                return event
            } catch (_: Throwable) {
                // Try next
            }
        }
        error("No compatible PlayerFishEvent constructor found for this server version")
    }

    fun createFakeHook(player: Player): FishHook {
        val id = UUID.randomUUID()
        var velocity = Vector(0.0, 0.0, 0.0)
        val handler = InvocationHandler { _, method, args ->
            when (method.name) {
                "getUniqueId" -> return@InvocationHandler id
                "getVelocity" -> return@InvocationHandler velocity
                "setVelocity" -> {
                    velocity = (args?.get(0) as? Vector) ?: velocity
                    return@InvocationHandler null
                }
                "isValid" -> return@InvocationHandler true
                "remove" -> return@InvocationHandler null
                "getWorld" -> return@InvocationHandler player.world
                "getLocation" -> return@InvocationHandler player.location.clone()
            }

            return@InvocationHandler when (method.returnType) {
                java.lang.Boolean.TYPE -> false
                java.lang.Integer.TYPE -> 0
                java.lang.Long.TYPE -> 0L
                java.lang.Float.TYPE -> 0f
                java.lang.Double.TYPE -> 0.0
                java.lang.Void.TYPE -> null
                else -> null
            }
        }

        val interfaces = mutableListOf<Class<*>>(FishHook::class.java)
        runCatching { Class.forName("org.bukkit.entity.Fish") }
            .getOrNull()
            ?.takeIf { it.isInterface }
            ?.let { interfaces.add(it) }
        return Proxy.newProxyInstance(
            FishHook::class.java.classLoader,
            interfaces.toTypedArray(),
            handler
        ) as FishHook
    }

    extensions(MainThreadDispatcherExtension(testPlugin))

    beforeSpec {
        runSync {
            val world = Bukkit.getWorld("world") ?: error("world not loaded")
            val location = Location(world, 0.0, 120.0, 0.0, 45f, 10f)
            fakePlayer = FakePlayer(testPlugin)
            fakePlayer.spawn(location)
            player = Bukkit.getPlayer(fakePlayer.uuid) ?: error("Player not found")
            setModeset(player, "old")
            module.reload()
        }
    }

    afterSpec {
        runSync { fakePlayer.removePlayer() }
    }

    beforeTest {
        runSync {
            setModeset(player, "old")
            module.reload()
            player.inventory.setItemInMainHand(ItemStack(Material.FISHING_ROD))
        }
    }

    test("sets hook velocity to the 1.8 formula (deterministic random seed)") {
        val hook = if (Reflector.versionIsNewerOrEqualTo(1, 14, 0)) {
            runSync { player.launchProjectile(FishHook::class.java) }
        } else {
            createFakeHook(player)
        }
        try {
            runSync {
                // Keep everything in a single main-thread slice so hook physics cannot tick between steps (legacy servers are sensitive).
                setModuleRandomSeed(0)

                val event = createFishEvent(player, hook, PlayerFishEvent.State.FISHING)
                module.onFishEvent(event)

                // Mirror the module's float-heavy computation so Java 8/legacy servers match precisely.
                val yaw = player.location.yaw.toDouble()
                val pitch = player.location.pitch.toDouble()

                val oldMaxVelocity = 0.4f.toDouble()
                val piF = Math.PI.toFloat().toDouble()
                val degF = 180.0f.toDouble()

                var vx = -sin(yaw / degF * piF) * cos(pitch / degF * piF) * oldMaxVelocity
                var vz = cos(yaw / degF * piF) * cos(pitch / degF * piF) * oldMaxVelocity
                var vy = -sin(pitch / degF * piF) * oldMaxVelocity

                val oldVelocityMultiplier = 1.5
                val vectorLength = sqrt(vx * vx + vy * vy + vz * vz).toFloat().toDouble()
                vx /= vectorLength
                vy /= vectorLength
                vz /= vectorLength

                val rng = Random(0)
                vx += rng.nextGaussian() * 0.007499999832361937
                vy += rng.nextGaussian() * 0.007499999832361937
                vz += rng.nextGaussian() * 0.007499999832361937

                vx *= oldVelocityMultiplier
                vy *= oldVelocityMultiplier
                vz *= oldVelocityMultiplier

                val expected = Vector(vx, vy, vz)
                val actual = hook.velocity
                assertVectorClose(actual, expected, 1e-6)
            }
        } finally {
            if (Reflector.versionIsNewerOrEqualTo(1, 14, 0)) {
                runSync { hook.remove() }
            }
        }
    }

    test("does not modify hook velocity for non-FISHING states") {
        val hook = if (Reflector.versionIsNewerOrEqualTo(1, 14, 0)) {
            runSync { player.launchProjectile(FishHook::class.java) }
        } else {
            createFakeHook(player)
        }
        try {
            val nonFishing = PlayerFishEvent.State.values().firstOrNull { it != PlayerFishEvent.State.FISHING }
                ?: error("No non-FISHING PlayerFishEvent state available")

            runSync {
                val original = Vector(0.123, 0.456, -0.789)
                hook.velocity = original
                val baseline = hook.velocity

                val event = createFishEvent(player, hook, nonFishing)
                module.onFishEvent(event)

                val actual = hook.velocity
                assertVectorClose(actual, baseline, 1e-8)
            }
        } finally {
            if (Reflector.versionIsNewerOrEqualTo(1, 14, 0)) {
                runSync { hook.remove() }
            }
        }
    }

    test("1.14+ applies an extra gravity tick when the hook is not in water") {
        if (!Reflector.versionIsNewerOrEqualTo(1, 14, 0)) return@test

        val hookBaseline = runSync { player.launchProjectile(FishHook::class.java) }
        val hookWithModule = runSync { player.launchProjectile(FishHook::class.java) }
        try {
            runSync {
                // Keep both hooks colocated so vanilla physics is as close as possible
                hookWithModule.teleport(hookBaseline.location)

                // Schedule the module's per-tick gravity adjustment for hookWithModule
                val event = createFishEvent(player, hookWithModule, PlayerFishEvent.State.FISHING)
                Bukkit.getPluginManager().callEvent(event)

                // Force the same starting velocity for both so we can compare deltas
                val start = Vector(0.0, 0.2, 0.0)
                hookBaseline.velocity = start
                hookWithModule.velocity = start
            }

            // Wait for at least one tick of the module's gravity adjustment
            delay(3 * 50L)

            val yBaseline = runSync { hookBaseline.velocity.y }
            val yWithModule = runSync { hookWithModule.velocity.y }

            // Module subtracts an extra 0.01 from Y each tick (when not in water),
            // so it should be noticeably more negative than the baseline hook.
            (yWithModule < yBaseline - 0.005) shouldBe true
        } finally {
            runSync {
                hookBaseline.remove()
                hookWithModule.remove()
            }
        }
    }
})
