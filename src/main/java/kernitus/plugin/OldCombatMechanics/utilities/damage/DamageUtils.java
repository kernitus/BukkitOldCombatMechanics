/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities.damage;

import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class DamageUtils {

    // Attack components order: (Base + Potion effects, scaled by attack delay) + Critical Hit + (Enchantments, scaled by attack delay)
    // Hurt components order: Overdamage - Armour Effects
    // getDamage() returns without armour effects, getFinalDamage() includes armour, armour enchants, blocking

    // 1.9 weapon enchantment calculation
    // SHARPNESS: 1.0 + (float) Math.max(0, i - 1) * 0.5
    // SMITE (undead): (float) i * 2.5F
    // BANE OF ARTHROPODS: (float) i * 2.5F

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
        boolean flag = this.fallDistance > 0.0F && !this.onGround && !this.k_() && !this.V()
        && !this.hasEffect(MobEffectList.BLINDNESS) && this.vehicle == null && entity instanceof EntityLiving;
        Where k_() is being on ladders or vine, and V() is in water.

        In 1.9 the player must also not be sprinting
        */
        return le.getFallDistance() > 0.0F &&
                !le.isOnGround() &&
                !isLivingEntityClimbing(le) &&
                !isInWater(le) &&
                le.getActivePotionEffects().stream().map(PotionEffect::getType).noneMatch(e -> e == PotionEffectType.BLINDNESS) &&
                !le.isInsideVehicle();
    }

    private static boolean isInWater(LivingEntity le) {
        if (Reflector.versionIsNewerOrEqualAs(1, 16, 0))
            return le.isInWater();
        return le.getLocation().getBlock().getType() == Material.WATER;
    }

    private static boolean isLivingEntityClimbing(LivingEntity le) {
        if (Reflector.versionIsNewerOrEqualAs(1, 17, 0))
            return le.isClimbing();
        final Material material = le.getLocation().getBlock().getType();
        return material == Material.LADDER || material == Material.VINE;
    }
}
