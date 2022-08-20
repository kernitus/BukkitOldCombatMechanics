/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities.damage;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.module.Module;
import kernitus.plugin.OldCombatMechanics.utilities.Messenger;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attributable;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public class EntityDamageByEntityListener extends Module {

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

        // Set last damage to actual value for other modules and plugins to use
        // This will be set back to 0 in MONITOR listener on the next tick to detect all potential overdamages
        final Double lastStoredDamage = lastDamages.get(damagee.getUniqueId());
        if(lastStoredDamage != null && damagee instanceof LivingEntity) {
            final LivingEntity livingDamagee = ((LivingEntity) damagee);
            livingDamagee.setLastDamage(lastStoredDamage);
        }

        final OCMEntityDamageByEntityEvent e = new OCMEntityDamageByEntityEvent
                (damager, damagee, event.getCause(), event.getDamage());

        // Call event for the other modules to make their modifications
        plugin.getServer().getPluginManager().callEvent(e);

        if (e.isCancelled()) return;

        // Now we re-calculate damage modified by the modules and set it back to original event
        // Attack components order: (Base + Potion effects, scaled by attack delay) + Critical Hit + (Enchantments, scaled by attack delay)
        // Hurt components order: Overdamage - Armour Effects
        double newDamage = e.getBaseDamage();

        debug("Base: " + e.getBaseDamage(), damager);

        //Weakness potion
        double weaknessModifier = e.getWeaknessModifier();
        if (e.isWeaknessModifierMultiplier()) newDamage *= weaknessModifier;
        else newDamage += weaknessModifier;

        debug("Weak: " + e.getWeaknessModifier(), damager);

        //Strength potion
        debug("Strength level: " + e.getStrengthLevel(), damager);
        double strengthModifier = e.getStrengthModifier() * e.getStrengthLevel();
        if (!e.isStrengthModifierMultiplier()) newDamage += strengthModifier;
        else if (e.isStrengthModifierAddend()) newDamage *= ++strengthModifier;
        else newDamage *= strengthModifier;

        // todo scale by attack delay
        // todo take into account weapon cooldown
        //  float f2 = this.getAttackStrengthScale(0.5F);
        //  f *= 0.2F + f2 * f2 * 0.8F;

        debug("Strength: " + strengthModifier, damager);

        // Critical hit: 1.9 is *1.5, 1.8 is *rand(0%,50%) + 1
        // Bukkit 1.8_r3 code:     i += this.random.nextInt(i / 2 + 2);
        if (e.was1_8Crit() && !e.wasSprinting()) {
            newDamage *= e.getCriticalMultiplier();
            if (e.RoundCritDamage()) newDamage = (int) newDamage;
            newDamage += e.getCriticalAddend();
            debug("Crit * " + e.getCriticalMultiplier() + " + " + e.getCriticalAddend(), damager);
        }

        //Enchantments
        newDamage += e.getMobEnchantmentsDamage() + e.getSharpnessDamage();
        debug("Mob " + e.getMobEnchantmentsDamage() + " Sharp: " + e.getSharpnessDamage(), damager);

        // Overdamage due to immunity
        // Invulnerability will cause less damage if they attack with a stronger weapon while vulnerable.
        // According to NMS, the last damage should actually be base tool + strength + crit, before overdamage
        final double newLastDamage = newDamage;

        if (damagee instanceof LivingEntity) {
            final LivingEntity livingDamagee = (LivingEntity) damagee;
            if((float) livingDamagee.getNoDamageTicks() > (float) livingDamagee.getMaximumNoDamageTicks() / 2.0F) {

                // This was either set to correct value above in this listener, or we're using the server's value
                final double lastDamage = livingDamagee.getLastDamage();
                if(newDamage <= lastDamage){
                    event.setCancelled(true);
                    debug("Was fake overdamage, cancelling event");
                    return;
                }

                debug("Overdamage: " + newDamage + " - " + lastDamage);
                // We must subtract previous damage from new weapon damage for this attack
                newDamage -= livingDamagee.getLastDamage();

                debug("Last damage " + lastDamage + " attack: " +
                        ((Attributable) damager).getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).getValue()
                        + " armour: " + ((Attributable) damagee).getAttribute(Attribute.GENERIC_ARMOR).getValue()
                        + " ticks: " + livingDamagee.getNoDamageTicks() + " /" + livingDamagee.getMaximumNoDamageTicks()
                );
            } else {
                lastDamages.put(damagee.getUniqueId(), newLastDamage);
            }
        }

        if (newDamage < 0) {
            debug("Damage was " + newDamage + " setting to 0", damager);
            newDamage = 0;
        }

        debug("New Damage: " + newDamage, damager);

        //event.setDamage(newDamage);
        // todo might have to nuke all values and just set BASE
        event.setDamage(EntityDamageEvent.DamageModifier.BASE, newDamage);
        Messenger.debug("SET NEW DAMAGE TO: " + newDamage);
    }

    /**
     * Set entity's last damage to 0 a tick after the event so all overdamage attacks get through.
     * This is set automatically after the event to the original damage for some reason.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void afterEntityDamage(EntityDamageByEntityEvent e) {
        final Entity damagee = e.getEntity();
        if (damagee instanceof LivingEntity) {
            final UUID damageeId = damagee.getUniqueId();
            if (lastDamages.containsKey(damageeId)) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    ((LivingEntity) damagee).setLastDamage(0);
                    debug("Set last damage to 0", damagee);
                }, 1L);
            }
        }
    }

}
