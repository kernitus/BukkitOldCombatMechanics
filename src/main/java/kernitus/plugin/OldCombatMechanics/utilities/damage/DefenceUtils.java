/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics.utilities.damage;

import kernitus.plugin.OldCombatMechanics.utilities.Messenger;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.SpigotFunctionChooser;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.VersionCompatUtils;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Utilities for calculating damage reduction from armour and status effects.
 * Defence order is armour defence -> resistance -> armour enchants -> absorption
 * BASE -> HARD_HAT -> BLOCKING -> ARMOUR -> RESISTANCE -> MAGIC -> ABSORPTION
 * This class just deals with everything from armour onwards
 */
public class DefenceUtils {
    private static final double REDUCTION_PER_ARMOUR_POINT = 0.04;
    private static final double REDUCTION_PER_RESISTANCE_LEVEL = 0.2;

    private static final Set<EntityDamageEvent.DamageCause> ARMOUR_IGNORING_CAUSES = EnumSet.of(
            EntityDamageEvent.DamageCause.FIRE_TICK,
            EntityDamageEvent.DamageCause.SUFFOCATION,
            EntityDamageEvent.DamageCause.DROWNING,
            EntityDamageEvent.DamageCause.STARVATION,
            EntityDamageEvent.DamageCause.FALL,
            EntityDamageEvent.DamageCause.VOID,
            EntityDamageEvent.DamageCause.CUSTOM,
            EntityDamageEvent.DamageCause.MAGIC,
            EntityDamageEvent.DamageCause.WITHER,
            // From 1.9
            EntityDamageEvent.DamageCause.FLY_INTO_WALL,
            EntityDamageEvent.DamageCause.DRAGON_BREATH
            // In 1.19 FIRE bypasses armour, but it doesn't in 1.8 so we don't add it here
    );

    // Stalagmite ignores armour but other blocks under CONTACT do not, explicitly checked below
    static {
        if (Reflector.versionIsNewerOrEqualAs(1, 11, 0))
            ARMOUR_IGNORING_CAUSES.add(EntityDamageEvent.DamageCause.CRAMMING);
        if (Reflector.versionIsNewerOrEqualAs(1, 17, 0))
            ARMOUR_IGNORING_CAUSES.add(EntityDamageEvent.DamageCause.FREEZE);
    }

    // Method added in 1.15
    private static final SpigotFunctionChooser<LivingEntity, Object, Double> getAbsorptionAmount =
            SpigotFunctionChooser.apiCompatCall(
                    (le, params) -> le.getAbsorptionAmount(),
                    (le, params) -> Double.valueOf(VersionCompatUtils.getAbsorptionAmount(le)));

    /**
     * Calculates the reduction by armour, resistance, armour enchantments and absorption.
     * The values are updated directly in the map for each damage modifier.
     *
     * @param damagedEntity   The entity that was damaged
     * @param damageModifiers A map of the damage modifiers and their values from the event
     * @param damageCause     The cause of the damage
     */
    @SuppressWarnings("deprecation")
    public static void calculateDefenceDamageReduction(LivingEntity damagedEntity,
                                                       Map<EntityDamageEvent.DamageModifier, Double> damageModifiers,
                                                       EntityDamageEvent.DamageCause damageCause) {

        final double armourPoints = damagedEntity.getAttribute(Attribute.GENERIC_ARMOR).getValue();
        // Make sure we don't go over 100% protection
        final double armourReductionFactor = Math.min(1.0, armourPoints * REDUCTION_PER_ARMOUR_POINT);

        // applyArmorModifier() calculations from NMS
        // Apply armour damage reduction after hard hat (wearing helmet & hit by block) and blocking reduction
        double currentDamage = damageModifiers.get(EntityDamageEvent.DamageModifier.BASE) +
                damageModifiers.getOrDefault(EntityDamageEvent.DamageModifier.HARD_HAT, 0.0) +
                damageModifiers.getOrDefault(EntityDamageEvent.DamageModifier.BLOCKING, 0.0);
        if (damageModifiers.containsKey(EntityDamageEvent.DamageModifier.ARMOR)) {
            double armourReduction = 0;
            // If the damage cause does not ignore armour
            // If the block they are in is a stalagmite, also ignore armour
            if (!ARMOUR_IGNORING_CAUSES.contains(damageCause) &&
                    !(Reflector.versionIsNewerOrEqualAs(1, 19, 0) &&
                            damageCause == EntityDamageEvent.DamageCause.CONTACT &&
                            damagedEntity.getLocation().getBlock().getType() == Material.POINTED_DRIPSTONE)
            ) {
                armourReduction = currentDamage * -armourReductionFactor;
            }
            damageModifiers.put(EntityDamageEvent.DamageModifier.ARMOR, armourReduction);
            currentDamage += armourReduction;
        }

        // This is the applyMagicModifier() calculations from NMS
        if (damageCause != EntityDamageEvent.DamageCause.STARVATION) {
            // Apply resistance effect
            if (damageModifiers.containsKey(EntityDamageEvent.DamageModifier.RESISTANCE) &&
                    damageCause != EntityDamageEvent.DamageCause.VOID &&
                    damagedEntity.hasPotionEffect(PotionEffectType.DAMAGE_RESISTANCE)) {
                final int level = damagedEntity.getPotionEffect(PotionEffectType.DAMAGE_RESISTANCE).getAmplifier() + 1;
                // Make sure we don't go over 100% protection
                final double resistanceReductionFactor = Math.min(1.0, level * REDUCTION_PER_RESISTANCE_LEVEL);
                final double resistanceReduction = -resistanceReductionFactor * currentDamage;
                damageModifiers.put(EntityDamageEvent.DamageModifier.RESISTANCE, resistanceReduction);
                currentDamage += resistanceReduction;
            }

            // Apply armour enchants
            // Don't calculate enchants if damage already 0 (like 1.8 NMS). Enchants cap at 80% reduction
            if (currentDamage > 0 && damageModifiers.containsKey(EntityDamageEvent.DamageModifier.MAGIC)) {
                final double enchantsReductionFactor = calculateArmourEnchantmentReductionFactor(damagedEntity.getEquipment().getArmorContents(), damageCause);
                final double enchantsReduction = currentDamage * -enchantsReductionFactor;
                damageModifiers.put(EntityDamageEvent.DamageModifier.MAGIC, enchantsReduction);
                currentDamage += enchantsReduction;
            }

            // Absorption
            if(damageModifiers.containsKey(EntityDamageEvent.DamageModifier.ABSORPTION)) {
                final double absorptionAmount = getAbsorptionAmount.apply(damagedEntity);
                double absorptionReduction = -Math.min(absorptionAmount, currentDamage);
                damageModifiers.put(EntityDamageEvent.DamageModifier.ABSORPTION, absorptionReduction);
            }
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
     *
     * @param baseDamage     The base damage done by the event, including weapon enchants, potions, crits
     * @param armourContents The 4 pieces of armour contained in the armour slots
     * @param damageCause    The source of damage
     * @return The damage done to the entity after armour is taken into account
     */
    public static double getDamageAfterArmour1_8(double baseDamage, ItemStack[] armourContents, EntityDamageEvent.DamageCause damageCause) {
        double armourPoints = 0;
        for (int i = 0; i < armourContents.length; i++) {
            final ItemStack itemStack = armourContents[i];
            if (itemStack == null) continue;
            final EquipmentSlot slot = new EquipmentSlot[]{EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD}[i];
            armourPoints += getAttributeModifierSum(itemStack.getType().getDefaultAttributeModifiers(slot).get(Attribute.GENERIC_ARMOR));
        }

        final double reductionFactor = armourPoints * REDUCTION_PER_ARMOUR_POINT;

        // Apply armour damage reduction
        double finalDamage = baseDamage - (ARMOUR_IGNORING_CAUSES.contains(damageCause) ? 0 : (baseDamage * reductionFactor));

        // Don't calculate enchantment reduction if damage is already 0. NMS 1.8 does it this way.
        final double enchantmentReductionFactor = calculateArmourEnchantmentReductionFactor(armourContents, damageCause);
        if (finalDamage > 0) {
            finalDamage -= finalDamage * enchantmentReductionFactor;
        }

        Messenger.debug("Reductions: Armour %.0f%%, Ench %.0f%%, Total %.2f%%, Start dmg: %.2f Final: %.2f", reductionFactor * 100,
                enchantmentReductionFactor * 100, (reductionFactor + (1 - reductionFactor) * enchantmentReductionFactor) * 100,
                baseDamage, finalDamage);

        return finalDamage;
    }

    /**
     * Applies all the operations for the attribute modifiers of a specific attribute.
     * Does not take into account the base value.
     */
    private static double getAttributeModifierSum(Collection<AttributeModifier> modifiers) {
        double sum = 0;
        for (AttributeModifier modifier : modifiers) {
            final double value = modifier.getAmount();
            switch (modifier.getOperation()) {
                case ADD_SCALAR:
                    sum += Math.abs(value);
                    break;
                case ADD_NUMBER:
                    sum += value;
                    break;
                case MULTIPLY_SCALAR_1:
                    sum *= value;
                    break;
            }
        }
        return sum;
    }

    private static double calculateArmourEnchantmentReductionFactor(ItemStack[] armourContents, EntityDamageEvent.DamageCause cause) {
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

        // Cap at 25
        totalEpf = Math.min(25, totalEpf);

        // Multiply by random value between 50% and 100%, then round up
        totalEpf = (int) Math.ceil(totalEpf * ThreadLocalRandom.current().nextDouble(0.5, 1));
        // totalEpf = (int) Math.ceil(totalEpf); // remove randomness for testing

        // Cap at 20
        totalEpf = Math.min(20, totalEpf);

        return REDUCTION_PER_ARMOUR_POINT * totalEpf;
    }

    private enum EnchantmentType {
        // Data from https://minecraft.fandom.com/wiki/Armor#Mechanics
        PROTECTION(() -> {
            EnumSet<EntityDamageEvent.DamageCause> damageCauses = EnumSet.of(
                    EntityDamageEvent.DamageCause.CONTACT,
                    EntityDamageEvent.DamageCause.ENTITY_ATTACK,
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
            if (Reflector.versionIsNewerOrEqualAs(1, 12, 0))
                damageCauses.add(EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK);

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
