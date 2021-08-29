package kernitus.plugin.OldCombatMechanics.utilities.damage;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.module.Module;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.scheduler.BukkitRunnable;

public class EntityDamageByEntityListener extends Module {

    private static EntityDamageByEntityListener INSTANCE;
    private boolean enabled;

    public EntityDamageByEntityListener(OCMMain plugin) {
        super(plugin, "entity-damage-listener");
        INSTANCE = this;
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
        Entity damager = event.getDamager();
        OCMEntityDamageByEntityEvent e = new OCMEntityDamageByEntityEvent
                (damager, event.getEntity(), event.getCause(), event.getDamage());

        // Call event for the other modules to make their modifications
        plugin.getServer().getPluginManager().callEvent(e);

        if (e.isCancelled()) return;

        // Now we re-calculate damage modified by the modules and set it back to original event
        // Damage order: base -> potion effects -> critical hit -> enchantments -> armour effects
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

        //Enchantments
        newDamage += e.getMobEnchantmentsDamage() + e.getSharpnessDamage();

        debug("Mob " + e.getMobEnchantmentsDamage() + " Sharp: " + e.getSharpnessDamage(), damager);

        if (newDamage < 0) {
            debug("Damage was " + newDamage + " setting to 0", damager);
            newDamage = 0;
        }

        debug("New Damage: " + newDamage, damager);

        event.setDamage(newDamage);
    }

    /**
     * Set entity's last damage 1 tick after event. For some reason this is not updated to the final damage properly.
     * (Maybe a Spigot bug?) Hopefully other plugins vibe with this. Otherwise can store this just for OCM.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void afterEntityDamage(EntityDamageByEntityEvent e) {
        final Entity damagee = e.getEntity();
        if (damagee instanceof LivingEntity) {
            final double damage = e.getDamage();

            new BukkitRunnable() {
                @Override
                public void run() {
                    ((LivingEntity) damagee).setLastDamage(damage);
                    debug("Set last damage to " + damage, damagee);
                }
            }.runTaskLater(plugin, 1);

        }
    }
}
