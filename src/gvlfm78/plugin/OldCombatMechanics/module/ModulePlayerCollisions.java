package gvlfm78.plugin.OldCombatMechanics.module;

import gvlfm78.plugin.OldCombatMechanics.Config;
import gvlfm78.plugin.OldCombatMechanics.OCMMain;
import gvlfm78.plugin.OldCombatMechanics.OCMTask;
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
        super(plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLogin(PlayerJoinEvent e) {

        Player p = e.getPlayer();
        World world = p.getWorld();

        System.out.println("Config.moduleEnabled(\"disable-player-collisions\") = " + Config.moduleEnabled("disable-player-collisions"));

        if (Config.moduleEnabled("disable-player-collisions", world)) {
            task.addPlayerToScoreboard(p);
        } else {
            task.removePlayerFromScoreboard(p);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWorldChange(PlayerChangedWorldEvent e) {

        Player player = e.getPlayer();
        World world = player.getWorld();

       if (Config.moduleEnabled("disable-player-collisions", world))

            task.addPlayerToScoreboard(player);

       else {

            task.removePlayerFromScoreboard(player);

       }

    }

}
