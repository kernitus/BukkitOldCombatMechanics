/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.ConfigUtils;
import kernitus.plugin.OldCombatMechanics.utilities.Messenger;
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

import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;
import java.util.Random;

/**
 * This module reverts fishing rod gravity and velocity back to 1.8 behaviour
 * <p>
 * The rewritten fishing hook uses gravity 0.03, while the 1.8 and 1.9 hook uses 0.04
 * Launch velocity in 1.9+ is also different from the 1.8 formula
 */
public class ModuleFishingRodVelocity extends OCMModule {

    private Random random;
    private double gravity;
    private Boolean rewrittenHook;
    private Double legacyGravity;
    private boolean hasDefaultGravityApi = true;
    private boolean legacyTargetResolved;
    private java.lang.reflect.Method getHandle;
    private java.lang.reflect.Field legacyHooked;
    private boolean hasWaterApi = true;
    private boolean hasHookedEntityApi = true;
    private boolean hasGravityApi = true;
    private final Map<FishHook, Integer> activeHooks = new HashMap<>();
    private BukkitRunnable gravityTask;
    // In 1.12- getHook() returns a Fish which extends FishHook
    private final SpigotFunctionChooser<PlayerFishEvent, Object, FishHook> getHook = SpigotFunctionChooser.apiCompatReflectionCall(
            (e, params) -> e.getHook(),
            PlayerFishEvent.class, "getHook"
    );

    public ModuleFishingRodVelocity(OCMMain plugin) {
        super(plugin, "fishing-rod-velocity");
        reload();
        plugin.addDisableListener(this::clearHooks);
    }

    @Override
    public void reload() {
        random = new Random();

        gravity = ConfigUtils.finiteDouble(module(), "gravity", 0.04, 0.0, 1.0);
        clearHooks();
    }

    private void clearHooks() {
        if (gravityTask != null) {
            gravityTask.cancel();
            gravityTask = null;
        }
        activeHooks.clear();
    }

    @EventHandler (ignoreCancelled = true)
    public void onFishEvent(PlayerFishEvent event) {
        final FishHook fishHook = getHook.apply(event);
        final Player player = event.getPlayer();

        if (event.isCancelled() || !isEnabled(player) || event.getState() != PlayerFishEvent.State.FISHING) return;

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

        if (Math.abs(gravity - nativeGravity(fishHook)) < 1e-8) return;

        // Adjust gravity on every tick unless it's in water.
        // Performance: on 1.14+ this used to schedule one repeating task per cast/hook. When players spam rods,
        // that can create lots of concurrent scheduled tasks. Instead, keep a set of active hooks and run one
        // shared per-tick task only while the set is non-empty. The work is still O(active hooks), but we avoid
        // scheduler overhead and per-hook task allocations.
        activeHooks.put(fishHook, fishHook.getTicksLived());
        ensureGravityTask();
    }

    private void ensureGravityTask() {
        if (gravityTask != null) return;

        gravityTask = new BukkitRunnable() {
            @Override
            public void run() {
                // Stop the task as soon as it is not needed (no active hooks) to avoid a permanent every-tick cost.
                if (activeHooks.isEmpty()) {
                    cancel();
                    gravityTask = null;
                    return;
                }

                final Iterator<Map.Entry<FishHook, Integer>> it = activeHooks.entrySet().iterator();
                while (it.hasNext()) {
                    final Map.Entry<FishHook, Integer> entry = it.next();
                    final FishHook hook = entry.getKey();
                    if (!hook.isValid() || hook.isOnGround() || !usesGravity(hook) ||
                            !(hook.getShooter() instanceof Player) || !isEnabled((Player) hook.getShooter()) || isHooked(hook)) {
                        it.remove();
                        continue;
                    }

                    if (hook.getTicksLived() == entry.getValue()) continue;
                    entry.setValue(hook.getTicksLived());

                    // We check both conditions as sometimes it's underwater but in seagrass, or when bobbing not underwater but the material is water
                    if (!isInWater(hook)) {
                        final Vector fVelocity = hook.getVelocity();
                        // Rewritten hooks subtract gravity, move, then apply 0.92 drag: correct the upcoming tick.
                        // Legacy hooks move, subtract gravity, then drag: replace the previous tick's dragged gravity.
                        final double correction = (gravity - nativeGravity(hook)) * (isRewrittenHook(hook) ? 1.0 : 0.92);
                        fVelocity.setY(fVelocity.getY() - correction);
                        hook.setVelocity(fVelocity);
                    }
                }

                if (activeHooks.isEmpty()) {
                    cancel();
                    gravityTask = null;
                }
            }
        };
        gravityTask.runTaskTimer(plugin, 1, 1);
    }
    private double nativeGravity(FishHook hook) {
        if (hasDefaultGravityApi) {
            try {
                // Read each hook's live baseline. getGravity() instead returns zero when gravity is disabled.
                return hook.getDefaultGravity();
            } catch (NoSuchMethodError ignored) {
                hasDefaultGravityApi = false;
            }
        }
        if (legacyGravity == null) legacyGravity = isRewrittenHook(hook) ? 0.03 : 0.04;
        return legacyGravity;
    }

    private boolean isRewrittenHook(FishHook hook) {
        if (rewrittenHook == null) {
            try {
                // CraftFishHook exposes the rewritten native state machine through this public API.
                // Its tick subtracts gravity before moving; this is independent of the gravity magnitude.
                hook.getState();
                rewrittenHook = true;
            } catch (NoSuchMethodError ignored) {
                try {
                    rewrittenHook = java.util.Arrays.stream(nativeHookType(hook).getDeclaredFields())
                            .anyMatch(field -> field.getType().isEnum());
                } catch (RuntimeException exception) {
                    // Preserve the established fallback for unknown legacy wrappers only.
                    rewrittenHook = Reflector.versionIsNewerOrEqualTo(1, 12, 0);
                    Messenger.warn("Could not inspect native fishing hook timing; using rewritten=%s", rewrittenHook);
                }
            }
        }
        return rewrittenHook;
    }

    private Class<?> nativeHookType(FishHook hook) {
        if (getHandle == null) getHandle = Reflector.getMethod(hook.getClass(), "getHandle");
        Class<?> hookType = Reflector.invokeMethod(getHandle, hook).getClass();
        while (hookType.getSuperclass() != null && !hookType.getSimpleName().equals("EntityFishingHook")
                && !hookType.getSimpleName().equals("FishingHook")) hookType = hookType.getSuperclass();
        return hookType;
    }

    private boolean usesGravity(FishHook hook) {
        if (hasGravityApi) {
            try {
                return hook.hasGravity();
            } catch (NoSuchMethodError ignored) {
                hasGravityApi = false;
            }
        }
        return true;
    }

    private boolean isInWater(FishHook hook) {
        if (hasWaterApi) {
            try {
                if (hook.isInWater()) return true;
            } catch (NoSuchMethodError ignored) {
                hasWaterApi = false;
            }
        }
        final Material material = hook.getLocation().getBlock().getType();
        return material == Material.WATER || material.name().equals("STATIONARY_WATER");
    }

    private boolean isHooked(FishHook hook) {
        if (hasHookedEntityApi) {
            try {
                return hook.getHookedEntity() != null;
            } catch (NoSuchMethodError ignored) {
                hasHookedEntityApi = false;
            }
        }
        if (!legacyTargetResolved) {
            // Old Paper has no public hooked-entity accessor. Discover its native target only on this path.
            try {
                legacyHooked = java.util.Arrays.stream(nativeHookType(hook).getDeclaredFields())
                        .filter(field -> field.getType().getSimpleName().equals("Entity"))
                        .findFirst().orElse(null);
                if (legacyHooked != null) legacyHooked.setAccessible(true);
            } catch (RuntimeException exception) {
                legacyHooked = null;
                Messenger.warn("Could not inspect native fishing hook target; target detection is unavailable");
            }
            legacyTargetResolved = true;
        }
        if (legacyHooked != null) {
            try {
                return legacyHooked.get(Reflector.invokeMethod(getHandle, hook)) != null;
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Cannot inspect hooked entity", exception);
            }
        }
        return false;
    }

}
