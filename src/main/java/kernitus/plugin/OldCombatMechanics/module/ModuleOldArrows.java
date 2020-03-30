package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

/**
 * Prevents players using new arrow mechanics
 */
public class ModuleOldArrows extends Module {


    public ModuleOldArrows(OCMMain plugin){
        super(plugin, "old-arrows");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onProjectileHit(EntityDamageByEntityEvent e){
        if(!(e.getEntity() instanceof Player)){
            return;
        }

        Player player = (Player) e.getEntity();

        if(!isEnabled(player.getWorld())){
            return;
        }

        if(!(e.getDamager() instanceof Arrow)){
            return;
        }

        Arrow arrow = (Arrow) e.getDamager();

        if (arrow.hasCustomEffects() && module().getBoolean("clear-effects")){
            arrow.clearCustomEffects();
        }
        e.setDamage(arrow.getDamage() * module().getDouble("damage-multiplier"));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onProjectileLaunch(ProjectileLaunchEvent e){
        if(!(e.getEntity() instanceof Arrow)){
            return;
        }

        Arrow arrow = (Arrow) e.getEntity();

        Vector velocity = arrow.getVelocity();
        double horizontalModifier = module().getDouble("velocity-multiplier.horizontal");
        double verticalModifier = module().getDouble("velocity-multiplier.vertical");
        Vector velocityModified = new Vector(
                horizontalModifier * velocity.getX(),
                verticalModifier * velocity.getY(),
                horizontalModifier * velocity.getZ()
        );

        arrow.setVelocity(velocityModified);
    }

}