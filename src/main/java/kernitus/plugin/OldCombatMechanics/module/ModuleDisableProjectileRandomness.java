/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.SpigotFunctionChooser;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

/**
 * Prevents the noise introduced when shooting with a bow to make arrows go straight.
 */
public class ModuleDisableProjectileRandomness extends OCMModule {

    private static double EPSILON;
    // Method was added in 1.14.0
    private static final SpigotFunctionChooser<Vector, Double, Vector> rotateAroundY = SpigotFunctionChooser.apiCompatCall(
            (vector, angle) -> vector.rotateAroundY(angle),
            (vector, angle) -> {
                double angleCos = Math.cos(angle);
                double angleSin = Math.sin(angle);

                double x = angleCos * vector.getX() + angleSin * vector.getZ();
                double z = -angleSin * vector.getX() + angleCos * vector.getZ();
                return vector.setX(x).setZ(z);
            }
    );

    public ModuleDisableProjectileRandomness(OCMMain plugin) {
        super(plugin, "disable-projectile-randomness");
        reload();
    }

    @Override
    public void reload() {
        EPSILON = module().getDouble("epsilon");
    }

    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent e) {
        final Projectile projectile = e.getEntity();
        final ProjectileSource shooter = projectile.getShooter();

        if (shooter instanceof Player) {
            final Player player = (Player) shooter;
            if (!isEnabled(player.getWorld())) return;
            debug("Making projectile go straight", player);

            final Vector playerDirection = player.getLocation().getDirection().normalize();
            final Vector projectileDirection = projectile.getVelocity();

            // Keep original speed
            final double originalMagnitude = projectileDirection.length();
            projectileDirection.normalize();

            // The following works because using rotate modifies the vector, so we must double it to undo the rotation
            // The vector is rotated around the Y axis and matched by checking only the X and Z values
            // Angles is specified in radians, where 10° = 0.17 radians
            if (!fuzzyVectorEquals(projectileDirection, playerDirection)) { // If the projectile is not going straight
                if (fuzzyVectorEquals(projectileDirection, rotateAroundY.apply(playerDirection, 0.17))) {
                    debug("10° Offset", player);
                } else if (fuzzyVectorEquals(projectileDirection, rotateAroundY.apply(playerDirection, -0.35)))
                    // arrowVelocity.rotateAroundY(-10);
                    debug("-10° Offset", player);
            }

            playerDirection.multiply(originalMagnitude);
            projectile.setVelocity(playerDirection);
        }
    }

    private boolean fuzzyVectorEquals(Vector a, Vector b) {
        return Math.abs(a.getX() - b.getX()) < EPSILON &&
                Math.abs(a.getZ() - b.getZ()) < EPSILON;
    }
}