package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.projectiles.ProjectileSource;

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

        if (arrow.hasCustomEffects()){
            arrow.clearCustomEffects();
        }

    }
}