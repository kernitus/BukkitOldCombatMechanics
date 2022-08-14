/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities.damage;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.module.Module;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

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

    @EventHandler(priority = EventPriority.HIGHEST)
    public void overdamage(EntityDamageByEntityEvent e) {
        final Entity damagee = e.getEntity();
        if (damagee instanceof LivingEntity) {
            final UUID uuid = damagee.getUniqueId();
            final LivingEntity livingEntity = ((LivingEntity) damagee);
            final double damage = e.getDamage();

            if (lastDamages.containsKey(uuid)) {
                final double lastDamage = lastDamages.get(uuid);
                if ((float) livingEntity.getNoDamageTicks() > (float) livingEntity.getMaximumNoDamageTicks() / 2.0F) {
                    if (damage > lastDamage) {
                        // was an overdamage, so the actual damage should be damage - lastDamage
                        e.setDamage(damage - lastDamage);
                        // Set last damage to actual value so other plugins are able to use it
                        // This will be set back to 0 in the MONITOR listener so we can detect other overdamages
                        livingEntity.setLastDamage(lastDamage);
                    } else {
                        // was not a real overdamage, cancel the event
                        e.setCancelled(true);
                    }
                } else {
                    // Set last damage to actual value so other plugins are able to use it
                    // This will be set back to 0 in the MONITOR listener so we can detect other overdamages
                    livingEntity.setLastDamage(lastDamage);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        final Entity damager = event.getDamager();
        final Entity damagee = event.getEntity();
        final OCMEntityDamageByEntityEvent e = new OCMEntityDamageByEntityEvent
                (damager, damagee, event.getCause(), event.getDamage());

        // Call event for the other modules to make their modifications
        plugin.getServer().getPluginManager().callEvent(e);

        if (e.isCancelled()) return;

        // Now we re-calculate damage modified by the modules and set it back to original event
        // Damage order: base -> potion effects -> critical hit -> overdamage -> enchantments -> armour effects
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

        debug("Strength: " + strengthModifier, damager);

        // Critical hit: 1.9 is *1.5, 1.8 is *rand(0%,50%) + 1
        // Bukkit 1.8_r3 code:     i += this.random.nextInt(i / 2 + 2);
        if (e.was1_8Crit() && !e.wasSprinting()) {
            newDamage *= e.getCriticalMultiplier();
            if (e.RoundCritDamage()) newDamage = (int) newDamage;
            newDamage += e.getCriticalAddend();
            debug("Crit * " + e.getCriticalMultiplier() + " + " + e.getCriticalAddend(), damager);
        }

        final double lastDamage = newDamage;

        // Overdamage due to immunity
        if (e.wasInvulnerabilityOverdamage() && damagee instanceof LivingEntity) {
            // We must subtract previous damage from new weapon damage for this attack
            final LivingEntity livingDamagee = (LivingEntity) damagee;
            newDamage -= livingDamagee.getLastDamage();
        }

        //Enchantments
        newDamage += e.getMobEnchantmentsDamage() + e.getSharpnessDamage();

        debug("Mob " + e.getMobEnchantmentsDamage() + " Sharp: " + e.getSharpnessDamage(), damager);

        if (newDamage < 0) {
            debug("Damage was " + newDamage + " setting to 0", damager);
            newDamage = 0;
        }

        debug("New Damage: " + newDamage, damager);

        event.setDamage(newDamage);

        // According to NMS, the last damage should actually be base tool + strength + crit, before overdamage
        /*
        if ((float) noDamageTicks > maxNoDamageTicks / 2.0F && f > lastDamage) {
                   this.actuallyHurt(damagesource, f - lastDamage);
                   this.lastDamage = f;
               } else {
                   this.lastDamage = f;
                   this.noDamageTicks = maxNoDamageTicks;
                   this.actuallyHurt(damagesource, f);
               }
         */
        if (!e.wasInvulnerabilityOverdamage())
            lastDamages.put(damagee.getUniqueId(), lastDamage);
    }

    /**
     * Set entity's last damage to 0 a tick after the event so all overdamage attacks get through.
     * This is set automatically after the event to the original damage for some reason.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
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
