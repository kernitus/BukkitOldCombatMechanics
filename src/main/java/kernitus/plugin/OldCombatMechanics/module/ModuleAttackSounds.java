/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSoundEffect;
import com.cryptomorin.xseries.XSound;
import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.Messenger;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.lang.reflect.Method;

/**
 * A module to disable the new attack sounds.
 */
public class ModuleAttackSounds extends OCMModule {

    private final SoundListener soundListener = new SoundListener();
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
            PacketEvents.getAPI().getEventManager().registerListener(soundListener);
        else
            PacketEvents.getAPI().getEventManager().unregisterListener(soundListener);
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
    private class SoundListener extends PacketListenerAbstract {
        private boolean disabledDueToError;

        @Override
        public void onPacketSend(PacketSendEvent packetEvent) {
            if (disabledDueToError || packetEvent.isCancelled())
                return;
            if (blockedSounds.isEmpty())
                return;

            final Object playerObject = packetEvent.getPlayer();
            if (!(playerObject instanceof Player))
                return;

            final Player player = (Player) playerObject;
            if (!isEnabled(player))
                return;

            final Object packetType = packetEvent.getPacketType();
            if (!PacketType.Play.Server.NAMED_SOUND_EFFECT.equals(packetType)
                    && !PacketType.Play.Server.SOUND_EFFECT.equals(packetType)) {
                return;
            }

            try {
                WrapperPlayServerSoundEffect wrapper = new WrapperPlayServerSoundEffect(packetEvent);
                com.github.retrooper.packetevents.protocol.sound.Sound sound = wrapper.getSound();
                if (sound == null || sound.getSoundId() == null)
                    return;

                String soundName = sound.getSoundId().toString();
                if (blockedSounds.contains(soundName)) {
                    packetEvent.setCancelled(true);
                    debug("Blocked sound " + soundName, player);
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
