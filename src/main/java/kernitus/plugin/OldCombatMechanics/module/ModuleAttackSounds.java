package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.Config;
import kernitus.plugin.OldCombatMechanics.utilities.Messenger;
import kernitus.plugin.OldCombatMechanics.utilities.packet.PacketAdapter;
import kernitus.plugin.OldCombatMechanics.utilities.packet.PacketEvent;
import kernitus.plugin.OldCombatMechanics.utilities.packet.PacketHelper;
import kernitus.plugin.OldCombatMechanics.utilities.packet.PacketManager;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.type.ClassType;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.type.PacketType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * A module to disable the new attack sounds.
 */
public class ModuleAttackSounds extends Module {

    private final SoundListener soundListener;

    private final Set<String> blockedSounds;

    public ModuleAttackSounds(OCMMain plugin){
        super(plugin, "disable-attack-sounds");

        this.soundListener = new SoundListener();
        this.blockedSounds = new HashSet<>(getBlockedSounds());

        // inject all players at startup, so the plugin still works properly after a reload
        OCMMain.getInstance().addEnableListener(() -> {
            for(Player player : Bukkit.getOnlinePlayers()){
                PacketManager.getInstance().addListener(soundListener, player);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerLogin(PlayerJoinEvent e){
        // always attach the listener, it checks internally
        PacketManager.getInstance().addListener(soundListener, e.getPlayer());
    }

    @Override
    public void reload(){
        blockedSounds.clear();
        blockedSounds.addAll(getBlockedSounds());
    }

    private Collection<String> getBlockedSounds(){
        return module().getStringList("blocked-sounds");
    }

    /**
     * Disables attack sounds.
     */
    private class SoundListener extends PacketAdapter {

        private final Class<?> PACKET_CLASS = PacketHelper.getPacketClass(PacketType.PlayOut, "NamedSoundEffect");
        private final Class<?> SOUND_EFFECT_CLASS = Reflector.getClass(ClassType.NMS, "sounds.SoundEffect");
        private final Class<?> MINECRAFT_KEY_CLASS = Reflector.getClass(ClassType.NMS, "resources.MinecraftKey");

        private boolean disabledDueToError;

        @Override
        public void onPacketSend(PacketEvent packetEvent){
            if(disabledDueToError || !isEnabled(packetEvent.getPlayer().getWorld())){
                return;
            }
            if(PACKET_CLASS != packetEvent.getPacket().getPacketClass()){
                return;
            }

            try{
                Object nmsPacket = packetEvent.getPacket().getNmsPacket();

                Object soundEffect = null;

                for(Field field : nmsPacket.getClass().getDeclaredFields()){
                    if(field.getType() == SOUND_EFFECT_CLASS){
                        field.setAccessible(true);
                        soundEffect = field.get(nmsPacket);
                    }
                }

                if(soundEffect == null){
                    Messenger.warn("Sound effect field not found!");
                    disabledDueToError = true;
                    return;
                }

                Object minecraftKey = null;

                for(Field field : soundEffect.getClass().getDeclaredFields()){
                    if(field.getType() == MINECRAFT_KEY_CLASS){
                        field.setAccessible(true);
                        minecraftKey = field.get(soundEffect);
                    }
                }

                if(minecraftKey == null){
                    Messenger.warn("Minecraft key field not found!");
                    disabledDueToError = true;
                    return;
                }

                // Bit hacky to rely on the toString method
                if(blockedSounds.contains(minecraftKey.toString())){
                    if(Config.debugEnabled()){
                        debug("Blocked sound " + minecraftKey, packetEvent.getPlayer());
                    }
                    packetEvent.setCancelled(true);
                }
            } catch(Exception e){
                disabledDueToError = true;
                Messenger.warn(
                        e,
                        "Error detecting sound packets. Please report it along with the following exception " +
                                "on github."
                );
            }
        }
    }
}
