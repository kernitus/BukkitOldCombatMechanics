package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
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

    private static double EPSILON;
    public ModuleDisableProjectileRandomness(OCMMain plugin){
        super(plugin, "disable-projectile-randomness");
        reload();
    }

    @Override
    public void reload(){
        EPSILON = module().getDouble("epsilon");
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
            Vector projectileDirection = projectile.getVelocity();

            // Keep original speed
            double originalMagnitude = projectileDirection.length();
            projectileDirection.normalize();

            // The following works because using rotate modifies the vector, so we must double it to undo the rotation
            // The vector is rotated around the Y axis and matched by checking only the X and Z values
            // Angles is specified in radians, where 10° = 0.17 radians
            if(!fuzzyVectorEquals(projectileDirection, playerDirection)) { // If the projectile is not going straight
                if (fuzzyVectorEquals(projectileDirection, playerDirection.rotateAroundY(0.17))) {
                    debug("10° Offset", player);
                }
                else if (fuzzyVectorEquals(projectileDirection, playerDirection.rotateAroundY(-0.35)))
                    //arrowVelocity.rotateAroundY(-10);
                    debug("-10° Offset", player);
            }

            playerDirection.multiply(originalMagnitude);
            projectile.setVelocity(playerDirection);
        }
    }

    private boolean fuzzyVectorEquals(Vector a, Vector b){
        return Math.abs(a.getX() - b.getX()) < EPSILON &&
                Math.abs(a.getZ() - b.getZ()) < EPSILON;
    }
}