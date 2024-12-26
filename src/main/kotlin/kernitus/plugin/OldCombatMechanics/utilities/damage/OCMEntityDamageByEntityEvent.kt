/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities.damage

import kernitus.plugin.OldCombatMechanics.utilities.Messenger
import kernitus.plugin.OldCombatMechanics.utilities.damage.DamageUtils.getNewSharpnessDamage
import kernitus.plugin.OldCombatMechanics.utilities.damage.DamageUtils.isCriticalHit1_8
import kernitus.plugin.OldCombatMechanics.utilities.damage.DamageUtils.isCriticalHit1_9
import kernitus.plugin.OldCombatMechanics.utilities.damage.MobDamage.getEntityEnchantmentsDamage
import kernitus.plugin.OldCombatMechanics.utilities.potions.PotionEffectTypeCompat
import kernitus.plugin.OldCombatMechanics.utilities.potions.PotionEffects
import kernitus.plugin.OldCombatMechanics.versions.enchantments.EnchantmentCompat
import org.bukkit.Material
import org.bukkit.entity.Entity
import org.bukkit.entity.HumanEntity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.bukkit.event.entity.EntityDamageEvent.DamageCause
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffectType

class OCMEntityDamageByEntityEvent(
    @JvmField val damager: Entity, @JvmField val damagee: Entity, @JvmField val cause: DamageCause, rawDamage: Double
) : Event(), Cancellable {
    private var cancelled = false
    override fun getHandlers(): HandlerList {
        return handlerList
    }


    var rawDamage: Double
        private set

    var weapon: ItemStack?
        private set

    @JvmField
    val sharpnessLevel: Int
    private val hasWeakness: Boolean

    // The levels as shown in-game, i.e. 1 or 2 corresponding to I and II
    val strengthLevel: Int

    @JvmField
    var weaknessLevel: Int

    @JvmField
    var baseDamage: Double = 0.0
    var mobEnchantmentsDamage: Double = 0.0

    @JvmField
    var sharpnessDamage: Double = 0.0

    @JvmField
    var criticalMultiplier: Double = 1.0

    @JvmField
    var strengthModifier: Double = 0.0

    @JvmField
    var weaknessModifier: Double = 0.0

    // In 1.9 strength modifier is an addend, in 1.8 it is a multiplier and addend (+130%)
    @JvmField
    var isStrengthModifierMultiplier: Boolean = false

    @JvmField
    var isStrengthModifierAddend: Boolean = true

    @JvmField
    var isWeaknessModifierMultiplier: Boolean = false

    private var was1_8Crit = false
    private var wasSprinting = false

    // Here we reverse-engineer all the various damages caused by removing them one at a time, backwards from what NMS code does.
    // This is so the modules can listen to this event and make their modifications, then EntityDamageByEntityListener sets the new values back.
    // Performs the opposite of the following:
    // (Base + Potion effects, scaled by attack delay) + Critical Hit + (Enchantments, scaled by attack delay), Overdamage, Armour
    init {
        // We ignore attacks like arrows etc. because we do not need to change the attack side of those
        // Other modules such as old armour strength work independently of this event
        if (damager !is LivingEntity) {
            isCancelled = true
        }

        // The raw damage passed to this event is EDBE's BASE damage, which does not include armour effects or resistance etc (defence)
        this.rawDamage = rawDamage

        /*
    Invulnerability will cause less damage if they attack with a stronger weapon while vulnerable.
    We must detect this and account for it, instead of setting the usual base weapon damage.
    We artificially set the last damage to 0 between events so that all hits will register,
    however we only do this for DamageByEntity, so there could still be environmental damage (e.g. cactus).
    */
        if (damagee is LivingEntity) {
            val livingDamagee = damagee
            if (livingDamagee.noDamageTicks.toFloat() > livingDamagee.maximumNoDamageTicks.toFloat() / 2.0f) {
                // NMS code also checks if current damage is higher that previous damage. However, here the event
                // already has the difference between the two as the raw damage, and the event does not fire at all
                // if this precondition is not met.

                // Adjust for last damage being environmental sources (e.g. cactus, fall damage)

                val lastDamage = livingDamagee.lastDamage
                this.rawDamage = rawDamage + lastDamage

                Messenger.debug(
                    livingDamagee,
                    "Overdamaged!: " + livingDamagee.noDamageTicks + "/" + livingDamagee.maximumNoDamageTicks + " last: " + livingDamagee.lastDamage
                )
            } else {
                Messenger.debug(
                    livingDamagee,
                    "Invulnerability: " + livingDamagee.noDamageTicks + "/" + livingDamagee.maximumNoDamageTicks + " last: " + livingDamagee.lastDamage
                )
            }
        }

        val livingDamager = damager as LivingEntity

        weapon = livingDamager.equipment!!.itemInMainHand
        // Yay paper. Why do you need to return null here?
        if (weapon == null) weapon = ItemStack(Material.AIR)

        // Technically the weapon could be in the offhand, i.e. a bow.
        // However, we are only concerned with melee weapons here, which will always be in the main hand.
        val damageeType = damagee.type

        Messenger.debug(livingDamager, "Raw attack damage: $rawDamage")
        Messenger.debug(livingDamager, "Without overdamage: " + this.rawDamage)


        mobEnchantmentsDamage = getEntityEnchantmentsDamage(damageeType, weapon!!)
        sharpnessLevel = weapon!!.getEnchantmentLevel(EnchantmentCompat.SHARPNESS.get())
        sharpnessDamage = getNewSharpnessDamage(sharpnessLevel)

        // Scale enchantment damage by attack cooldown
        if (damager is HumanEntity) {
            val cooldown = DamageUtils.getAttackCooldown.apply(damager, 0.5f)
            mobEnchantmentsDamage *= cooldown.toDouble()
            sharpnessDamage *= cooldown.toDouble()
        }

        Messenger.debug(
            livingDamager, "Mob: $mobEnchantmentsDamage Sharpness: $sharpnessDamage"
        )

        // Amount of damage including potion effects and critical hits
        var tempDamage = this.rawDamage - mobEnchantmentsDamage - sharpnessDamage

        Messenger.debug(livingDamager, "No ench damage: $tempDamage")

        // Check if it's a critical hit
        if (livingDamager is Player && isCriticalHit1_8(livingDamager as HumanEntity)) {
            was1_8Crit = true
            Messenger.debug(livingDamager, "1.8 Critical hit detected")
            // In 1.9 a crit also requires the player not to be sprinting
            if (isCriticalHit1_9(livingDamager)) {
                Messenger.debug(livingDamager, "1.9 Critical hit detected")
                Messenger.debug("1.9 Critical hit detected")
                criticalMultiplier = 1.5
                tempDamage /= 1.5
            }
        }

        // Un-scale the damage by the attack strength
        if (damager is HumanEntity) {
            val cooldown = DamageUtils.getAttackCooldown.apply(damager, 0.5f)
            tempDamage /= (0.2f + cooldown * cooldown * 0.8f).toDouble()
        }

        // amplifier 0 = Strength I    amplifier 1 = Strength II
        strengthLevel = (PotionEffects.get(livingDamager, PotionEffectTypeCompat.STRENGTH.get())?.amplifier ?: -1) + 1

        strengthModifier = (strengthLevel * 3).toDouble()

        Messenger.debug(
            livingDamager, "Strength Modifier: $strengthModifier"
        )

        // Don't set has weakness if amplifier is > 0 or < -1, which is outside normal range and probably set by plugin
        // We use an amplifier of -1 (Level 0) to have no effect so weaker attacks will register
        val weaknessAmplifier = PotionEffects.get(livingDamager, PotionEffectType.WEAKNESS)?.amplifier
        hasWeakness = weaknessAmplifier != null && (weaknessAmplifier == -1 || weaknessAmplifier == 0)
        weaknessLevel = (weaknessAmplifier ?: -1) + 1

        weaknessModifier = (weaknessLevel * -4).toDouble()

        Messenger.debug(
            livingDamager, "Weakness Modifier: $weaknessModifier"
        )

        baseDamage = tempDamage + weaknessModifier - strengthModifier
        Messenger.debug(livingDamager, "Base tool damage: $baseDamage")
    }

    /**
     * Whether the attacker had the weakness potion effect,
     * and the level of the effect was either 0 (used by OCM) or 1 (normal value).
     * Values outside this range are to be ignored, as they are probably from other plugins.
     */
    fun hasWeakness(): Boolean {
        return hasWeakness
    }

    override fun isCancelled(): Boolean {
        return cancelled
    }

    override fun setCancelled(cancelled: Boolean) {
        this.cancelled = cancelled
    }

    fun wasSprinting(): Boolean {
        return wasSprinting
    }

    fun setWasSprinting(wasSprinting: Boolean) {
        this.wasSprinting = wasSprinting
    }

    fun was1_8Crit(): Boolean {
        return was1_8Crit
    }

    fun setWas1_8Crit(was1_8Crit: Boolean) {
        this.was1_8Crit = was1_8Crit
    }

    companion object {
        private val handlerList: HandlerList = HandlerList()

        @JvmStatic // Make sure Java reflection can access this
        fun getHandlerList(): HandlerList {
            return handlerList
        }
    }
}
