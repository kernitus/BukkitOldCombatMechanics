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

        if (isEnabled())
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

        public SoundListener(Plugin plugin) {
            super(plugin, PacketType.Play.Server.NAMED_SOUND_EFFECT);
        }

        @Override
        public void onPacketSending(PacketEvent packetEvent) {
            if (disabledDueToError || !isEnabled(packetEvent.getPlayer()))
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

                String soundName = null;
                Object soundEventObject = nmsSoundEvent;

                // On modern versions, the packet contains a Holder<SoundEvent> which wraps the
                // SoundEvent. Try to call Holder.value() to get the actual SoundEvent first.
                Method valueMethod = Reflector.getMethod(nmsSoundEvent.getClass(), "value");
                if (valueMethod != null) {
                    Object unwrappedEvent = Reflector.invokeMethod(valueMethod, nmsSoundEvent);
                    if (unwrappedEvent != null) {
                        soundEventObject = unwrappedEvent;
                    }
                }

                // Try to obtain a ResourceLocation from the unwrapped object first
                Method getLocationMethod = Reflector.getMethod(soundEventObject.getClass(), "getLocation");
                if (getLocationMethod == null) {
                    // Mojang-mapped methods often use `location()`
                    getLocationMethod = Reflector.getMethod(soundEventObject.getClass(), "location");
                }
                if (getLocationMethod != null) {
                    Object resourceLocation = Reflector.invokeMethod(getLocationMethod, soundEventObject);
                    if (resourceLocation != null)
                        soundName = resourceLocation.toString();
                }

                // If we still don't have a name, the object may still be a Holder instance.
                // Newer versions can expose the key without resolving the value via
                // unwrapKey()/key().
                if (soundName == null) {
                    Method unwrapKeyMethod = Reflector.getMethod(soundEventObject.getClass(), "unwrapKey");
                    if (unwrapKeyMethod != null) {
                        Object optionalKey = Reflector.invokeMethod(unwrapKeyMethod, soundEventObject);
                        if (optionalKey != null) {
                            Method isPresent = Reflector.getMethod(optionalKey.getClass(), "isPresent");
                            Method getOpt = Reflector.getMethod(optionalKey.getClass(), "get");
                            Boolean present = isPresent != null ? Reflector.invokeMethod(isPresent, optionalKey) : null;
                            if (Boolean.TRUE.equals(present) && getOpt != null) {
                                Object resourceKey = Reflector.invokeMethod(getOpt, optionalKey);
                                if (resourceKey != null) {
                                    Method keyLoc = Reflector.getMethod(resourceKey.getClass(), "location");
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

                // Alternative path: some versions provide key() directly on the holder
                if (soundName == null) {
                    Method keyMethod = Reflector.getMethod(soundEventObject.getClass(), "key");
                    if (keyMethod != null) {
                        Object resourceKey = Reflector.invokeMethod(keyMethod, soundEventObject);
                        if (resourceKey != null) {
                            Method keyLoc = Reflector.getMethod(resourceKey.getClass(), "location");
                            if (keyLoc != null) {
                                Object resourceLocation = Reflector.invokeMethod(keyLoc, resourceKey);
                                if (resourceLocation != null)
                                    soundName = resourceLocation.toString();
                            }
                        }
                    }
                }

                if (soundName == null) {
                    // If we still can't find it, something changed in the server/ProtocolLib
                    // mapping.
                    throw new IllegalStateException(
                            "Could not find sound location method on " + soundEventObject.getClass().getName());
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
