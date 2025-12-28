/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics

import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player

fun attackCompat(attacker: Player, target: Entity) {
    // Prefer the Bukkit/Paper API if it exists on this runtime.
    val apiAttack = attacker.javaClass.methods.firstOrNull { method ->
        method.name == "attack" &&
            method.parameterCount == 1 &&
            Entity::class.java.isAssignableFrom(method.parameterTypes[0])
    }
    if (apiAttack != null) {
        try {
            apiAttack.invoke(attacker, target)
            return
        } catch (ignored: Exception) {
            // Fall through to NMS-based attack.
        }
    }

    // Fall back to NMS attack on legacy servers.
    try {
        val handleMethod = attacker.javaClass.methods.firstOrNull { method ->
            method.name == "getHandle" && method.parameterCount == 0
        } ?: run {
            if (target is LivingEntity) {
                target.damage(0.0, attacker)
            }
            return
        }
        val attackerHandle = handleMethod.invoke(attacker)
        val targetHandle = target.javaClass.methods.firstOrNull { method ->
            method.name == "getHandle" && method.parameterCount == 0
        }?.invoke(target)

        if (attackerHandle != null && targetHandle != null) {
            val attackMethod = attackerHandle.javaClass.methods.firstOrNull { method ->
                method.parameterCount == 1 && (method.name == "attack" || method.name == "a")
            }
            if (attackMethod != null) {
                attackMethod.invoke(attackerHandle, targetHandle)
                return
            }
        }
    } catch (ignored: Exception) {
        // fall through to damage fallback
    }

    // Last resort: fire a damage attempt to trigger the event pipeline.
    if (target is LivingEntity) {
        target.damage(0.0, attacker)
    }
}
