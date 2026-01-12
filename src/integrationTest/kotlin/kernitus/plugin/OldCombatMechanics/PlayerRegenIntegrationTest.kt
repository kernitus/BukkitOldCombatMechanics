/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics

import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import kernitus.plugin.OldCombatMechanics.module.ModulePlayerRegen
import kotlinx.coroutines.delay
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityRegainHealthEvent
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.Callable

@OptIn(ExperimentalKotest::class)
class PlayerRegenIntegrationTest : FunSpec({
    val testPlugin = JavaPlugin.getPlugin(OCMTestMain::class.java)
    val ocm = JavaPlugin.getPlugin(OCMMain::class.java)
    val module = ModuleLoader.getModules().filterIsInstance<ModulePlayerRegen>().firstOrNull()
        ?: error("ModulePlayerRegen not registered")

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
        ModuleLoader.toggleModules()
    }

    suspend fun withConfig(intervalMs: Long, amount: Int, exhaustion: Double, block: suspend () -> Unit) {
        val oldInterval = ocm.config.getLong("old-player-regen.interval")
        val oldAmount = ocm.config.getInt("old-player-regen.amount")
        val oldExhaustion = ocm.config.getDouble("old-player-regen.exhaustion")

        try {
            runSync {
                ocm.config.set("old-player-regen.interval", intervalMs)
                ocm.config.set("old-player-regen.amount", amount)
                ocm.config.set("old-player-regen.exhaustion", exhaustion)
                module.reload()
                ModuleLoader.toggleModules()
            }
            block()
        } finally {
            runSync {
                ocm.config.set("old-player-regen.interval", oldInterval)
                ocm.config.set("old-player-regen.amount", oldAmount)
                ocm.config.set("old-player-regen.exhaustion", oldExhaustion)
                module.reload()
                ModuleLoader.toggleModules()
            }
        }
    }

    fun createRegainEvent(player: Player, reason: EntityRegainHealthEvent.RegainReason, amount: Double): EntityRegainHealthEvent {
        val ctors = EntityRegainHealthEvent::class.java.constructors
        for (ctor in ctors) {
            val paramTypes = ctor.parameterTypes
            val args = arrayOfNulls<Any>(paramTypes.size)
            var ok = true
            for (i in paramTypes.indices) {
                val t = paramTypes[i]
                args[i] = when {
                    org.bukkit.entity.Entity::class.java.isAssignableFrom(t) -> player
                    t == java.lang.Double.TYPE || t == Double::class.java -> amount
                    EntityRegainHealthEvent.RegainReason::class.java.isAssignableFrom(t) -> reason
                    else -> null
                }
                if (args[i] == null && t.isPrimitive) {
                    ok = false
                    break
                }
            }
            if (!ok) continue
            try {
                @Suppress("UNCHECKED_CAST")
                return ctor.newInstance(*args) as EntityRegainHealthEvent
            } catch (_: Throwable) {
                // Try next
            }
        }
        error("No compatible EntityRegainHealthEvent constructor found for this server version")
    }

    extensions(MainThreadDispatcherExtension(testPlugin))

    beforeSpec {
        runSync {
            val world = Bukkit.getWorld("world") ?: error("world not loaded")
            val location = Location(world, 0.0, 120.0, 0.0, 0f, 0f)
            fakePlayer = FakePlayer(testPlugin)
            fakePlayer.spawn(location)
            player = Bukkit.getPlayer(fakePlayer.uuid) ?: error("Player not found")
            player.isOp = true
            setModeset(player, "old")
        }
    }

    afterSpec {
        runSync { fakePlayer.removePlayer() }
    }

    beforeTest {
        runSync {
            setModeset(player, "old")
            player.health = 20.0
            player.exhaustion = 0f
            player.saturation = 0f

            // Ensure per-player state from previous tests does not leak (healTimes is keyed by UUID).
            runCatching {
                val names = listOf("lastHealTick", "healTimes")
                val f = names.asSequence()
                    .mapNotNull { name -> runCatching { ModulePlayerRegen::class.java.getDeclaredField(name) }.getOrNull() }
                    .firstOrNull()
                if (f != null) {
                    f.isAccessible = true
                    @Suppress("UNCHECKED_CAST")
                    (f.get(module) as? MutableMap<Any, Any>)?.clear()
                }
            }
        }
    }

    test("SATIATED regen is cancelled and replaced with configured heal + exhaustion") {
        withConfig(intervalMs = 0, amount = 2, exhaustion = 3.0) {
            runSync {
                player.health = 10.0
                player.exhaustion = 1.0f
            }

            val event = runSync { createRegainEvent(player, EntityRegainHealthEvent.RegainReason.SATIATED, 1.0) }
            runSync {
                module.onRegen(event)
                event.isCancelled shouldBe true
                player.health shouldBe (12.0 plusOrMinus 1e-9)

                // Simulate vanilla modifying exhaustion despite the cancellation; OCM applies its own value next tick.
                player.exhaustion = 2.0f
            }

            delay(2 * 50L)

            runSync {
                player.exhaustion.toDouble() shouldBe (4.0 plusOrMinus 0.0001) // previous(1.0) + config(3.0)
            }
        }
    }

    test("heal is skipped when within the configured interval") {
        withConfig(intervalMs = 60_000, amount = 2, exhaustion = 3.0) {
            runSync {
                player.health = 10.0
                player.exhaustion = 1.0f
            }

            val first = runSync { createRegainEvent(player, EntityRegainHealthEvent.RegainReason.SATIATED, 1.0) }
            runSync {
                module.onRegen(first)
                player.health shouldBe (12.0 plusOrMinus 1e-9)
            }

            // Simulate immediate damage, then attempt to heal again within the interval.
            runSync {
                player.health = 10.0
                player.exhaustion = 1.0f
            }

            val second = runSync { createRegainEvent(player, EntityRegainHealthEvent.RegainReason.SATIATED, 1.0) }
            runSync {
                module.onRegen(second)
                second.isCancelled shouldBe true
                player.health shouldBe (10.0 plusOrMinus 1e-9)

                // Simulate vanilla exhaustion change; the module should restore to previous exhaustion next tick.
                player.exhaustion = 3.5f
            }

            delay(2 * 50L)

            runSync {
                player.exhaustion.toDouble() shouldBe (1.0 plusOrMinus 0.0001)
            }
        }
    }

    test("non-SATIATED regain reasons are not modified") {
        val nonSatiated = EntityRegainHealthEvent.RegainReason.values().firstOrNull {
            it != EntityRegainHealthEvent.RegainReason.SATIATED
        } ?: error("No non-SATIATED regain reason available")

        withConfig(intervalMs = 0, amount = 100, exhaustion = 3.0) {
            runSync {
                player.health = 10.0
                player.exhaustion = 1.0f
            }
            val event = runSync { createRegainEvent(player, nonSatiated, 5.0) }
            runSync {
                module.onRegen(event)
                event.isCancelled shouldBe false
                player.health shouldBe (10.0 plusOrMinus 1e-9)
            }
        }
    }

    test("healing is clamped to max health") {
        withConfig(intervalMs = 0, amount = 100, exhaustion = 3.0) {
            runSync { player.health = 19.5 }
            val event = runSync { createRegainEvent(player, EntityRegainHealthEvent.RegainReason.SATIATED, 1.0) }
            runSync {
                module.onRegen(event)
                player.health shouldBe (20.0 plusOrMinus 1e-9)
            }
        }
    }
})
