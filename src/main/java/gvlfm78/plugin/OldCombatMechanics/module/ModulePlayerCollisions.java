package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.OCMTask;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Created by Rayzr522 on 6/25/16.
 */
public class ModulePlayerCollisions extends Module {

    OCMTask task = new OCMTask(plugin);

    public ModulePlayerCollisions(OCMMain plugin) {
        super(plugin, "disable-player-collisions");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLogin(PlayerJoinEvent e) {

        Player p = e.getPlayer();
        World world = p.getWorld();

        if (isEnabled(world)) {
            task.addPlayerToScoreboard(p);
        } else {
            task.removePlayerFromScoreboard(p);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWorldChange(PlayerChangedWorldEvent e) {

        Player player = e.getPlayer();
        World world = player.getWorld();

       if (isEnabled(world))

            task.addPlayerToScoreboard(player);

       else {

            task.removePlayerFromScoreboard(player);

       }

    }

}
