package gvlfm78.plugin.OldCombatMechanics.module;

import gvlfm78.plugin.OldCombatMechanics.OCMMain;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

/**
 * Prevents the noise introduced when shooting with a bow to make arrows go straight.
 */
public class ModuleDisableProjectileRandomness extends Module {

    public ModuleDisableProjectileRandomness(OCMMain plugin){
        super(plugin, "disable-projectile-randomness");
    }

    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent e){
        Projectile projectile = e.getEntity();
        ProjectileSource shooter = projectile.getShooter();

        if(shooter instanceof Player){
            Player player = (Player) shooter;
            if(!isEnabled(player.getWorld())) return;
            debug("Making projectile go straight", player);

            Vector playerDirection = player.getLocation().getDirection().normalize();
            // keep original speed
            Vector arrowVelocity = playerDirection.multiply(projectile.getVelocity().length());

            projectile.setVelocity(arrowVelocity);
        }
    }
}