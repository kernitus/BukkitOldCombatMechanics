/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module

import kernitus.plugin.OldCombatMechanics.OCMMain
import org.bukkit.event.EventHandler
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDamageEvent.DamageCause

/**
 * Bring back old fire burning delay behaviour
 */
class ModuleOldBurnDelay(plugin: OCMMain) : OCMModule(plugin, "old-burn-delay") {
    @EventHandler
    fun onFireTick(e: EntityDamageEvent) {
        if (e.cause == DamageCause.FIRE) {
            val entity = e.entity
            if (!isEnabled(entity)) return

            val fireTicks = module().getInt("fire-ticks")
            entity.fireTicks = fireTicks
            debug("Setting fire ticks to $fireTicks", entity)
        }
    }
}
