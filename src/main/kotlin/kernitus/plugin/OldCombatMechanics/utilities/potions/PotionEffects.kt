/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities.potions

import kernitus.plugin.OldCombatMechanics.utilities.reflection.SpigotFunctionChooser
import org.bukkit.entity.LivingEntity
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

object PotionEffects {
    private val getPotionEffectsFunction: SpigotFunctionChooser<LivingEntity, PotionEffectType, PotionEffect?> =
        SpigotFunctionChooser.apiCompatCall({ le, type -> le.getPotionEffect(type) }, { le, type ->
            le.activePotionEffects.stream().filter { potionEffect -> potionEffect.type == type }.findAny().orElse(null)
        })

    /**
     * Returns the [PotionEffect] of a given [PotionEffectType] for a given [LivingEntity], if present.
     *
     * @param entity the entity to query
     * @param type   the type to search
     * @return the [PotionEffect] if present
     */
    fun get(entity: LivingEntity, type: PotionEffectType): PotionEffect? {
        return getPotionEffectsFunction(entity, type)
    }
}
