/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities.damage;

import kernitus.plugin.OldCombatMechanics.utilities.Messenger;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.SpigotFunctionChooser;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class DamageUtils {

    // Method added in 1.16.4
    private static final SpigotFunctionChooser<LivingEntity, Object, Boolean> isInWater = SpigotFunctionChooser.apiCompatCall(
            (le, params) -> le.isInWater(),
            (le, params) -> le.getLocation().getBlock().getType() == Material.WATER
    );

    // Method added in 1.17.0
    private static final SpigotFunctionChooser<LivingEntity, Object, Boolean> isClimbing = SpigotFunctionChooser.apiCompatCall(
            (le, params) -> le.isClimbing(),
            (le, params) -> {
                final Material material = le.getLocation().getBlock().getType();
                return material == Material.LADDER || material == Material.VINE;
            }
    );

    /**
     * Gets attack cooldown from player, if possible.
     * Method added in 1.16.
     * @param humanEntity The player to get the attack cooldown from
     * @return The attack cooldown, between 0 and 1. Null if method not present in this Spigot version.
     */
    public static Float getAttackCooldown(HumanEntity humanEntity) {
        // In versions below 1.16, the attack cooldown is immediately reset during the attack processing,
        // before the EntityDamageEvent is called. This means even using reflection we cannot recover the cooldown
        // from just before the attack.
        float attackCooldown;
        try {
            attackCooldown = humanEntity.getAttackCooldown();
        } catch (NoSuchMethodError e) {
            Messenger.debug("getAttackCooldown method not found, using alternative behaviour...");
            return null;
        }
        return attackCooldown;
    }

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
     * @param humanEntity Living entity to perform checks on
     * @return Whether hit is critical
     */
    public static boolean isCriticalHit1_8(HumanEntity humanEntity) {
        /* Code from Bukkit 1.8_R3:
        boolean flag = this.fallDistance > 0.0F && !this.onGround && !this.onClimbable() && !this.isInWater()
        && !this.hasEffect(MobEffectList.BLINDNESS) && this.vehicle == null && entity instanceof EntityLiving;
        */
        return humanEntity.getFallDistance() > 0.0F &&
                !humanEntity.isOnGround() &&
                !isClimbing.apply(humanEntity) &&
                !isInWater.apply(humanEntity) &&
                humanEntity.getActivePotionEffects().stream().map(PotionEffect::getType)
                        .noneMatch(e -> e == PotionEffectType.BLINDNESS) &&
                !humanEntity.isInsideVehicle();
    }

    public static boolean isCriticalHit1_9(Player player) {
        final Float attackCooldown = getAttackCooldown(player);
        // If we cannot get the attack cooldown, assume the critical hit happened
        return isCriticalHit1_8(player) && (attackCooldown == null || attackCooldown > 0.9F) && !player.isSprinting();
    }
}
