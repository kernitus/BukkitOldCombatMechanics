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
                        // This server version doesn't have the getKey method, so we fall back to the legacy name
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

                String soundName;
                Object soundEventObject = nmsSoundEvent;

                // On modern versions, the packet contains a Holder<SoundEvent> which wraps the
                // SoundEvent.
                // We need to call Holder.value() to get the actual SoundEvent.
                Method valueMethod = Reflector.getMethod(nmsSoundEvent.getClass(), "value");
                if (valueMethod != null) {
                    Object unwrappedEvent = Reflector.invokeMethod(valueMethod, nmsSoundEvent);
                    if (unwrappedEvent != null) {
                        soundEventObject = unwrappedEvent;
                    }
                }

                // On older versions, the packet contained the SoundEvent directly.
                // In both cases, we should now have a SoundEvent object.
                Method getLocationMethod = Reflector.getMethod(soundEventObject.getClass(), "getLocation");
                if (getLocationMethod == null) {
                    // The structure might have changed in a new version, let's try `location()` as
                    // a fallback for ResourceKey
                    getLocationMethod = Reflector.getMethod(soundEventObject.getClass(), "location");
                }

                if (getLocationMethod != null) {
                    Object resourceLocation = Reflector.invokeMethod(getLocationMethod, soundEventObject);
                    soundName = resourceLocation.toString();
                } else {
                    // If we still can't find it, something is very wrong.
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
