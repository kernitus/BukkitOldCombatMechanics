/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities.damage

import com.google.common.base.Function
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDamageEvent.DamageModifier
import java.util.EnumMap

/** Re-evaluates only the defences downstream of a changed block. */
@Suppress("DEPRECATION")
object BlockingDamageRecalculation {
    // Bukkit exposes no API to re-evaluate modifiers after changing BLOCKING.
    // setDamage(double) instead uses the original blocking function. Reuse the
    // server's captured functions through a cached compatibility accessor rather
    // than duplicating version-specific armour, potion and enchantment maths.
    private val functionsField =
        try {
            Reflector.getField(EntityDamageEvent::class.java, "modifierFunctions")
        } catch (_: RuntimeException) {
            null
        }

    @JvmStatic
    fun replaceBlocking(
        event: EntityDamageEvent,
        blocking: Double,
    ): Boolean {
        if (!event.isApplicable(DamageModifier.BLOCKING) || !blocking.isFinite()) return false
        val accessor = functionsField ?: return false
        val updated = EnumMap<DamageModifier, Double>(DamageModifier::class.java)
        try {
            @Suppress("UNCHECKED_CAST")
            val functions = accessor.get(event) as? Map<DamageModifier, Function<Double, Double>> ?: return false
            var oldRemaining = 0.0
            var newRemaining = 0.0
            var afterBlocking = false
            for (modifier in DamageModifier.values()) {
                if (!event.isApplicable(modifier)) continue
                val old = event.getDamage(modifier)
                val replacement =
                    when {
                        modifier == DamageModifier.BLOCKING -> {
                            blocking
                        }

                        afterBlocking -> {
                            val function = functions[modifier] ?: return false
                            // Preserve existing plugin adjustments, changing only the
                            // reduction attributable to the changed incoming damage.
                            old + function.apply(newRemaining) - function.apply(oldRemaining)
                        }

                        else -> {
                            old
                        }
                    }
                if (!replacement.isFinite()) return false
                updated[modifier] = replacement
                oldRemaining += old
                newRemaining += replacement
                if (modifier == DamageModifier.BLOCKING) afterBlocking = true
            }
        } catch (_: IllegalAccessException) {
            return false
        } catch (_: RuntimeException) {
            return false
        }
        // Leave the event untouched if any part of the compatibility calculation failed.
        updated.forEach { (modifier, damage) -> event.setDamage(modifier, damage) }
        return true
    }
}
