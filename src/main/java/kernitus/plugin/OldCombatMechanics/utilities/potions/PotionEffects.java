/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities.potions;

import kernitus.plugin.OldCombatMechanics.utilities.reflection.SpigotFunctionChooser;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Optional;

public class PotionEffects {

    private static final SpigotFunctionChooser<LivingEntity, PotionEffectType, PotionEffect> getPotionEffectsFunction =
            SpigotFunctionChooser.apiCompatCall((le, type) -> le.getPotionEffect(type),
                    (le, type) ->
                            le.getActivePotionEffects().stream()
                                    .filter(potionEffect -> potionEffect.getType().equals(type))
                                    .findAny()
                                    .orElse(null)
            );

    /**
     * Returns the {@link PotionEffect} of a given {@link PotionEffectType} for a given {@link LivingEntity}, if present.
     *
     * @param entity the entity to query
     * @param type   the type to search
     * @return the {@link PotionEffect} if present
     */
    public static Optional<PotionEffect> get(LivingEntity entity, PotionEffectType type) {
        return Optional.ofNullable(getOrNull(entity, type));
    }

    /**
     * Returns the {@link PotionEffect} of a given {@link PotionEffectType} for a given {@link LivingEntity}, if present.
     *
     * @param entity the entity to query
     * @param type   the type to search
     * @return the {@link PotionEffect} or null if not present
     */
    public static PotionEffect getOrNull(LivingEntity entity, PotionEffectType type) {
        return getPotionEffectsFunction.apply(entity, type);
    }
}
