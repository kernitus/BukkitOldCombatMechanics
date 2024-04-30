/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.SpigotFunctionChooser;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Random;

/**
 * This module reverts fishing rod gravity and velocity back to 1.8 behaviour
 * <p>
 * Fishing rod gravity in 1.14+ is 0.03 while in 1.8 it is 0.04
 * Launch velocity in 1.9+ is also different from the 1.8 formula
 */
public class ModuleFishingRodVelocity extends OCMModule {

    private Random random;
    private boolean hasDifferentGravity;
    // In 1.12- getHook() returns a Fish which extends FishHook
    private final SpigotFunctionChooser<PlayerFishEvent, Object, FishHook> getHook = SpigotFunctionChooser.apiCompatReflectionCall(
            (e, params) -> e.getHook(),
            PlayerFishEvent.class, "getHook"
    );

    public ModuleFishingRodVelocity(OCMMain plugin) {
        super(plugin, "fishing-rod-velocity");
        reload();
    }

    @Override
    public void reload() {
        random = new Random();

        // Versions 1.14+ have different gravity than previous versions
        hasDifferentGravity = Reflector.versionIsNewerOrEqualTo(1, 14, 0);
    }

    @EventHandler (ignoreCancelled = true)
    public void onFishEvent(PlayerFishEvent event) {
        final FishHook fishHook = getHook.apply(event);
        final Player player = event.getPlayer();

        if (!isEnabled(player) || event.getState() != PlayerFishEvent.State.FISHING) return;

        final Location location = event.getPlayer().getLocation();
        final double playerYaw = location.getYaw();
        final double playerPitch = location.getPitch();

        final float oldMaxVelocity = 0.4F;
        double velocityX = -Math.sin(playerYaw / 180.0F * (float) Math.PI) * Math.cos(playerPitch / 180.0F * (float) Math.PI) * oldMaxVelocity;
        double velocityZ = Math.cos(playerYaw / 180.0F * (float) Math.PI) * Math.cos(playerPitch / 180.0F * (float) Math.PI) * oldMaxVelocity;
        double velocityY = -Math.sin(playerPitch / 180.0F * (float) Math.PI) * oldMaxVelocity;

        final double oldVelocityMultiplier = 1.5;

        final double vectorLength = (float) Math.sqrt(velocityX * velocityX + velocityY * velocityY + velocityZ * velocityZ);
        velocityX /= vectorLength;
        velocityY /= vectorLength;
        velocityZ /= vectorLength;

        velocityX += random.nextGaussian() * 0.007499999832361937D;
        velocityY += random.nextGaussian() * 0.007499999832361937D;
        velocityZ += random.nextGaussian() * 0.007499999832361937D;

        velocityX *= oldVelocityMultiplier;
        velocityY *= oldVelocityMultiplier;
        velocityZ *= oldVelocityMultiplier;

        fishHook.setVelocity(new Vector(velocityX, velocityY, velocityZ));

        if (!hasDifferentGravity) return;

        // Adjust gravity on every tick unless it's in water
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!fishHook.isValid() || fishHook.isOnGround()) cancel();

                // We check both conditions as sometimes it's underwater but in seagrass, or when bobbing not underwater but the material is water
                if (!fishHook.isInWater() && fishHook.getWorld().getBlockAt(fishHook.getLocation()).getType() != Material.WATER) {
                    final Vector fVelocity = fishHook.getVelocity();
                    fVelocity.setY(fVelocity.getY() - 0.01);
                    fishHook.setVelocity(fVelocity);
                }
            }
        }.runTaskTimer(plugin, 1, 1);
    }
}
