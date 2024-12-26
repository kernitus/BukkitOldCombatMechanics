/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities.damage

import kernitus.plugin.OldCombatMechanics.utilities.Messenger
import kernitus.plugin.OldCombatMechanics.utilities.damage.AttackCooldownTracker.Companion.getLastCooldown
import kernitus.plugin.OldCombatMechanics.utilities.reflection.SpigotFunctionChooser
import org.bukkit.Material
import org.bukkit.entity.HumanEntity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

object DamageUtils {
    // Method added in 1.16.4
    private val isInWater: SpigotFunctionChooser<LivingEntity, Any, Boolean> = SpigotFunctionChooser.apiCompatCall(
        { le: LivingEntity, params: Any? -> le.isInWater },
        { le: LivingEntity, params: Any? -> le.location.block.type == Material.WATER }
    )

    // Method added in 1.17.0
    private val isClimbing: SpigotFunctionChooser<LivingEntity, Any, Boolean> = SpigotFunctionChooser.apiCompatCall(
        { le: LivingEntity, params: Any? -> le.isClimbing },
        { le: LivingEntity, params: Any? ->
            val material = le.location.block.type
            material == Material.LADDER || material == Material.VINE
        }
    )

    // Method added in 1.16. Default parameter for reflected method is 0.5F
    @JvmField
    val getAttackCooldown: SpigotFunctionChooser<HumanEntity, Any, Float> = SpigotFunctionChooser.apiCompatCall(
        { he: HumanEntity, params: Any? -> he.attackCooldown },
        { he: HumanEntity, params: Any? -> getAttackCooldown(he) }
    )

    /**
     * Gets last stored attack cooldown. Used when method is not available and we are keeping track of value ourselves.
     * @param humanEntity The player to get the attack cooldown from
     * @return The attack cooldown, as a value between 0 and 1
     */
    private fun getAttackCooldown(humanEntity: HumanEntity): Float {
        val cooldown = getLastCooldown(humanEntity.uniqueId)
        if (cooldown == null) {
            Messenger.debug("Last attack cooldown null for " + humanEntity.name + ", assuming full attack strength")
            return 1f
        }
        return cooldown
    }

    /**
     * Get sharpness damage multiplier for 1.9
     *
     * @param level The level of the enchantment
     * @return Sharpness damage multiplier
     */
    @JvmStatic
    fun getNewSharpnessDamage(level: Int): Double {
        return if (level >= 1) 1 + (level - 1) * 0.5 else 0.0
    }

    /**
     * Get sharpness damage multiplier for 1.8
     *
     * @param level The level of the enchantment
     * @return Sharpness damage multiplier
     */
    @JvmStatic
    fun getOldSharpnessDamage(level: Int): Double {
        return if (level >= 1) level * 1.25 else 0.0
    }

    /**
     * Check preconditions for critical hit
     *
     * @param humanEntity Living entity to perform checks on
     * @return Whether hit is critical
     */
    @JvmStatic
    fun isCriticalHit1_8(humanEntity: HumanEntity): Boolean {
        /* Code from Bukkit 1.8_R3:
        boolean flag = this.fallDistance > 0.0F && !this.onGround && !this.onClimbable() && !this.isInWater()
        && !this.hasEffect(MobEffectList.BLINDNESS) && this.vehicle == null && entity instanceof EntityLiving;
        */
        return humanEntity.fallDistance > 0.0f && !humanEntity.isOnGround && !isClimbing.apply(humanEntity) && !isInWater.apply(
            humanEntity
        ) &&
                humanEntity.activePotionEffects.stream().map { obj: PotionEffect -> obj.type }
                    .noneMatch { e: PotionEffectType -> e === PotionEffectType.BLINDNESS } && !humanEntity.isInsideVehicle
    }

    @JvmStatic
    fun isCriticalHit1_9(player: Player): Boolean {
        return isCriticalHit1_8(player) && getAttackCooldown.apply(player) > 0.9f && !player.isSprinting
    }
}
