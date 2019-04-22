package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDamageEvent.DamageModifier;

/**
 * Allows customizing the shield damage reduction percentages.
 */
public class ModuleShieldDamageReduction extends Module {

    private String genericDamageReduction, projectileDamageReduction;

    public ModuleShieldDamageReduction(OCMMain plugin){
        super(plugin, "shield-damage-reduction");

    }

    @Override
    public void reload(){
        genericDamageReduction = module()
                .getString("genericDamageReduction", "50%")
                .replaceAll(" ", "");

        projectileDamageReduction = module()
                .getString("projectileDamageReduction", "50%")
                .replaceAll(" ", "");
    }


    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHit(EntityDamageByEntityEvent e){
        Entity entity = e.getEntity();

        if(!(entity instanceof Player)) return;

        Player player = (Player) entity;

        if(!shieldBlockedDamage(e)) return;

        // Instead of reducing damage to 33% apply config reduction

        double reducedDamage = getReducedDamage(e.getDamage(), e.getCause());

        //Also make sure reducing the damage doesn't result in negative damage
        e.setDamage(DamageModifier.BLOCKING, 0);

        if(e.getFinalDamage() >= reducedDamage)
            e.setDamage(DamageModifier.BLOCKING, -reducedDamage);

        debug("Damage reduced by: " + e.getDamage(DamageModifier.BLOCKING), player);
    }

    private double getReducedDamage(double fullDamage, DamageCause damageCause){
        String damageReductionString = damageCause == DamageCause.PROJECTILE
                ? projectileDamageReduction
                : genericDamageReduction;

        if(damageReductionString.matches("\\d{1,3}%")){
            // Reduce damage by percentage
            int percentage = Integer.parseInt(damageReductionString.replace("%", ""));
            fullDamage = (fullDamage - 1) * percentage / 100;
        } else if(damageReductionString.matches("\\d+")){
            // Reduce by specified amount of half-hearts
            fullDamage = Integer.parseInt(damageReductionString);
        } else {
            fullDamage = 0;
        }

        if(fullDamage < 0)
            fullDamage = 0;
        return fullDamage;
    }

    private boolean shieldBlockedDamage(EntityDamageByEntityEvent e){
        // Only reduce damage if they were hit head on, i.e. the shield blocked some of the damage
        return e.getDamage(DamageModifier.BLOCKING) < 0;
    }
}
