package gvlfm78.plugin.OldCombatMechanics;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class OCMListener implements Listener {

    private OCMMain plugin;

    public OCMListener(OCMMain instance) {
        this.plugin = instance;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLogin(PlayerJoinEvent e) {

        OCMUpdateChecker updateChecker = new OCMUpdateChecker(plugin);
        Player p = e.getPlayer();

        // Checking for updates
        if (p.hasPermission("OldCombatMechanics.notify")) {
            updateChecker.sendUpdateMessages(p);
        }

    }

}