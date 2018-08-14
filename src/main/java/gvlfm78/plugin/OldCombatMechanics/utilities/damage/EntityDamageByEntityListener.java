package gvlfm78.plugin.OldCombatMechanics.utilities.damage;

import gvlfm78.plugin.OldCombatMechanics.OCMMain;
import gvlfm78.plugin.OldCombatMechanics.module.Module;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class EntityDamageByEntityListener extends Module {

    private static EntityDamageByEntityListener INSTANCE;
    private boolean enabled;

    public EntityDamageByEntityListener(OCMMain plugin){
        super(plugin, "entity-damage-listener");
        INSTANCE = this;
    }

    public static EntityDamageByEntityListener getINSTANCE(){
        return INSTANCE;
    }

    @Override
    public boolean isEnabled(){
        return enabled;
    }

    public void setEnabled(boolean enabled){
        this.enabled = enabled;
    }

    @EventHandler (priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event){
        Entity damager = event.getDamager();
        OCMEntityDamageByEntityEvent e = new OCMEntityDamageByEntityEvent
                (damager, event.getEntity(), event.getCause(), event.getDamage());

        plugin.getServer().getPluginManager().callEvent(e);

        //Re-calculate modified damage and set it back to original event
        // Damage order: base + potion effects + critical hit + enchantments + armour effects
        double newDamage = e.getBaseDamage();

        debug("Base: " + e.getBaseDamage(), damager);

        //Weakness potion
        double weaknessModifier = e.getWeaknessModifier();
        if(e.isWeaknessModifierMultiplier()) newDamage *= weaknessModifier;
        else newDamage += weaknessModifier;

        debug("Weak: " + e.getWeaknessModifier(), damager);

        //Strength potion
        double strengthModifier = e.getStrengthModifier() * e.getStrengthLevel();
        if(e.isStrengthModifierMultiplier()) newDamage *= strengthModifier;
        else newDamage += strengthModifier;

        debug("Strength: " + strengthModifier, damager);

        //Critical hit
        newDamage *= e.getCriticalMultiplier();

        debug("Crit: " + e.getCriticalMultiplier(), damager);

        //Enchantments
        newDamage += e.getMobEnchantmentsDamage() + e.getSharpnessDamage();

        debug("Mob " + e.getMobEnchantmentsDamage() + " Sharp: " + e.getSharpnessDamage(), damager);

        if(newDamage < 0){
            debug("Damage was " + newDamage + " setting to 0", damager);
            newDamage = 0;
        }

        debug("New Damage: " + newDamage, damager);

        event.setDamage(newDamage);
    }
}
