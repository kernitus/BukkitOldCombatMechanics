/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities.compatibility;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Optional MythicMobs bridge for damage mechanics that explicitly prevent knockback.
 *
 * <p>This class deliberately uses reflection only. MythicMobs is not a compile-time
 * dependency and all public API access is feature-detected at runtime.</p>
 */
public final class MythicMobsKnockbackBridge {

    private static final String MYTHIC_MOBS_PLUGIN = "MythicMobs";
    private static final String MYTHIC_DAMAGE_EVENT = "io.lumine.mythic.bukkit.events.MythicDamageEvent";
    private static final String NO_IMPACT_TAG = "no_impact";

    private final OCMMain plugin;
    private final Listener listener = new Listener() { };
    private final Map<UUID, NoKnockbackMarker> markers = new HashMap<>();

    private Method getDamageMetadata;
    private Method getTarget;
    private Method getPreventsKnockback;
    private Method getTags;
    private Method getData;
    private Method getCaster;
    private Method getSource;
    private BukkitTask cleanupTask;
    private long tickCounter;
    private boolean registered;
    private boolean disabled;
    private boolean warnedDisabled;
    private boolean warnedMissingAccessors;

    public MythicMobsKnockbackBridge(OCMMain plugin) {
        this.plugin = plugin;
        registerIfAvailable();
    }

    /**
     * Consumes a one-shot no-knockback marker for the supplied target and optional source.
     *
     * @param targetId target entity UUID
     * @param sourceId source entity UUID, or {@code null} if the source cannot be mapped cleanly
     * @return true when OCM knockback should be suppressed
     */
    public boolean consumeNoKnockback(UUID targetId, UUID sourceId) {
        if (targetId == null) return false;

        final NoKnockbackMarker marker = markers.get(targetId);
        if (marker == null) return false;
        if (marker.expiresAtTick <= tickCounter) {
            markers.remove(targetId);
            stopCleanupTaskIfIdle();
            return false;
        }

        if (marker.sourceId != null && sourceId != null && !marker.sourceId.equals(sourceId)) {
            return false;
        }

        markers.remove(targetId);
        stopCleanupTaskIfIdle();
        return true;
    }

    private void registerIfAvailable() {
        try {
            registerIfAvailableSafely();
        } catch (LinkageError | RuntimeException ex) {
            disableBridge("MythicMobs knockback bridge disabled: incompatible MythicMobs API could not be loaded safely", ex);
        }
    }

    private void registerIfAvailableSafely() {
        if (!plugin.getServer().getPluginManager().isPluginEnabled(MYTHIC_MOBS_PLUGIN)) return;

        final Class<? extends Event> eventClass;
        try {
            eventClass = Class.forName(MYTHIC_DAMAGE_EVENT).asSubclass(Event.class);
        } catch (ClassNotFoundException | ClassCastException ex) {
            plugin.getLogger().fine("MythicMobs knockback bridge unavailable: MythicDamageEvent was not found");
            return;
        } catch (LinkageError | RuntimeException ex) {
            disableBridge("MythicMobs knockback bridge disabled: MythicDamageEvent could not be loaded safely", ex);
            return;
        }

        getDamageMetadata = publicNoArgMethod(eventClass, "getDamageMetadata");
        if (getDamageMetadata == null) getDamageMetadata = publicNoArgMethod(eventClass, "getMetadata");
        getTarget = publicNoArgMethod(eventClass, "getTarget");
        getCaster = publicNoArgMethod(eventClass, "getCaster");
        getSource = publicNoArgMethod(eventClass, "getSource");
        if (getSource == null) getSource = publicNoArgMethod(eventClass, "getDamager");
        if (getSource == null) getSource = publicNoArgMethod(eventClass, "getAttacker");
        if (disabled) return;
        if (getSource == null && getCaster == null) {
            plugin.getLogger().warning("MythicMobs knockback bridge found no source or caster accessor; no-knockback markers will not be source-specific");
        }

        if (getDamageMetadata == null) {
            plugin.getLogger().warning("MythicMobs knockback bridge unavailable: MythicDamageEvent exposes no damage metadata accessor");
            return;
        }
        if (getTarget == null) {
            plugin.getLogger().warning("MythicMobs knockback bridge unavailable: MythicDamageEvent exposes no target accessor");
            return;
        }

        try {
            plugin.getServer().getPluginManager().registerEvent(eventClass, listener, EventPriority.MONITOR,
                    new EventExecutor() {
                        @Override
                        public void execute(Listener ignored, Event event) throws EventException {
                            try {
                                handleMythicDamageEvent(event);
                            } catch (LinkageError | RuntimeException ex) {
                                disableBridge("MythicMobs knockback bridge disabled: incompatible MythicMobs API failed while handling damage", ex);
                            }
                        }
                    }, plugin, true);
        } catch (LinkageError | RuntimeException ex) {
            disableBridge("MythicMobs knockback bridge disabled: event registration failed for incompatible MythicMobs API", ex);
            return;
        }
        plugin.addDisableListener(() -> HandlerList.unregisterAll(listener));
        registered = true;
    }

    private void handleMythicDamageEvent(Event event) {
        if (disabled) return;

        final Object metadata = invoke(getDamageMetadata, event);
        if (!preventsKnockback(metadata)) return;

        final Entity target = toBukkitEntity(invoke(getTarget, event));
        if (target == null) {
            warnMissingAccessors("MythicMobs knockback bridge could not resolve the MythicDamageEvent target entity");
            return;
        }

        final Entity source = toBukkitEntity(invoke(getSource, event));
        if (source == null) {
            final Entity caster = toBukkitEntity(invoke(getCaster, event));
            markNoKnockback(target.getUniqueId(), caster == null ? null : caster.getUniqueId());
        } else {
            markNoKnockback(target.getUniqueId(), source.getUniqueId());
        }
    }

    private boolean preventsKnockback(Object metadata) {
        if (disabled) return false;
        if (metadata == null) return false;

        if (getPreventsKnockback == null || !getPreventsKnockback.getDeclaringClass().isInstance(metadata)) {
            getPreventsKnockback = publicNoArgMethod(metadata.getClass(), "getPreventsKnockback");
        }
        if (Boolean.TRUE.equals(invoke(getPreventsKnockback, metadata))) return true;

        for (String key : knockbackKeys()) {
            if (Boolean.TRUE.equals(invokeSingleArg(metadata, "getBoolean", key))) return true;
            if (metadataValuePreventsKnockback(invokeSingleArg(metadata, "getValue", key), 0)) return true;
            if (metadataValuePreventsKnockback(invokeTwoArgs(metadata, "getOrDefault", key, Boolean.FALSE), 0)) return true;
        }

        if (getTags == null || !getTags.getDeclaringClass().isInstance(metadata)) {
            getTags = publicNoArgMethod(metadata.getClass(), "getTags");
        }
        if (hasNoImpactTag(invoke(getTags, metadata))) return true;

        if (getData == null || !getData.getDeclaringClass().isInstance(metadata)) {
            getData = publicNoArgMethod(metadata.getClass(), "getData");
        }
        return metadataValuePreventsKnockback(invoke(getData, metadata), 0);
    }

    private String[] knockbackKeys() {
        return new String[] {"preventsKnockback", "preventsknockback", "preventKnockback", "preventknockback"};
    }

    private boolean metadataValuePreventsKnockback(Object value, int depth) {
        if (value == null || depth > 4) return false;
        if (Boolean.TRUE.equals(value)) return true;
        if (value instanceof Map<?, ?>) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (isNoImpactTag(entry.getKey()) || metadataValuePreventsKnockback(entry.getValue(), depth + 1)) return true;
            }
            return false;
        }
        if (value instanceof Collection<?>) {
            for (Object item : (Collection<?>) value) {
                if (metadataValuePreventsKnockback(item, depth + 1)) return true;
            }
            return false;
        }
        if (value.getClass().isArray()) {
            final int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                if (metadataValuePreventsKnockback(Array.get(value, i), depth + 1)) return true;
            }
            return false;
        }
        return isNoImpactTag(value);
    }

    private boolean hasNoImpactTag(Object tags) {
        if (tags == null) return false;
        if (tags instanceof Collection<?>) {
            for (Object tag : (Collection<?>) tags) {
                if (isNoImpactTag(tag)) return true;
            }
            return false;
        }
        if (tags.getClass().isArray()) {
            final int length = Array.getLength(tags);
            for (int i = 0; i < length; i++) {
                if (isNoImpactTag(Array.get(tags, i))) return true;
            }
            return false;
        }
        return isNoImpactTag(tags);
    }

    private boolean isNoImpactTag(Object tag) {
        if (tag == null) return false;
        final String value = String.valueOf(tag).toLowerCase(Locale.ROOT);
        return value.equals(NO_IMPACT_TAG) || value.endsWith("." + NO_IMPACT_TAG)
                || value.contains("damagetags.no_impact") || value.contains(NO_IMPACT_TAG);
    }

    private Entity toBukkitEntity(Object object) {
        if (object == null) return null;
        if (object instanceof Entity) return (Entity) object;

        final Object bukkitEntity = invoke(publicNoArgMethod(object.getClass(), "getBukkitEntity"), object);
        if (bukkitEntity instanceof Entity) return (Entity) bukkitEntity;

        final Object entity = invoke(publicNoArgMethod(object.getClass(), "getEntity"), object);
        if (entity instanceof Entity) return (Entity) entity;
        if (entity != object) return toBukkitEntity(entity);

        return null;
    }

    private void markNoKnockback(UUID targetId, UUID sourceId) {
        markers.put(targetId, new NoKnockbackMarker(sourceId, tickCounter + 2));
        ensureCleanupTaskRunning();
    }

    private void ensureCleanupTaskRunning() {
        if (cleanupTask != null) return;
        cleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            tickCounter++;
            if (markers.isEmpty()) {
                stopCleanupTaskIfIdle();
                return;
            }

            final Iterator<Map.Entry<UUID, NoKnockbackMarker>> it = markers.entrySet().iterator();
            while (it.hasNext()) {
                if (it.next().getValue().expiresAtTick <= tickCounter) {
                    it.remove();
                }
            }

            stopCleanupTaskIfIdle();
        }, 1L, 1L);
    }

    private void stopCleanupTaskIfIdle() {
        if (cleanupTask == null || !markers.isEmpty()) return;
        cleanupTask.cancel();
        cleanupTask = null;
    }

    private Object invoke(Method method, Object handle) {
        return invoke(method, handle, new Object[0]);
    }

    private Object invokeSingleArg(Object handle, String name, Object arg) {
        return invoke(publicMethod(handle == null ? null : handle.getClass(), name, arg), handle, arg);
    }

    private Object invokeTwoArgs(Object handle, String name, Object first, Object second) {
        return invoke(publicMethod(handle == null ? null : handle.getClass(), name, first, second), handle, first, second);
    }

    private Object invoke(Method method, Object handle, Object... args) {
        if (disabled || method == null || handle == null) return null;
        try {
            return method.invoke(handle, args);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            if (registered) plugin.getLogger().fine("MythicMobs knockback bridge reflection failed: " + ex.getMessage());
            return null;
        } catch (LinkageError ex) {
            disableBridge("MythicMobs knockback bridge disabled: incompatible MythicMobs API failed during reflective access", ex);
            return null;
        }
    }

    private Method publicNoArgMethod(Class<?> type, String name) {
        if (type == null || name == null) return null;
        try {
            for (Method method : type.getMethods()) {
                if (method.getParameterCount() == 0 && method.getName().equals(name)
                        && Modifier.isPublic(method.getModifiers())) {
                    return method;
                }
            }
        } catch (LinkageError | RuntimeException ex) {
            disableBridge("MythicMobs knockback bridge disabled: incompatible MythicMobs API failed during method discovery", ex);
            return null;
        }
        return null;
    }

    private Method publicMethod(Class<?> type, String name, Object... args) {
        if (type == null || name == null) return null;
        try {
            for (Method method : type.getMethods()) {
                if (!method.getName().equals(name) || !Modifier.isPublic(method.getModifiers())
                        || method.getParameterCount() != args.length) continue;
                if (parametersAccept(method.getParameterTypes(), args)) return method;
            }
        } catch (LinkageError | RuntimeException ex) {
            disableBridge("MythicMobs knockback bridge disabled: incompatible MythicMobs API failed during method discovery", ex);
            return null;
        }
        return null;
    }

    private void disableBridge(String message, Throwable ex) {
        disabled = true;
        if (registered) HandlerList.unregisterAll(listener);
        registered = false;
        if (warnedDisabled) return;
        warnedDisabled = true;
        plugin.getLogger().log(Level.WARNING, message, ex);
    }

    private boolean parametersAccept(Class<?>[] parameterTypes, Object[] args) {
        for (int i = 0; i < parameterTypes.length; i++) {
            if (args[i] == null) continue;
            final Class<?> parameterType = wrapPrimitive(parameterTypes[i]);
            if (!parameterType.isInstance(args[i])) return false;
        }
        return true;
    }

    private Class<?> wrapPrimitive(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == char.class) return Character.class;
        return Void.class;
    }

    private void warnMissingAccessors(String message) {
        if (warnedMissingAccessors) return;
        warnedMissingAccessors = true;
        plugin.getLogger().log(Level.WARNING, message);
    }

    private static final class NoKnockbackMarker {
        private final UUID sourceId;
        private final long expiresAtTick;

        private NoKnockbackMarker(UUID sourceId, long expiresAtTick) {
            this.sourceId = sourceId;
            this.expiresAtTick = expiresAtTick;
        }
    }
}
