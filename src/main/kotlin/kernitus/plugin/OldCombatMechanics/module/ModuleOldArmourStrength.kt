/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module

import kernitus.plugin.OldCombatMechanics.OCMMain
import kernitus.plugin.OldCombatMechanics.utilities.damage.DefenceUtils.calculateDefenceDamageReduction
import org.bukkit.entity.LivingEntity
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDamageEvent.DamageCause
import org.bukkit.event.entity.EntityDamageEvent.DamageModifier
import java.util.*
import java.util.stream.Collectors

/**
 * Reverts the armour strength changes to 1.8 calculations, including enchantments.
 * Also recalculates resistance and absorption accordingly.
 *
 *
 * It is based on [this revision](https://minecraft.gamepedia.com/index.php?title=Armor&oldid=909187)
 * of the minecraft wiki.
 */
class ModuleOldArmourStrength(plugin: OCMMain) : OCMModule(plugin, "old-armour-strength") {
    // Defence order is armour defence points -> resistance -> armour enchants -> absorption
    private var randomness = false

    init {
        reload()
    }

    override fun reload() {
        randomness = module().getBoolean("randomness")
    }

    @Suppress("deprecation")
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onEntityDamage(e: EntityDamageEvent) {
        // 1.8 NMS: Damage = (damage after blocking * (25 - total armour strength)) / 25
        if (e.entity !is LivingEntity) return

        val damagedEntity = e.entity as LivingEntity

        // If there was an attacker, and he does not have this module enabled, return
        if (e.cause == DamageCause.ENTITY_ATTACK && e is EntityDamageByEntityEvent) {
            val damager = e.damager
            if (!isEnabled(damager, damagedEntity)) return
        }

        val damageModifiers =
            Arrays.stream(DamageModifier.entries.toTypedArray())
                .filter { type: DamageModifier -> e.isApplicable(type) }
                .collect(
                    Collectors.toMap(
                        { m: DamageModifier -> m },
                        { type: DamageModifier? -> e.getDamage(type!!) })
                )

        calculateDefenceDamageReduction(damagedEntity, damageModifiers, e.cause, randomness)

        // Set the modifiers back to the event
        damageModifiers.forEach { (type: DamageModifier, damage: Double) -> e.setDamage(type, damage) }

        debug("BASE: " + damageModifiers[DamageModifier.BASE])
        debug("BLOCKING: " + damageModifiers[DamageModifier.BLOCKING])
        debug("ARMOUR: " + damageModifiers[DamageModifier.ARMOR])
        debug("RESISTANCE: " + damageModifiers[DamageModifier.RESISTANCE])
        debug("ARMOUR ENCHS: " + damageModifiers[DamageModifier.MAGIC])
        debug("ABSORPTION: " + damageModifiers[DamageModifier.ABSORPTION])

        val finalDamage =
            damageModifiers.values.stream().reduce(0.0) { a: Double, b: Double -> java.lang.Double.sum(a, b) }
        debug("Final damage after defence calc: $finalDamage")
    }
}
