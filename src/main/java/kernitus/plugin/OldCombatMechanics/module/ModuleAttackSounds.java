package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.Config;
import kernitus.plugin.OldCombatMechanics.utilities.Messenger;
import kernitus.plugin.OldCombatMechanics.utilities.packet.mitm.PacketAdapter;
import kernitus.plugin.OldCombatMechanics.utilities.packet.mitm.PacketEvent;
import kernitus.plugin.OldCombatMechanics.utilities.packet.mitm.PacketManager;
import kernitus.plugin.OldCombatMechanics.utilities.packet.sound.SoundPacket;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;

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

        private boolean disabledDueToError;

        @Override
        public void onPacketSend(PacketEvent packetEvent){
            if(disabledDueToError || !isEnabled(packetEvent.getPlayer().getWorld())){
                return;
            }

            try{
                SoundPacket.from(packetEvent.getPacket())
                        .filter(it -> blockedSounds.contains(it.getSoundName()))
                        .ifPresent(packet -> {
                            packetEvent.setCancelled(true);
                            if(Config.debugEnabled()){
                                debug("Blocked sound " + packet.getSoundName(), packetEvent.getPlayer());
                            }
                        });
            } catch(Exception | ExceptionInInitializerError e){
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
