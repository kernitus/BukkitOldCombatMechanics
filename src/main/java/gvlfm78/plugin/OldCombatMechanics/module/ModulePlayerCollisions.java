package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.TeamUtils;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class ModulePlayerCollisions extends Module {

    public ModulePlayerCollisions(OCMMain plugin){
        super(plugin, "disable-player-collisions");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerLogin(PlayerJoinEvent e){
        TeamUtils.sendTeamPacket(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent e){
        TeamUtils.getSecurePlayers().remove(e.getPlayer());
    }

}
