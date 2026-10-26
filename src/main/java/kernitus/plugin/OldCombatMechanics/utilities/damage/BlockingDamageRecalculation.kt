/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities.damage

import com.google.common.base.Function
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector
import org.bukkit.entity.LivingEntity
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDamageEvent.DamageModifier
import java.util.EnumMap

/** Re-evaluates native defences downstream of a changed blocking or immunity modifier. */
@Suppress("DEPRECATION")
object BlockingDamageRecalculation {
    private val immunityModifier = DamageModifier.values().firstOrNull { it.name == "INVULNERABILITY_REDUCTION" }

    @JvmStatic
    fun hasImmunityModifier(event: EntityDamageEvent): Boolean =
        immunityModifier?.let { event.isApplicable(it) } == true

    @JvmStatic
    fun removeNativeImmunity(event: EntityDamageEvent): Boolean {
        val modifier = immunityModifier ?: return false
        if (!event.isApplicable(modifier)) return false
        if (event.getDamage(modifier) == 0.0 && event.getOriginalDamage(modifier) == 0.0) return true
        val accessor = functionsField ?: return false
        return try {
            @Suppress("UNCHECKED_CAST")
            val functions = accessor.get(event) as? Map<DamageModifier, Function<Double, Double>> ?: return false
            val nativeFunction = functions[modifier] ?: return false
            val upstream =
                DamageModifier
                    .values()
                    .takeWhile { it != modifier }
                    .filter { event.isApplicable(it) }
                    .sumOf { event.getDamage(it) }
            // setDamage(double) retains additive foreign adjustments. Remove only the native
            // captured reduction, preserving changes made by earlier plugins to this modifier.
            replaceModifier(event, modifier, event.getDamage(modifier) - nativeFunction.apply(upstream))
        } catch (_: IllegalAccessException) {
            false
        } catch (_: RuntimeException) {
            false
        }
    }

    class OriginalDamage internal constructor(
        private val event: EntityDamageEvent,
    ) {
        private val modifiers =
            DamageModifier
                .values()
                .filter { event.isApplicable(it) }
                .associateWith { event.getDamage(it) }
        private val cancelled = event.isCancelled
        private val lastDamage = (event.entity as? LivingEntity)?.lastDamage

        // Native accepted-hit history excludes immunity and later defensive reductions.
        val nativeBaseline: Double =
            modifiers
                .filterKeys {
                    it.name == "BASE" || it.name == "BLOCKING" || it.name == "FREEZING" || it.name == "HARD_HAT"
                }.values
                .sum()

        fun restore() {
            modifiers.forEach { (modifier, value) -> event.setDamage(modifier, value) }
            event.isCancelled = cancelled
            lastDamage?.let { (event.entity as LivingEntity).lastDamage = it }
        }
    }

    @JvmStatic
    fun captureImmunityState(event: EntityDamageEvent): OriginalDamage? =
        if (hasImmunityModifier(event)) OriginalDamage(event) else null

    // Bukkit exposes no API to re-evaluate modifiers after changing BLOCKING.
    // setDamage(double) instead uses the original blocking function. Reuse the
    // server's captured functions through a cached compatibility accessor rather
    // than duplicating version-specific armour, potion and enchantment maths.
    private val functionsField by lazy {
        try {
            Reflector.getField(EntityDamageEvent::class.java, "modifierFunctions")
        } catch (_: RuntimeException) {
            null
        }
    }

    @JvmStatic
    fun replaceBlocking(
        event: EntityDamageEvent,
        blocking: Double,
    ): Boolean = replaceModifier(event, DamageModifier.BLOCKING, blocking)

    private fun replaceModifier(
        event: EntityDamageEvent,
        changed: DamageModifier,
        value: Double,
    ): Boolean {
        if (!event.isApplicable(changed) || !value.isFinite()) return false
        if (event.getDamage(changed) == value) return true
        val accessor = functionsField ?: return false
        val updated = EnumMap<DamageModifier, Double>(DamageModifier::class.java)
        try {
            @Suppress("UNCHECKED_CAST")
            val functions = accessor.get(event) as? Map<DamageModifier, Function<Double, Double>> ?: return false
            var oldRemaining = 0.0
            var newRemaining = 0.0
            var afterChanged = false
            for (modifier in DamageModifier.values()) {
                if (!event.isApplicable(modifier)) continue
                val old = event.getDamage(modifier)
                val replacement =
                    when {
                        modifier == changed -> {
                            value
                        }

                        afterChanged -> {
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
                if (modifier == changed) afterChanged = true
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
