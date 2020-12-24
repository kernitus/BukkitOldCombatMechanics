package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.util.Vector;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;

/**
 * This module reverts fishing rod gravity and velocity back to 1.8 behaviour
 * <p>
 * Fishing rod gravity in 1.14+ is 0.04 while in 1.8 it is 0.03
 * Launch velocity in 1.9+ is also changed from the 1.8 formula
 */
public class ModuleFishingRodVelocity extends Module {

    private static final HashSet<FishHook> fishHooks = new HashSet<>();
    private static boolean hasDifferentGravity;
    private final Random random = new Random();
    private final Method getHook;

    public ModuleFishingRodVelocity(OCMMain plugin) {
        super(plugin, "fishing-rod-velocity");

        // Versions 1.14+ have different gravity than previous versions
        final String bukkitVersion = Bukkit.getBukkitVersion();
        hasDifferentGravity = Reflector.versionIsNewerOrEqualAs(1, 14, 0);

        // Reflection because in 1.12- this method returns the Fish class, which was renamed to FishHook in 1.13+
        getHook = Reflector.getMethod(PlayerFishEvent.class, "getHook");

        if (hasDifferentGravity) {
            plugin.addEnableListener(() -> Bukkit.getScheduler().runTaskTimer(plugin, () -> {

                Iterator<FishHook> fishingHookIterator = ModuleFishingRodVelocity.fishHooks.iterator();

                while (fishingHookIterator.hasNext()) {
                    FishHook fishHook = fishingHookIterator.next();

                    if (!fishHook.isValid()) {
                        fishingHookIterator.remove();
                    } else if (fishHook.getWorld().getBlockAt(fishHook.getLocation()).getType() != Material.WATER) {
                        Vector fVelocity = fishHook.getVelocity();
                        fVelocity.setY(fVelocity.getY() - 0.01);
                        fishHook.setVelocity(fVelocity);
                    }
                }
            }, 1, 1));
        }
    }

    @EventHandler
    public void onFishEvent(PlayerFishEvent event) {
        final FishHook fishHook = Reflector.invokeMethod(getHook, event);

        if (!isEnabled(fishHook.getWorld()) || event.getState() != PlayerFishEvent.State.FISHING) return;

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
        velocityX = velocityX + this.random.nextGaussian() * 0.007499999832361937D;
        velocityY = velocityY + this.random.nextGaussian() * 0.007499999832361937D;
        velocityZ = velocityZ + this.random.nextGaussian() * 0.007499999832361937D;
        velocityX = velocityX * oldVelocityMultiplier;
        velocityY = velocityY * oldVelocityMultiplier;
        velocityZ = velocityZ * oldVelocityMultiplier;

        Vector newSpeed = new Vector(velocityX, velocityY, velocityZ);

        fishHook.setVelocity(newSpeed);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFishingEventMonitor(PlayerFishEvent event) {
        final FishHook fishHook = Reflector.invokeMethod(getHook, event);

        if (!isEnabled(fishHook.getWorld())) return;

        final PlayerFishEvent.State state = event.getState();

        if (hasDifferentGravity && state == PlayerFishEvent.State.FISHING) fishHooks.add(fishHook);

        if (state == PlayerFishEvent.State.IN_GROUND) fishHooks.remove(fishHook);
    }
}
