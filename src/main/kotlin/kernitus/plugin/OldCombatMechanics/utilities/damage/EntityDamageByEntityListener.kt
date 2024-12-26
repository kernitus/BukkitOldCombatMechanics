/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities.damage

import kernitus.plugin.OldCombatMechanics.OCMMain
import kernitus.plugin.OldCombatMechanics.module.OCMModule
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.entity.HumanEntity
import org.bukkit.entity.LivingEntity
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import java.util.*

class EntityDamageByEntityListener(plugin: OCMMain) : OCMModule(plugin, "entity-damage-listener") {
    var enabled: Boolean = false

    companion object {
        lateinit var INSTANCE: EntityDamageByEntityListener
    }

    private val lastDamages: MutableMap<UUID, Double>

    init {
        INSTANCE = this
        lastDamages = WeakHashMap()
    }

    override fun isEnabled() = enabled
    override fun isEnabled(world: World) = enabled

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onEntityDamage(event: EntityDamageEvent) {
        val damagee = event.entity

        if (event !is EntityDamageByEntityEvent) {
            // Damage immunity only applies to living entities
            if (damagee !is LivingEntity) return

            restoreLastDamage(damagee)

            var newDamage = event.damage // base damage, before defence calculations

            // Overdamage due to immunity
            // Invulnerability will cause less damage if they attack with a stronger weapon while vulnerable
            // That is, the difference in damage will be dealt, but only if new attack is stronger than previous one
            checkOverdamage(damagee, event, newDamage)

            if (newDamage < 0) {
                debug("Damage was $newDamage setting to 0")
                newDamage = 0.0
            }

            // Set damage, this should scale effects in the 1.9 way in case some of our modules are disabled
            event.damage = newDamage
            debug("Attack damage (before defence): $newDamage")
        } else {
            val damager = event.damager

            // Call event constructor before setting lastDamage back, because we need it for calculations
            val e = OCMEntityDamageByEntityEvent(damager, damagee, event.getCause(), event.getDamage())

            // Set last damage to actual value for other modules and plugins to use
            // This will be set back to 0 in MONITOR listener on the next tick to detect all potential overdamages.
            // If there is large delay between last time an entity was damaged and the next damage,
            // the last damage might have been removed from the weak hash map. This is intended, as the immunity
            // ticks tends to be a short period of time anyway and last damage is irrelevant after immunity has expired.
            if (damagee is LivingEntity) restoreLastDamage(damagee)

            // Call event for the other modules to make their modifications
            plugin.server.pluginManager.callEvent(e)

            if (e.isCancelled) return

            // Now we re-calculate damage modified by the modules and set it back to original event
            // Attack components order: (Base + Potion effects, scaled by attack delay) + Critical Hit + (Enchantments, scaled by attack delay)
            // Hurt components order: Overdamage - Armour - Resistance - Armour enchants - Absorption
            var newDamage = e.baseDamage

            debug("Base: " + e.baseDamage, damager)
            debug("Base: " + e.baseDamage)

            // Weakness potion
            val weaknessModifier = e.weaknessModifier * e.weaknessLevel
            val weaknessAddend = if (e.isWeaknessModifierMultiplier) newDamage * weaknessModifier else weaknessModifier
            // Don't modify newDamage yet so both potion effects are calculated off of the base damage
            debug("Weak: $weaknessAddend")
            debug("Weak: $weaknessAddend", damager)

            // Strength potion
            debug("Strength level: " + e.strengthLevel)
            debug("Strength level: " + e.strengthLevel, damager)
            var strengthModifier = e.strengthModifier * e.strengthLevel
            if (!e.isStrengthModifierMultiplier) newDamage += strengthModifier
            else if (e.isStrengthModifierAddend) newDamage *= ++strengthModifier
            else newDamage *= strengthModifier

            debug("Strength: $strengthModifier")
            debug("Strength: $strengthModifier", damager)

            newDamage += weaknessAddend

            // Scale by attack delay
            // float currentItemAttackStrengthDelay = 1.0D / GenericAttributes.ATTACK_SPEED * 20.0D
            // attack strength ticker goes up by 1 every tick, is reset to 0 after an attack
            // float f2 = MathHelper.clamp((attackStrengthTicker + 0.5) / currentItemAttackStrengthDelay, 0.0F, 1.0F);
            // f *= 0.2F + f2 * f2 * 0.8F;
            // the multiplier is equivalent to y = 0.8x^2 + 0.2
            // because x (f2) is always between 0 and 1, the multiplier will always be between 0.2 and 1
            // this implies 40 speed is the minimum to always have full attack strength
            if (damager is HumanEntity) {
                val cooldown = DamageUtils.getAttackCooldown(damager, 0.5f) // i.e. f2
                debug("Scale by attack delay: $newDamage *= 0.2 + $cooldown^2 * 0.8")
                newDamage *= (0.2f + cooldown * cooldown * 0.8f).toDouble()
            }

            // Critical hit
            val criticalMultiplier = e.criticalMultiplier
            debug("Crit $newDamage *= $criticalMultiplier")
            newDamage *= criticalMultiplier

            // Enchantment damage, scaled by attack cooldown
            var enchantmentDamage = e.mobEnchantmentsDamage + e.sharpnessDamage
            if (damager is HumanEntity) {
                val cooldown = DamageUtils.getAttackCooldown(damager, 0.5f)
                debug("Scale enchantments by attack delay: $enchantmentDamage *= $cooldown")
                enchantmentDamage *= cooldown.toDouble()
            }
            newDamage += enchantmentDamage
            debug(
                "Mob " + e.mobEnchantmentsDamage + " Sharp: " + e.sharpnessDamage + " Scaled: " + enchantmentDamage,
                damager
            )

            if (damagee is LivingEntity) {
                // Overdamage due to immunity
                // Invulnerability will cause less damage if they attack with a stronger weapon while vulnerable
                // That is, the difference in damage will be dealt, but only if new attack is stronger than previous one
                // Value before overdamage will become new "last damage"
                newDamage = checkOverdamage(damagee, event, newDamage)
            }

            if (newDamage < 0) {
                debug("Damage was $newDamage setting to 0", damager)
                newDamage = 0.0
            }

            // Set damage, this should scale effects in the 1.9 way in case some of our modules are disabled
            event.setDamage(newDamage)
            debug("New Damage: $newDamage", damager)
            debug("Attack damage (before defence): $newDamage")
        }
    }

    /**
     * Set entity's last damage to 0 a tick after the event so all overdamage attacks get through.
     * The last damage is overridden by NMS code regardless of what the actual damage is set to via Spigot.
     * Finally, the LOWEST priority listener above will set the last damage back to the correct value
     * for other plugins to use the next time the entity is damaged.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun afterEntityDamage(event: EntityDamageEvent) {
        val damagee = event.entity

        if (event is EntityDamageByEntityEvent) {
            if (lastDamages.containsKey(damagee.uniqueId)) {
                // Set last damage to 0, so we can detect attacks even by weapons with a weaker attack value than what OCM would calculate
                Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                    (damagee as LivingEntity).lastDamage = 0.0
                    debug("Set last damage to 0", damagee)
                    debug("Set last damage to 0")
                }, 1L)
            }
        } else {
            // if not EDBYE then we leave last damage as is
            lastDamages.remove(damagee.uniqueId)
            debug("Non-entity damage, using default last damage", damagee)
            debug("Non-entity damage, using default last damage")
        }
    }

    /**
     * Restored the correct last damage for the given entity
     *
     * @param damagee The living entity to try to restore the last damage for
     */
    private fun restoreLastDamage(damagee: LivingEntity) {
        lastDamages[damagee.uniqueId]?.let { lastStoredDamage ->
            damagee.lastDamage = lastStoredDamage
            debug("Set last damage back to $lastStoredDamage", damagee)
            debug("Set last damage back to $lastStoredDamage")
        }
    }

    private fun checkOverdamage(livingDamagee: LivingEntity, event: EntityDamageEvent, newDamage: Double): Double {
        var newDamage = newDamage
        val newLastDamage = newDamage

        if (livingDamagee.noDamageTicks.toFloat() > livingDamagee.maximumNoDamageTicks.toFloat() / 2.0f) {
            // Last damage was either set to correct value above in this listener, or we're using the server's value
            // If other plugins later modify BASE damage, they should either be taking last damage into account,
            // or ignoring the event if it is cancelled
            val lastDamage = livingDamagee.lastDamage
            if (newDamage <= lastDamage) {
                event.damage = 0.0
                event.isCancelled = true
                debug("Was fake overdamage, cancelling $newDamage <= $lastDamage")
                return 0.0
            }

            debug("Overdamage: $newDamage - $lastDamage")
            // We must subtract previous damage from new weapon damage for this attack
            newDamage -= livingDamagee.lastDamage

            debug(
                ("Last damage " + lastDamage + " new damage: " + newLastDamage + " applied: " + newDamage
                        + " ticks: " + livingDamagee.noDamageTicks + " /" + livingDamagee.maximumNoDamageTicks)
            )
        }
        // Update the last damage done, including when it was overdamage.
        // This means attacks must keep increasing in value during immunity period to keep dealing overdamage.
        lastDamages[livingDamagee.uniqueId] = newLastDamage

        return newDamage
    }
}
