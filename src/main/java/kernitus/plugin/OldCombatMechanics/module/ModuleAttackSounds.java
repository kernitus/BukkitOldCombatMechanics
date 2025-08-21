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
import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.Messenger;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * A module to disable the new attack sounds.
 */
public class ModuleAttackSounds extends OCMModule {

    private final ProtocolManager protocolManager = plugin.getProtocolManager();
    private final SoundListener soundListener = new SoundListener(plugin);
    private final Set<String> blockedSounds = new HashSet<>(getBlockedSounds());

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
        return module().getStringList("blocked-sound-names");
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
                try {
                    Method getLocationMethod = Reflector.getMethod(nmsSoundEvent.getClass(), "getLocation");
                    Object resourceLocation = Reflector.invokeMethod(getLocationMethod, nmsSoundEvent);
                    soundName = resourceLocation.toString();
                } catch (RuntimeException e) {
                    // Fallback
                    Method valueMethod = Reflector.getMethod(nmsSoundEvent.getClass(), "value");
                    Object actualSoundEvent = Reflector.invokeMethod(valueMethod, nmsSoundEvent);
                    Method getLocationMethod = Reflector.getMethod(actualSoundEvent.getClass(), "getLocation");
                    Object resourceLocation = Reflector.invokeMethod(getLocationMethod, actualSoundEvent);
                    soundName = resourceLocation.toString();
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
