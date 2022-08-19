/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics.utilities.damage;

import kernitus.plugin.OldCombatMechanics.utilities.Messenger;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Utilities for calculating damage reduction from armour and armour enchantments
 */
public class ArmourUtils {
    private static final double REDUCTION_PER_ARMOUR_POINT = 0.04;

    private static final Set<EntityDamageEvent.DamageCause> NON_REDUCED_CAUSES = EnumSet.of(
            EntityDamageEvent.DamageCause.FIRE_TICK,
            EntityDamageEvent.DamageCause.VOID,
            EntityDamageEvent.DamageCause.SUFFOCATION,
            EntityDamageEvent.DamageCause.DROWNING,
            EntityDamageEvent.DamageCause.STARVATION,
            EntityDamageEvent.DamageCause.FALL,
            EntityDamageEvent.DamageCause.MAGIC,
            EntityDamageEvent.DamageCause.LIGHTNING
    );

    static {
        if (Reflector.versionIsNewerOrEqualAs(1, 17, 0))
            NON_REDUCED_CAUSES.add(EntityDamageEvent.DamageCause.FREEZE);
    }

    /**
     * Calculates the reduction by armour and armour enchantments for each DamageModifier
     * The values are updated directly in the map
     * @param damagedEntity The entity that was damaged
     * @param damageModifiers A map of the damagemodifiers and their values from the event
     * @param damageCause The cause of the damage
     */
    public static void calculateArmourDamageReduction(LivingEntity damagedEntity,
                                                      Map<EntityDamageEvent.DamageModifier, Double> damageModifiers,
                                                      EntityDamageEvent.DamageCause damageCause) {

        final double armourPoints = damagedEntity.getAttribute(Attribute.GENERIC_ARMOR).getValue();
        final double reductionPercentage = armourPoints * REDUCTION_PER_ARMOUR_POINT;

        // Apply armour damage reduction after blocking reduction
        final double blockedDamage = damageModifiers.containsKey(EntityDamageEvent.DamageModifier.BLOCKING) ? damageModifiers.get(EntityDamageEvent.DamageModifier.BLOCKING) : 0;
        final double armourReduction =
                (!NON_REDUCED_CAUSES.contains(damageCause) && damageModifiers.containsKey(EntityDamageEvent.DamageModifier.ARMOR)) ?
                        -((damageModifiers.get(EntityDamageEvent.DamageModifier.BASE) + blockedDamage)
                                * reductionPercentage) : 0;
        damageModifiers.put(EntityDamageEvent.DamageModifier.ARMOR, armourReduction);

        final double provisionFinalDamage = damageModifiers.keySet().stream()
                .filter(key -> key != EntityDamageEvent.DamageModifier.MAGIC) // Ignore armour enchantment reduction
                .map(damageModifiers::get)
                .reduce(0.0, Double::sum);

        // Don't calculate enchantment reduction if damage is already 0. NMS 1.8 does it this way.
        if (provisionFinalDamage > 0 && damageModifiers.containsKey(EntityDamageEvent.DamageModifier.MAGIC)) {
            //Set new MAGIC (Armour enchants) damage
            damageModifiers.put(EntityDamageEvent.DamageModifier.MAGIC,
                    provisionFinalDamage *
                            -calculateArmourEnchantmentReductionPercentage(damagedEntity.getEquipment().getArmorContents(), damageCause));
        }

        /*
        final double finalDamage = damageModifiers.values().stream().reduce(0.0, Double::sum);
        debug(String.format("Reductions: Armour %.0f, Ench %.0f, Total %.2f, Final Damage: %.2f", reductionPercentage * 100,
                enchantmentReductionPercentage * 100, (reductionPercentage + (1 - reductionPercentage) * enchantmentReductionPercentage) * 100,
                finalDamage), damagedEntity);
         */
    }

    /**
     * Return the damage after applying armour and armour enchants protections, following 1.8 algorithm.
     * @param baseDamage The base damage done by the event, including weapon enchants, potions, crits
     * @param armourPoints The armor.generic attribute on the player, i.e. the sum of protection provided by each armour piece
     * @param armourContents The 4 pieces of armour contained in the armour slots
     * @param damageCause The source of damage
     * @return The damage done to the entity after armour is taken into account
     */
    public static double getDamageAfterArmour1_8(double baseDamage, double armourPoints, ItemStack[] armourContents, EntityDamageEvent.DamageCause damageCause){
        final double reductionPercentage = armourPoints * REDUCTION_PER_ARMOUR_POINT;

        // Apply armour damage reduction after blocking reduction
        double finalDamage = baseDamage - (!NON_REDUCED_CAUSES.contains(damageCause) ? (baseDamage * reductionPercentage) : 0);

        // Don't calculate enchantment reduction if damage is already 0. NMS 1.8 does it this way.
        final double enchantmentReductionPercentage = calculateArmourEnchantmentReductionPercentage(armourContents, damageCause);
        if (finalDamage > 0) {
            finalDamage -= finalDamage * enchantmentReductionPercentage;
        }

        Messenger.debug("Reductions: Armour %.0f%%, Ench %.0f%%, Total %.2f%%, Start dmg: %.2f Final: %.2f", reductionPercentage * 100,
                enchantmentReductionPercentage * 100, (reductionPercentage + (1 - reductionPercentage) * enchantmentReductionPercentage) * 100,
                baseDamage, finalDamage);

        return finalDamage;
    }

    private static double calculateArmourEnchantmentReductionPercentage(ItemStack[] armourContents, EntityDamageEvent.DamageCause cause) {
        int totalEpf = 0;
        for (ItemStack armourItem : armourContents) {
            if (armourItem != null && armourItem.getType() != Material.AIR) {
                for (EnchantmentType enchantmentType : EnchantmentType.values()) {
                    if (!enchantmentType.protectsAgainst(cause)) continue;

                    int enchantmentLevel = armourItem.getEnchantmentLevel(enchantmentType.getEnchantment());

                    if (enchantmentLevel > 0) {
                        totalEpf += enchantmentType.getEpf(enchantmentLevel);
                    }
                }
            }
        }

        // capped at 25
        totalEpf = Math.min(25, totalEpf);

        totalEpf = (int) Math.ceil(totalEpf * ThreadLocalRandom.current().nextDouble(0.5, 1));

        // capped at 20
        totalEpf = Math.min(20, totalEpf);

        return REDUCTION_PER_ARMOUR_POINT * totalEpf;
    }

    private enum EnchantmentType {
        // Data from https://minecraft.fandom.com/wiki/Armor#Mechanics
        PROTECTION(() -> {
            EnumSet<EntityDamageEvent.DamageCause> damageCauses = EnumSet.of(
                    EntityDamageEvent.DamageCause.CONTACT,
                    EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                    EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK,
                    EntityDamageEvent.DamageCause.PROJECTILE,
                    EntityDamageEvent.DamageCause.FALL,
                    EntityDamageEvent.DamageCause.FIRE,
                    EntityDamageEvent.DamageCause.LAVA,
                    EntityDamageEvent.DamageCause.BLOCK_EXPLOSION,
                    EntityDamageEvent.DamageCause.ENTITY_EXPLOSION,
                    EntityDamageEvent.DamageCause.LIGHTNING,
                    EntityDamageEvent.DamageCause.POISON,
                    EntityDamageEvent.DamageCause.MAGIC,
                    EntityDamageEvent.DamageCause.WITHER,
                    EntityDamageEvent.DamageCause.FALLING_BLOCK,
                    EntityDamageEvent.DamageCause.THORNS,
                    EntityDamageEvent.DamageCause.DRAGON_BREATH
            );
            if (Reflector.versionIsNewerOrEqualAs(1, 10, 0))
                damageCauses.add(EntityDamageEvent.DamageCause.HOT_FLOOR);

            return damageCauses;
        },
                0.75, Enchantment.PROTECTION_ENVIRONMENTAL),
        FIRE_PROTECTION(() -> {
            EnumSet<EntityDamageEvent.DamageCause> damageCauses = EnumSet.of(
                    EntityDamageEvent.DamageCause.FIRE,
                    EntityDamageEvent.DamageCause.FIRE_TICK,
                    EntityDamageEvent.DamageCause.LAVA
            );

            if (Reflector.versionIsNewerOrEqualAs(1, 10, 0)) {
                damageCauses.add(EntityDamageEvent.DamageCause.HOT_FLOOR);
            }

            return damageCauses;
        }, 1.25, Enchantment.PROTECTION_FIRE),
        BLAST_PROTECTION(() -> EnumSet.of(
                EntityDamageEvent.DamageCause.ENTITY_EXPLOSION,
                EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
        ), 1.5, Enchantment.PROTECTION_EXPLOSIONS),
        PROJECTILE_PROTECTION(() -> EnumSet.of(
                EntityDamageEvent.DamageCause.PROJECTILE
        ), 1.5, Enchantment.PROTECTION_PROJECTILE),
        FALL_PROTECTION(() -> EnumSet.of(
                EntityDamageEvent.DamageCause.FALL
        ), 2.5, Enchantment.PROTECTION_FALL);

        private final Set<EntityDamageEvent.DamageCause> protection;
        private final double typeModifier;
        private final Enchantment enchantment;

        EnchantmentType(Supplier<Set<EntityDamageEvent.DamageCause>> protection, double typeModifier, Enchantment enchantment) {
            this.protection = protection.get();
            this.typeModifier = typeModifier;
            this.enchantment = enchantment;
        }

        /**
         * Returns whether the armour protects against the given damage cause.
         *
         * @param cause the damage cause
         * @return true if the armour protects against the given damage cause
         */
        public boolean protectsAgainst(EntityDamageEvent.DamageCause cause) {
            return protection.contains(cause);
        }

        /**
         * Returns the bukkit enchantment.
         *
         * @return the bukkit enchantment
         */
        public Enchantment getEnchantment() {
            return enchantment;
        }

        /**
         * Returns the enchantment protection factor (EPF).
         *
         * @param level the level of the enchantment
         * @return the EPF
         */
        public int getEpf(int level) {
            // floor ( (6 + level^2) * TypeModifier / 3 )
            return (int) Math.floor((6 + level * level) * typeModifier / 3);
        }
    }
}
