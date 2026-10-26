/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities.damage

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent
import org.bukkit.Bukkit
import org.bukkit.entity.HumanEntity
import org.bukkit.event.Event
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.RegisteredListener
import java.util.UUID

/**
 * Retains public preattack strength across native recharge resets before damage dispatch.
 * As with other MONITOR observations, later listeners must not mutate recharge or attack attributes.
 */
object AttackCooldownSnapshot {
    fun interface Scope : AutoCloseable {
        override fun close()
    }

    private data class Attempt(
        val player: UUID,
        val target: UUID,
        val strength: Float,
        val depth: Int,
        val frameFromBottom: Int,
        val frameClass: String,
        val frameMethod: String,
    )

    private val attempts = mutableListOf<Attempt>()
    private var active: Attempt? = null
    private var strengthAvailable = true
    private val listenerClass = RegisteredListener::class.java.name

    private fun dispatchDepth(stack: Array<StackTraceElement>): Int =
        stack.count { it.className == listenerClass && it.methodName == "callEvent" }

    @JvmStatic
    fun register(plugin: Plugin) {
        attempts.clear()
        active = null
        strengthAvailable = true
        val eventClass =
            try {
                Class
                    .forName(
                        "io.papermc.paper.event.player.PrePlayerAttackEntityEvent",
                        false,
                        plugin.javaClass.classLoader,
                    ).asSubclass(Event::class.java)
            } catch (_: ClassNotFoundException) {
                return
            }
        val listener = object : Listener {}
        Bukkit.getPluginManager().registerEvent(eventClass, listener, EventPriority.MONITOR, { _, event ->
            capture(event as PrePlayerAttackEntityEvent)
        }, plugin)
        // A suppressed attack has no final damage callback. Bound pending observations with one
        // shared task, including when no subsequent attack arrives to replace that player's entry.
        Bukkit.getScheduler().runTaskTimer(plugin, Runnable { attempts.clear() }, 1L, 1L)
    }

    private fun capture(event: PrePlayerAttackEntityEvent) {
        if (!strengthAvailable) return
        val stack = Thread.currentThread().stackTrace
        // Capture the native caller rather than depending on mapped attack method names.
        // The public event supplies strength; this boundary only excludes nested foreign damage.
        val eventIndex = stack.indexOfFirst { it.className == Event::class.java.name && it.methodName == "callEvent" }
        val nativeIndex = eventIndex + 1
        // Fabricated public events inside a real attack still have a native frame farther down.
        // Only the immediate caller of the public dispatch may establish this observation.
        if (eventIndex < 0 || nativeIndex >= stack.size ||
            !stack[nativeIndex].className.startsWith("net.minecraft.")
        ) {
            return
        }
        val frameFromBottom = stack.size - nativeIndex
        attempts.removeAll { it.player == event.player.uniqueId && it.frameFromBottom >= frameFromBottom }
        if (event.isCancelled || !event.willAttack()) return
        val strength =
            try {
                event.player.getCooledAttackStrength(0.5f)
            } catch (_: NoSuchMethodError) {
                strengthAvailable = false
                attempts.clear()
                return
            }
        val frame = stack[nativeIndex]
        attempts.add(
            Attempt(
                event.player.uniqueId,
                event.attacked.uniqueId,
                strength,
                dispatchDepth(stack),
                frameFromBottom,
                frame.className,
                frame.methodName,
            ),
        )
    }

    /** The returned scope also masks an outer snapshot while processing foreign nested damage. */
    @JvmStatic
    fun enter(event: EntityDamageEvent): Scope {
        val previous = active
        active = null
        if (attempts.isNotEmpty() && event is EntityDamageByEntityEvent &&
            event.cause == EntityDamageEvent.DamageCause.ENTITY_ATTACK
        ) {
            val stack = Thread.currentThread().stackTrace
            val depth = dispatchDepth(stack)
            val match =
                attempts.lastOrNull {
                    val index = stack.size - it.frameFromBottom
                    it.player == event.damager.uniqueId && it.target == event.entity.uniqueId && it.depth == depth &&
                        index >= 0 && stack[index].className == it.frameClass &&
                        stack[index].methodName == it.frameMethod
                }
            if (match != null) {
                attempts.remove(match)
                active = match
            }
        }
        return Scope { active = previous }
    }

    @JvmStatic
    fun strength(entity: HumanEntity): Float? = active?.takeIf { it.player == entity.uniqueId }?.strength
}
