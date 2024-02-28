/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities.damage;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.module.OCMModule;
import kernitus.plugin.OldCombatMechanics.scheduler.SchedulerManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public class EntityDamageByEntityListener extends OCMModule {

    private static EntityDamageByEntityListener INSTANCE;
    private boolean enabled;
    private final Map<UUID, Double> lastDamages;

    public EntityDamageByEntityListener(OCMMain plugin) {
        super(plugin, "entity-damage-listener");
        INSTANCE = this;
        lastDamages = new WeakHashMap<>();
    }

    public static EntityDamageByEntityListener getINSTANCE() {
        return INSTANCE;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        final Entity damager = event.getDamager();
        final Entity damagee = event.getEntity();

        // Call event constructor before setting lastDamage back, because we need it for calculations
        final OCMEntityDamageByEntityEvent e = new OCMEntityDamageByEntityEvent
                (damager, damagee, event.getCause(), event.getDamage());

        // Set last damage to actual value for other modules and plugins to use
        // This will be set back to 0 in MONITOR listener on the next tick to detect all potential overdamages.
        // If there is large delay between last time an entity was damaged and the next damage,
        // the last damage might have been removed from the weak hash map. This is intended, as the immunity
        // ticks tends to be a short period of time anyway and last damage is irrelevant after immunity has expired.
        final Double lastStoredDamage = lastDamages.get(damagee.getUniqueId());
        if (lastStoredDamage != null && damagee instanceof LivingEntity) {
            final LivingEntity livingDamagee = ((LivingEntity) damagee);
            livingDamagee.setLastDamage(lastStoredDamage);
        }

        // Call event for the other modules to make their modifications
        plugin.getServer().getPluginManager().callEvent(e);

        if (e.isCancelled()) return;

        // Now we re-calculate damage modified by the modules and set it back to original event
        // Attack components order: (Base + Potion effects, scaled by attack delay) + Critical Hit + (Enchantments, scaled by attack delay)
        // Hurt components order: Overdamage - Armour - Resistance - Armour enchants - Absorption
        double newDamage = e.getBaseDamage();

        debug("Base: " + e.getBaseDamage(), damager);
        debug("Base: " + e.getBaseDamage());

        // Weakness potion
        final double weaknessModifier = e.getWeaknessModifier() * e.getWeaknessLevel();
        final double weaknessAddend = e.isWeaknessModifierMultiplier() ? newDamage * weaknessModifier : weaknessModifier;
        // Don't modify newDamage yet so both potion effects are calculated off of the base damage
        debug("Weak: " + weaknessAddend);
        debug("Weak: " + weaknessAddend, damager);

        // Strength potion
        debug("Strength level: " + e.getStrengthLevel());
        debug("Strength level: " + e.getStrengthLevel(), damager);
        double strengthModifier = e.getStrengthModifier() * e.getStrengthLevel();
        if (!e.isStrengthModifierMultiplier()) newDamage += strengthModifier;
        else if (e.isStrengthModifierAddend()) newDamage *= ++strengthModifier;
        else newDamage *= strengthModifier;

        debug("Strength: " + strengthModifier);
        debug("Strength: " + strengthModifier, damager);

        newDamage += weaknessAddend;

        // Scale by attack delay
        // float currentItemAttackStrengthDelay = 1.0D / GenericAttributes.ATTACK_SPEED * 20.0D
        // attack strength ticker goes up by 1 every tick, is reset to 0 after an attack
        // float f2 = MathHelper.clamp((attackStrengthTicker + 0.5) / currentItemAttackStrengthDelay, 0.0F, 1.0F);
        // f *= 0.2F + f2 * f2 * 0.8F;
        // the multiplier is equivalent to y = 0.8x^2 + 0.2
        // because x (f2) is always between 0 and 1, the multiplier will always be between 0.2 and 1
        // this implies 40 speed is the minimum to always have full attack strength
        if (damager instanceof HumanEntity) {
            final float cooldown = DamageUtils.getAttackCooldown.apply((HumanEntity) damager, 0.5F); // i.e. f2
            debug("Scale by attack delay: " + newDamage + " *= 0.2 + " + cooldown + "^2 * 0.8");
            newDamage *= 0.2F + cooldown * cooldown * 0.8F;
        }

        // Critical hit
        final double criticalMultiplier = e.getCriticalMultiplier();
        debug("Crit " + newDamage + " *= " + criticalMultiplier);
        newDamage *= criticalMultiplier;

        // Enchantment damage, scaled by attack cooldown
        double enchantmentDamage = e.getMobEnchantmentsDamage() + e.getSharpnessDamage();
        if (damager instanceof HumanEntity) {
            final float cooldown = DamageUtils.getAttackCooldown.apply((HumanEntity) damager, 0.5F);
            debug("Scale enchantments by attack delay: " + enchantmentDamage + " *= " + cooldown);
            enchantmentDamage *= cooldown;
        }
        newDamage += enchantmentDamage;
        debug("Mob " + e.getMobEnchantmentsDamage() + " Sharp: " + e.getSharpnessDamage() + " Scaled: " + enchantmentDamage, damager);

        // Value before overdamage will become new "last damage"
        final double newLastDamage = newDamage;

        // Overdamage due to immunity
        // Invulnerability will cause less damage if they attack with a stronger weapon while vulnerable
        // That is, the difference in damage will be dealt, but only if new attack is stronger than previous one
        if (damagee instanceof LivingEntity) {
            final LivingEntity livingDamagee = (LivingEntity) damagee;
            if ((float) livingDamagee.getNoDamageTicks() > (float) livingDamagee.getMaximumNoDamageTicks() / 2.0F) {
                // Last damage was either set to correct value above in this listener, or we're using the server's value
                // If other plugins later modify BASE damage, they should either be taking last damage into account,
                // or ignoring the event if it is cancelled
                final double lastDamage = livingDamagee.getLastDamage();
                if (newDamage <= lastDamage) {
                    event.setDamage(0);
                    event.setCancelled(true);
                    debug("Was fake overdamage, cancelling " + newDamage + " <= " + lastDamage);
                    return;
                }

                debug("Overdamage: " + newDamage + " - " + lastDamage);
                // We must subtract previous damage from new weapon damage for this attack
                newDamage -= livingDamagee.getLastDamage();

                debug("Last damage " + lastDamage + " new damage: " + newLastDamage + " applied: " + newDamage
                        + " ticks: " + livingDamagee.getNoDamageTicks() + " /" + livingDamagee.getMaximumNoDamageTicks()
                );
            }
            // Update the last damage done, including when it was overdamage.
            // This means attacks must keep increasing in value during immunity period to keep dealing overdamage.
            lastDamages.put(damagee.getUniqueId(), newLastDamage);
        }

        if (newDamage < 0) {
            debug("Damage was " + newDamage + " setting to 0", damager);
            newDamage = 0;
        }

        // Set damage, this should scale effects in the 1.9 way in case some of our modules are disabled
        event.setDamage(newDamage);
        debug("New Damage: " + newDamage, damager);
        debug("Attack damage (before defence): " + newDamage);
    }

    /**
     * Set entity's last damage to 0 a tick after the event so all overdamage attacks get through.
     * The last damage is overridden by NMS code regardless of what the actual damage is set to via Spigot.
     * Finally, the LOWEST priority listener above will set the last damage back to the correct value
     * for other plugins to use the next time the entity is damaged.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void afterEntityDamage(EntityDamageEvent event) {
        final Entity damagee = event.getEntity();

        if(event instanceof EntityDamageByEntityEvent) {
            if (lastDamages.containsKey(damagee.getUniqueId())) {
                // Set last damage to 0, so we can detect attacks even by weapons with a weaker attack value than what OCM would calculate
                SchedulerManager.INSTANCE.getScheduler().runTaskLater(plugin, () -> {
                    ((LivingEntity) damagee).setLastDamage(0);
                    debug("Set last damage to 0", damagee);
                }, 1L);
            }
        } else {
            // if not EDBYE then we leave last damage as is
            lastDamages.remove(damagee.getUniqueId());
            debug("Non-entity damage, using default last damage", damagee);
        }
    }

}
