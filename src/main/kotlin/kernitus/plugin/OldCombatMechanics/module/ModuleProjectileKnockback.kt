/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module

import kernitus.plugin.OldCombatMechanics.OCMMain
import org.bukkit.entity.EntityType
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent.DamageModifier

/**
 * Adds knockback to eggs, snowballs and ender pearls.
 */
class ModuleProjectileKnockback(plugin: OCMMain) : OCMModule(plugin, "projectile-knockback") {
    @EventHandler(priority = EventPriority.NORMAL)
    fun onEntityHit(e: EntityDamageByEntityEvent) {
        if (!isEnabled(e.damager, e.entity)) return

        when (val type = e.damager.type) {
            EntityType.SNOWBALL, EntityType.EGG, EntityType.ENDER_PEARL -> if (e.damage == 0.0) { // So we don't override enderpearl fall damage
                e.damage = module().getDouble("damage." + type.toString().lowercase())
                if (e.isApplicable(DamageModifier.ABSORPTION)) e.setDamage(DamageModifier.ABSORPTION, 0.0)
            }
            else -> {}
        }
    }
}