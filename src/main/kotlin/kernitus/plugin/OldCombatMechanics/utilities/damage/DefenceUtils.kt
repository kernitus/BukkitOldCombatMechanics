/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities.damage

import kernitus.plugin.OldCombatMechanics.utilities.Messenger
import kernitus.plugin.OldCombatMechanics.utilities.potions.PotionEffectTypeCompat
import kernitus.plugin.OldCombatMechanics.utilities.reflection.SpigotFunctionChooser
import kernitus.plugin.OldCombatMechanics.utilities.reflection.VersionCompatUtils
import kernitus.plugin.OldCombatMechanics.versions.enchantments.EnchantmentCompat
import org.bukkit.Material
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.LivingEntity
import org.bukkit.event.entity.EntityDamageEvent.DamageCause
import org.bukkit.event.entity.EntityDamageEvent.DamageModifier
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import java.util.function.Supplier
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min

/**
 * Utilities for calculating damage reduction from armour and status effects.
 * Defence order is armour defence -> resistance -> armour enchants -> absorption
 * BASE -> HARD_HAT -> BLOCKING -> ARMOUR -> RESISTANCE -> MAGIC -> ABSORPTION
 * This class just deals with everything from armour onwards
 */
object DefenceUtils {
    private const val REDUCTION_PER_ARMOUR_POINT = 0.04
    private const val REDUCTION_PER_RESISTANCE_LEVEL = 0.2

    private val ARMOUR_IGNORING_CAUSES: MutableSet<DamageCause> = EnumSet.of(
        DamageCause.FIRE_TICK,
        DamageCause.SUFFOCATION,
        DamageCause.DROWNING,
        DamageCause.STARVATION,
        DamageCause.FALL,
        DamageCause.VOID,
        DamageCause.CUSTOM,
        DamageCause.MAGIC,
        DamageCause.WITHER,  // From 1.9
        DamageCause.FLY_INTO_WALL,
        DamageCause.DRAGON_BREATH // In 1.19 FIRE bypasses armour, but it doesn't in 1.8 so we don't add it here
    )

    // Stalagmite ignores armour but other blocks under CONTACT do not, explicitly checked below
    init {
        if (VersionCompatUtils.versionIsNewerOrEqualTo(1, 11, 0)) ARMOUR_IGNORING_CAUSES.add(
            DamageCause.CRAMMING
        )
        if (VersionCompatUtils.versionIsNewerOrEqualTo(1, 17, 0)) ARMOUR_IGNORING_CAUSES.add(
            DamageCause.FREEZE
        )
    }

    // Method added in 1.15
    private val getAbsorptionAmount: SpigotFunctionChooser<LivingEntity, Any, Double> =
        SpigotFunctionChooser.apiCompatCall(
            { le: LivingEntity, _: Any? -> le.absorptionAmount },
            { le: LivingEntity, _: Any? ->
                VersionCompatUtils.getAbsorptionAmount(le)
                    .toDouble()
            })

    /**
     * Calculates the reduction by armour, resistance, armour enchantments and absorption.
     * The values are updated directly in the map for each damage modifier.
     *
     * @param damagedEntity   The entity that was damaged
     * @param damageModifiers A map of the damage modifiers and their values from the event
     * @param damageCause     The cause of the damage
     */
    @JvmStatic
    @Suppress("deprecation")
    fun calculateDefenceDamageReduction(
        damagedEntity: LivingEntity,
        damageModifiers: MutableMap<DamageModifier, Double>,
        damageCause: DamageCause,
        randomness: Boolean
    ) {
        val armourPoints = damagedEntity.getAttribute(Attribute.GENERIC_ARMOR)?.value ?: 0.0
        // Make sure we don't go over 100% protection
        val armourReductionFactor = min(1.0, armourPoints * REDUCTION_PER_ARMOUR_POINT)

        // applyArmorModifier() calculations from NMS
        // Apply armour damage reduction after hard hat (wearing helmet & hit by block) and blocking reduction
        var currentDamage = damageModifiers.getOrDefault(DamageModifier.BASE, 0.0) +
                damageModifiers.getOrDefault(DamageModifier.HARD_HAT, 0.0) +
                damageModifiers.getOrDefault(DamageModifier.BLOCKING, 0.0)

        if (damageModifiers.containsKey(DamageModifier.ARMOR)) {
            var armourReduction = 0.0
            // If the damage cause does not ignore armour
            // If the block they are in is a stalagmite, also ignore armour
            if (!ARMOUR_IGNORING_CAUSES.contains(damageCause) &&
                !(VersionCompatUtils.versionIsNewerOrEqualTo(
                    1,
                    19,
                    0
                ) && damageCause == DamageCause.CONTACT && damagedEntity.location.block.type == Material.POINTED_DRIPSTONE)
            ) {
                armourReduction = currentDamage * -armourReductionFactor
            }
            damageModifiers[DamageModifier.ARMOR] = armourReduction
            currentDamage += armourReduction
        }

        // This is the applyMagicModifier() calculations from NMS
        if (damageCause != DamageCause.STARVATION) {
            // Apply resistance effect
            if (damageModifiers.containsKey(DamageModifier.RESISTANCE) && damageCause != DamageCause.VOID &&
                damagedEntity.hasPotionEffect(PotionEffectTypeCompat.RESISTANCE.potionEffectType)
            ) {
                val level =
                    (damagedEntity.getPotionEffect(PotionEffectTypeCompat.RESISTANCE.potionEffectType)?.amplifier
                        ?: 0) + 1
                // Make sure we don't go over 100% protection
                val resistanceReductionFactor = min(1.0, level * REDUCTION_PER_RESISTANCE_LEVEL)
                val resistanceReduction = -resistanceReductionFactor * currentDamage
                damageModifiers[DamageModifier.RESISTANCE] = resistanceReduction
                currentDamage += resistanceReduction
            }

            // Apply armour enchants
            // Don't calculate enchants if damage already 0 (like 1.8 NMS). Enchants cap at 80% reduction
            if (currentDamage > 0 && damageModifiers.containsKey(DamageModifier.MAGIC)) {
                val enchantsReductionFactor = calculateArmourEnchantmentReductionFactor(
                    damagedEntity.equipment?.armorContents ?: emptyArray(), damageCause, randomness
                )
                val enchantsReduction = currentDamage * -enchantsReductionFactor
                damageModifiers[DamageModifier.MAGIC] = enchantsReduction
                currentDamage += enchantsReduction
            }

            // Absorption
            if (damageModifiers.containsKey(DamageModifier.ABSORPTION)) {
                val absorptionAmount = getAbsorptionAmount(damagedEntity)
                val absorptionReduction = -min(absorptionAmount, currentDamage)
                damageModifiers[DamageModifier.ABSORPTION] = absorptionReduction
            }
        }
    }

    /**
     * Return the damage after applying armour, resistance, and armour enchants protections, following 1.8 algorithm.
     *
     * @param defender       The entity that is being attacked
     * @param baseDamage     The base damage done by the event, including weapon enchants, potions, crits
     * @param armourContents The 4 pieces of armour contained in the armour slots
     * @param damageCause    The source of damage
     * @param randomness     Whether to apply random multiplier
     * @return The damage done to the entity after armour is taken into account
     */
    fun getDamageAfterArmour1_8(
        defender: LivingEntity,
        baseDamage: Double,
        armourContents: Array<ItemStack>,
        damageCause: DamageCause,
        randomness: Boolean
    ): Double {
        var armourPoints = 0.0
        val nullableStack = arrayOfNulls<ItemStack?>(4)

        for (i in armourContents.indices) {
            val itemStack = armourContents[i]
            val slot =
                arrayOf(
                    EquipmentSlot.FEET,
                    EquipmentSlot.LEGS,
                    EquipmentSlot.CHEST,
                    EquipmentSlot.HEAD
                )[i]
            armourPoints += getAttributeModifierSum(itemStack.type.getDefaultAttributeModifiers(slot)[Attribute.GENERIC_ARMOR])
            nullableStack[i] = itemStack
        }

        val reductionFactor = armourPoints * REDUCTION_PER_ARMOUR_POINT

        // Apply armour damage reduction
        var finalDamage =
            baseDamage - (if (ARMOUR_IGNORING_CAUSES.contains(damageCause)) 0.0 else (baseDamage * reductionFactor))

        // Calculate resistance
        if (defender.hasPotionEffect(PotionEffectTypeCompat.RESISTANCE.potionEffectType)) {
            val resistanceLevel =
                (defender.getPotionEffect(PotionEffectTypeCompat.RESISTANCE.potionEffectType)?.amplifier ?: 0) + 1
            finalDamage *= 1.0 - (resistanceLevel * 0.2)
        }

        // Don't calculate enchantment reduction if damage is already 0. NMS 1.8 does it this way.
        val enchantmentReductionFactor =
            calculateArmourEnchantmentReductionFactor(nullableStack, damageCause, randomness)
        if (finalDamage > 0) {
            finalDamage -= finalDamage * enchantmentReductionFactor
        }

        Messenger.debug(
            "Reductions: Armour %.0f%%, Ench %.0f%%, Total %.2f%%, Start dmg: %.2f Final: %.2f",
            reductionFactor * 100,
            enchantmentReductionFactor * 100,
            (reductionFactor + (1 - reductionFactor) * enchantmentReductionFactor) * 100,
            baseDamage,
            finalDamage
        )

        return finalDamage
    }

    /**
     * Applies all the operations for the attribute modifiers of a specific attribute.
     * Does not take into account the base value.
     */
    private fun getAttributeModifierSum(modifiers: Collection<AttributeModifier>): Double {
        var sum = 0.0
        for (modifier in modifiers) {
            val value = modifier.amount
            when (modifier.operation) {
                AttributeModifier.Operation.ADD_SCALAR -> sum += abs(value)
                AttributeModifier.Operation.ADD_NUMBER -> sum += value
                AttributeModifier.Operation.MULTIPLY_SCALAR_1 -> sum *= value
            }
        }
        return sum
    }


    private fun calculateArmourEnchantmentReductionFactor(
        armourContents: Array<ItemStack?>,
        cause: DamageCause,
        randomness: Boolean
    ): Double {
        var totalEpf = 0
        for (armourItem in armourContents) {
            if (armourItem != null && armourItem.type != Material.AIR) {
                for (enchantmentType in EnchantmentType.entries) {
                    if (!enchantmentType.protectsAgainst(cause)) continue

                    val enchantmentLevel = armourItem.getEnchantmentLevel(enchantmentType.enchantment)

                    if (enchantmentLevel > 0) {
                        totalEpf += enchantmentType.getEpf(enchantmentLevel)
                    }
                }
            }
        }

        // Cap at 25
        totalEpf = min(25.0, totalEpf.toDouble()).toInt()

        // Multiply by random value between 50% and 100%, then round up
        val multiplier = if (randomness) ThreadLocalRandom.current().nextDouble(0.5, 1.0) else 1.0
        totalEpf = ceil(totalEpf * multiplier).toInt()

        // Cap at 20
        totalEpf = min(20.0, totalEpf.toDouble()).toInt()

        return REDUCTION_PER_ARMOUR_POINT * totalEpf
    }

    private enum class EnchantmentType(
        protection: Supplier<Set<DamageCause>>, private val typeModifier: Double,
        /**
         * Returns the bukkit enchantment.
         *
         * @return the bukkit enchantment
         */
        val enchantment: Enchantment
    ) {
        // Data from https://minecraft.fandom.com/wiki/Armor#Mechanics
        PROTECTION(
            Supplier {
                val damageCauses = EnumSet.of(
                    DamageCause.CONTACT,
                    DamageCause.ENTITY_ATTACK,
                    DamageCause.PROJECTILE,
                    DamageCause.FALL,
                    DamageCause.FIRE,
                    DamageCause.LAVA,
                    DamageCause.BLOCK_EXPLOSION,
                    DamageCause.ENTITY_EXPLOSION,
                    DamageCause.LIGHTNING,
                    DamageCause.POISON,
                    DamageCause.MAGIC,
                    DamageCause.WITHER,
                    DamageCause.FALLING_BLOCK,
                    DamageCause.THORNS,
                    DamageCause.DRAGON_BREATH
                )
                if (VersionCompatUtils.versionIsNewerOrEqualTo(1, 10, 0)) damageCauses.add(
                    DamageCause.HOT_FLOOR
                )
                if (VersionCompatUtils.versionIsNewerOrEqualTo(1, 12, 0)) damageCauses.add(
                    DamageCause.ENTITY_SWEEP_ATTACK
                )
                damageCauses
            },
            0.75, EnchantmentCompat.PROTECTION.get()
        ),
        FIRE_PROTECTION(Supplier {
            val damageCauses = EnumSet.of(
                DamageCause.FIRE,
                DamageCause.FIRE_TICK,
                DamageCause.LAVA
            )
            if (VersionCompatUtils.versionIsNewerOrEqualTo(1, 10, 0)) {
                damageCauses.add(DamageCause.HOT_FLOOR)
            }
            damageCauses
        }, 1.25, EnchantmentCompat.FIRE_PROTECTION.get()),
        BLAST_PROTECTION(Supplier {
            EnumSet.of(
                DamageCause.ENTITY_EXPLOSION,
                DamageCause.BLOCK_EXPLOSION
            )
        }, 1.5, EnchantmentCompat.BLAST_PROTECTION.get()),
        PROJECTILE_PROTECTION(Supplier {
            EnumSet.of(
                DamageCause.PROJECTILE
            )
        }, 1.5, EnchantmentCompat.PROJECTILE_PROTECTION.get()),
        FALL_PROTECTION(Supplier {
            EnumSet.of(
                DamageCause.FALL
            )
        }, 2.5, EnchantmentCompat.FEATHER_FALLING.get());

        private val protection = protection.get()

        /**
         * Returns whether the armour protects against the given damage cause.
         *
         * @param cause the damage cause
         * @return true if the armour protects against the given damage cause
         */
        fun protectsAgainst(cause: DamageCause): Boolean {
            return protection.contains(cause)
        }

        /**
         * Returns the enchantment protection factor (EPF).
         *
         * @param level the level of the enchantment
         * @return the EPF
         */
        fun getEpf(level: Int): Int {
            // floor ( (6 + level^2) * TypeModifier / 3 )
            return floor((6 + level * level) * typeModifier / 3).toInt()
        }
    }
}
