/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities.damage;

import kernitus.plugin.OldCombatMechanics.utilities.reflection.SpigotFunctionChooser;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class DamageUtils {

    // Method added in 1.16.4
    private static final SpigotFunctionChooser<LivingEntity, Void, Boolean> isInWater = SpigotFunctionChooser.apiCompatCall(
            (le, params) -> le.isInWater(),
            (le, params) -> le.getLocation().getBlock().getType() == Material.WATER
    );

    // Method added in 1.17.0
    private static final SpigotFunctionChooser<LivingEntity, Void, Boolean> isClimbing = SpigotFunctionChooser.apiCompatCall(
            (le, params) -> le.isClimbing(),
            (le, params) -> {
                final Material material = le.getLocation().getBlock().getType();
                return material == Material.LADDER || material == Material.VINE;
            }
    );

    /**
     * Get sharpness damage multiplier for 1.9
     *
     * @param level The level of the enchantment
     * @return Sharpness damage multiplier
     */
    public static double getNewSharpnessDamage(int level) {
        return level >= 1 ? 1 + (level - 1) * 0.5 : 0;
    }

    /**
     * Get sharpness damage multiplier for 1.8
     *
     * @param level The level of the enchantment
     * @return Sharpness damage multiplier
     */
    public static double getOldSharpnessDamage(int level) {
        return level >= 1 ? level * 1.25 : 0;
    }

    /**
     * Check preconditions for critical hit
     *
     * @param le Living entity to perform checks on
     * @return Whether hit is critical
     */
    public static boolean isCriticalHit1_8(LivingEntity le) {
        /* Code from Bukkit 1.8_R3:
        boolean flag = this.fallDistance > 0.0F && !this.onGround && !this.onClimbable() && !this.isInWater()
        && !this.hasEffect(MobEffectList.BLINDNESS) && this.vehicle == null && entity instanceof EntityLiving;
        */
        return le.getFallDistance() > 0.0F &&
                !le.isOnGround() &&
                !isClimbing.apply(le) &&
                !isInWater.apply(le) &&
                le.getActivePotionEffects().stream().map(PotionEffect::getType).noneMatch(e -> e == PotionEffectType.BLINDNESS) &&
                !le.isInsideVehicle();
    }

    public static boolean isCriticalHit1_9(Player player) {
        return isCriticalHit1_8(player) && player.getAttackCooldown() > 0.9F && !player.isSprinting();
    }
}
