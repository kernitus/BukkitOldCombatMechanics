/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.cryptomorin.xseries.XSound;
import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.Messenger;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector;
import org.bukkit.Sound;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A module to disable the new attack sounds.
 */
public class ModuleAttackSounds extends OCMModule {

    private final ProtocolManager protocolManager = plugin.getProtocolManager();
    private final SoundListener soundListener = new SoundListener(plugin);
    private final Set<String> blockedSounds = new HashSet<>();

    public ModuleAttackSounds(OCMMain plugin) {
        super(plugin, "disable-attack-sounds");

        reload();
    }

    @Override
    public void reload() {
        blockedSounds.clear();
        blockedSounds.addAll(getBlockedSounds());

        if (isEnabled() && !blockedSounds.isEmpty())
            protocolManager.addPacketListener(soundListener);
        else
            protocolManager.removePacketListener(soundListener);
    }

    private Collection<String> getBlockedSounds() {
        List<String> fromConfig = module().getStringList("blocked-sound-names");
        Set<String> processed = new HashSet<>();
        for (String soundName : fromConfig) {
            Optional<XSound> xSound = XSound.matchXSound(soundName);
            if (xSound.isPresent()) {
                Sound sound = xSound.get().parseSound();
                if (sound != null) {
                    // On modern versions, we can get the namespaced key directly
                    try {
                        Method getKeyMethod = Sound.class.getMethod("getKey");
                        Object key = getKeyMethod.invoke(sound);
                        processed.add(key.toString());
                        continue;
                    } catch (Exception ignored) {
                        // This server version doesn't have the getKey method, so we fall back to the
                        // legacy name
                    }
                }
                // Fallback for older versions or if the sound is not in the Bukkit enum
                String processedName = soundName.toLowerCase(Locale.ROOT).replace('_', '.');
                if (!processedName.contains(":")) {
                    processedName = "minecraft:" + processedName;
                }
                processed.add(processedName);
            } else {
                Messenger.warn("Invalid sound name in config: " + soundName);
            }
        }
        return processed;
    }

    /**
     * Disables attack sounds.
     */
    private class SoundListener extends PacketAdapter {
        private boolean disabledDueToError;

        // Cache reflective lookups per class to reduce overhead in hot paths
        private final Map<Class<?>, Method> valueMethodCache = new ConcurrentHashMap<>();
        private final Map<Class<?>, Method> keyMethodCache = new ConcurrentHashMap<>();
        private final Map<Class<?>, Method> unwrapKeyMethodCache = new ConcurrentHashMap<>();
        private final Map<Class<?>, Method> getLocationMethodCache = new ConcurrentHashMap<>();
        private final Map<Class<?>, Method> locationMethodCache = new ConcurrentHashMap<>();
        // Cache resolved names per packet sound object; weak keys prevent memory
        // retention
        private final Map<Object, String> soundNameCache = Collections.synchronizedMap(new WeakHashMap<>());

        private Method getCachedOrFind(Map<Class<?>, Method> cache, Class<?> clazz, String name) {
            Method m = cache.get(clazz);
            if (m == null) {
                m = Reflector.getMethod(clazz, name);
                if (m != null)
                    cache.put(clazz, m);
            }
            return m;
        }

        public SoundListener(Plugin plugin) {
            super(plugin, PacketType.Play.Server.NAMED_SOUND_EFFECT);
        }

        @Override
        public void onPacketSending(PacketEvent packetEvent) {
            if (disabledDueToError || packetEvent.isCancelled() || !isEnabled(packetEvent.getPlayer()))
                return;
            if (blockedSounds.isEmpty())
                return;

            try {
                final PacketContainer packetContainer = packetEvent.getPacket();

                // Get the sound name via reflection to avoid conversion errors for unregistered
                // sounds.
                // This is necessary because some server versions/plugins might send sound
                // packets
                // with sounds that are not in the Bukkit registry, causing
                // packetContainer.getSoundEffects().read(0) to throw an exception.
                Object nmsSoundEvent = packetContainer.getModifier().read(0);
                if (nmsSoundEvent == null) {
                    return;
                }

                // Use a weakly-referenced cache to avoid recomputing names across recipients
                String soundName = soundNameCache.get(nmsSoundEvent);
                if (soundName == null) {
                    Object soundEventObject = nmsSoundEvent;

                    // Prefer not to resolve the Holder value: derive the ResourceKey first if
                    // possible
                    // 1) Try key() directly on the object (Holder may expose it)
                    Method keyMethod = getCachedOrFind(keyMethodCache, soundEventObject.getClass(), "key");
                    if (keyMethod != null) {
                        Object resourceKey = Reflector.invokeMethod(keyMethod, soundEventObject);
                        if (resourceKey != null) {
                            Method keyLoc = getCachedOrFind(locationMethodCache, resourceKey.getClass(), "location");
                            if (keyLoc != null) {
                                Object resourceLocation = Reflector.invokeMethod(keyLoc, resourceKey);
                                if (resourceLocation != null)
                                    soundName = resourceLocation.toString();
                            }
                        }
                    }

                    // 2) Try unwrapKey() -> Optional<ResourceKey>
                    if (soundName == null) {
                        Method unwrapKeyMethod = getCachedOrFind(unwrapKeyMethodCache, soundEventObject.getClass(),
                                "unwrapKey");
                        if (unwrapKeyMethod != null) {
                            Object optionalKeyObj = Reflector.invokeMethod(unwrapKeyMethod, soundEventObject);
                            if (optionalKeyObj instanceof Optional) {
                                Optional<?> opt = (Optional<?>) optionalKeyObj;
                                if (opt.isPresent()) {
                                    Object resourceKey = opt.get();
                                    if (resourceKey != null) {
                                        Method keyLoc = getCachedOrFind(locationMethodCache, resourceKey.getClass(),
                                                "location");
                                        if (keyLoc != null) {
                                            Object resourceLocation = Reflector.invokeMethod(keyLoc, resourceKey);
                                            if (resourceLocation != null)
                                                soundName = resourceLocation.toString();
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3) Try to obtain a ResourceLocation from the object directly (older versions)
                    if (soundName == null) {
                        Method getLocationMethod = getCachedOrFind(getLocationMethodCache, soundEventObject.getClass(),
                                "getLocation");
                        if (getLocationMethod == null) {
                            // Mojang-mapped methods often use `location()`
                            getLocationMethod = getCachedOrFind(locationMethodCache, soundEventObject.getClass(),
                                    "location");
                        }
                        if (getLocationMethod != null) {
                            Object resourceLocation = Reflector.invokeMethod(getLocationMethod, soundEventObject);
                            if (resourceLocation != null)
                                soundName = resourceLocation.toString();
                        }
                    }

                    // 4) As a last resort, resolve Holder.value() then repeat the steps
                    if (soundName == null) {
                        Method valueMethod = getCachedOrFind(valueMethodCache, nmsSoundEvent.getClass(), "value");
                        if (valueMethod != null) {
                            Object unwrappedEvent = Reflector.invokeMethod(valueMethod, nmsSoundEvent);
                            if (unwrappedEvent != null) {
                                soundEventObject = unwrappedEvent;

                                // Try direct location on the unwrapped SoundEvent
                                Method getLocationMethod = getCachedOrFind(getLocationMethodCache,
                                        soundEventObject.getClass(), "getLocation");
                                if (getLocationMethod == null) {
                                    getLocationMethod = getCachedOrFind(locationMethodCache,
                                            soundEventObject.getClass(), "location");
                                }
                                if (getLocationMethod != null) {
                                    Object resourceLocation = Reflector.invokeMethod(getLocationMethod,
                                            soundEventObject);
                                    if (resourceLocation != null)
                                        soundName = resourceLocation.toString();
                                }

                                // Or key() on the unwrapped event if provided
                                if (soundName == null) {
                                    Method keyMethodUnwrapped = getCachedOrFind(keyMethodCache,
                                            soundEventObject.getClass(), "key");
                                    if (keyMethodUnwrapped != null) {
                                        Object resourceKey = Reflector.invokeMethod(keyMethodUnwrapped,
                                                soundEventObject);
                                        if (resourceKey != null) {
                                            Method keyLoc = getCachedOrFind(locationMethodCache, resourceKey.getClass(),
                                                    "location");
                                            if (keyLoc != null) {
                                                Object resourceLocation = Reflector.invokeMethod(keyLoc, resourceKey);
                                                if (resourceLocation != null)
                                                    soundName = resourceLocation.toString();
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (soundName == null) {
                        // If we still can't find it, something changed in the server/ProtocolLib
                        // mapping
                        throw new IllegalStateException(
                                "Could not find sound location method on " + soundEventObject.getClass().getName());
                    }

                    soundNameCache.put(nmsSoundEvent, soundName);
                }

                if (blockedSounds.contains(soundName)) {
                    packetEvent.setCancelled(true);
                    debug("Blocked sound " + soundName, packetEvent.getPlayer());
                }
            } catch (Exception | ExceptionInInitializerError e) {
                disabledDueToError = true;
                Messenger.warn(
                        e,
                        "Error detecting sound packets. Please report it along with the following exception " +
                                "on github.");
            }
        }
    }
}
