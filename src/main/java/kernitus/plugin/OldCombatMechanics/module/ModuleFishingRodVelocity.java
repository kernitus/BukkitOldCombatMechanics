package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

import java.util.Random;

public class ModuleFishingRodVelocity extends Module {
    Random rand = new Random();

    /**
     * 1.9 changed the formula used for fishing rod velocity to be a bit slower.
     * This module uses the exact 1.8 formula in order to revert this change.
     * Some error in velocity occurs due to other changes in 1.9
     */

    public ModuleFishingRodVelocity(OCMMain plugin) {
        super(plugin, "old-fishing-rod-velocity");
    }

    @EventHandler
    public void onFishingRodCast(ProjectileLaunchEvent event) {
        if (event.getEntity() instanceof FishHook) {
            Projectile fishHook = event.getEntity();
            ProjectileSource caster = fishHook.getShooter();

            if (caster instanceof Player) {
                Player casterPlayer = (Player) caster;
                double playerYaw = casterPlayer.getLocation().getYaw();
                double playerPitch = casterPlayer.getLocation().getPitch();

                float oldMaxVelocity = 0.4F;
                double velocityX = -Math.sin(playerYaw / 180.0F * (float)Math.PI) * Math.cos(playerPitch / 180.0F * (float)Math.PI) * oldMaxVelocity;
                double velocityZ = Math.cos(playerYaw / 180.0F * (float)Math.PI) * Math.cos(playerPitch / 180.0F * (float)Math.PI) * oldMaxVelocity;
                double velocityY = -Math.sin(playerPitch / 180.0F * (float)Math.PI) * oldMaxVelocity;

                double oldVelocityMultiplier = 1.37F; // This is 1.5 in vanilla 1.8, the formula doesn't work exactly as it should

                float vectorLength = (float) Math.sqrt(velocityX * velocityX + velocityY * velocityY + velocityZ * velocityZ);
                velocityX = velocityX / (double)vectorLength;
                velocityY = velocityY / (double)vectorLength;
                velocityZ = velocityZ / (double)vectorLength;
                velocityX = velocityX + this.rand.nextGaussian() * 0.007499999832361937D;
                velocityY = velocityY + this.rand.nextGaussian() * 0.007499999832361937D;
                velocityZ = velocityZ + this.rand.nextGaussian() * 0.007499999832361937D;
                velocityX = velocityX * oldVelocityMultiplier;
                velocityY = velocityY * oldVelocityMultiplier;
                velocityZ = velocityZ * oldVelocityMultiplier;

                Vector newSpeed = new Vector(velocityX, velocityY, velocityZ);

                fishHook.setVelocity(newSpeed);
            }
        }
    }
}
