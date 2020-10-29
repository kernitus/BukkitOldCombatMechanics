package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.util.Vector;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;

public class ModuleFishingRodVelocity extends Module {
    public static HashSet<FishHook> fishHooks = new HashSet<>();
    boolean versionChagesGravity;
    Random rand = new Random();
    Method getHook;

    /**
     * This module uses the exact 1.8 formula in order to revert fishing rod gravity
     * and fishing rod velocity back to 1.8
     *
     * Fishing rod gravity in 1.9+ is 0.04 while in 1.8 it is 0.03
     * Launch velocity in 1.9+ was also changed from the 1.8 formula
     */

    public ModuleFishingRodVelocity(OCMMain plugin) {
        super(plugin, "fishing-rod-velocity");

        // 1.14 versions and higher change gravity in testing, while 1.13 versions and lower
        // have the same fishing hook gravity as 1.8
        versionChagesGravity = !Bukkit.getVersion().contains("1.13") && !Bukkit.getVersion().contains("1.12") && !Bukkit.getVersion().contains("1.11") && !Bukkit.getVersion().contains("1.10") && !Bukkit.getVersion().contains("1.9");

        try {
            // Reflection because in 1.12- this method returns the Fish class, which was renamed to FishHook in 1.13+
            getHook = PlayerFishEvent.class.getMethod("getHook");
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }

        OCMMain.getInstance().addEnableListener(() -> Bukkit.getScheduler().runTaskTimer(OCMMain.getInstance(), () -> {
            Iterator<FishHook> fishingHookIterator = ModuleFishingRodVelocity.fishHooks.iterator();

            while (fishingHookIterator.hasNext()) {
                FishHook fishHook = fishingHookIterator.next();

                if (!fishHook.isValid()) {
                    fishingHookIterator.remove();
                } else if (versionChagesGravity && fishHook.getWorld().getBlockAt(fishHook.getLocation()).getType() != Material.WATER) {
                    Vector fVelocity = fishHook.getVelocity();
                    fVelocity.setY(fVelocity.getY() - 0.01);
                    fishHook.setVelocity(fVelocity);
                }
            }
        }, 1, 1));
    }

    @EventHandler
    public void onFishEvent(PlayerFishEvent event) throws InvocationTargetException, IllegalAccessException {
        FishHook fishHook = (FishHook) getHook.invoke(event);

        if(!isEnabled(fishHook.getWorld())) return;

        if (event.getState() == PlayerFishEvent.State.FISHING) {
            Player casterPlayer = event.getPlayer();
            double playerYaw = casterPlayer.getLocation().getYaw();
            double playerPitch = casterPlayer.getLocation().getPitch();

            float oldMaxVelocity = 0.4F;
            double velocityX = -Math.sin(playerYaw / 180.0F * (float) Math.PI) * Math.cos(playerPitch / 180.0F * (float) Math.PI) * oldMaxVelocity;
            double velocityZ = Math.cos(playerYaw / 180.0F * (float) Math.PI) * Math.cos(playerPitch / 180.0F * (float) Math.PI) * oldMaxVelocity;
            double velocityY = -Math.sin(playerPitch / 180.0F * (float) Math.PI) * oldMaxVelocity;

            double oldVelocityMultiplier = 1.5;

            float vectorLength = (float) Math.sqrt(velocityX * velocityX + velocityY * velocityY + velocityZ * velocityZ);
            velocityX = velocityX / (double) vectorLength;
            velocityY = velocityY / (double) vectorLength;
            velocityZ = velocityZ / (double) vectorLength;
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


    @EventHandler(priority = EventPriority.MONITOR)
    public void onFishingEventMonitor(PlayerFishEvent event) throws InvocationTargetException, IllegalAccessException {
        FishHook fishHook = (FishHook) getHook.invoke(event);

        if(!isEnabled(fishHook.getWorld())) return;

        if (event.getState() == PlayerFishEvent.State.FISHING) {
            fishHooks.add(fishHook);
        }

        if (event.getState()  == PlayerFishEvent.State.IN_GROUND) {
            fishHooks.remove(fishHook);
        }
    }
}
