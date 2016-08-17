package kernitus.plugin.OldCombatMechanics;

import kernitus.plugin.OldCombatMechanics.module.Module;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class OCMListener extends Module implements Listener {

	private OCMMain plugin;

	public OCMListener(OCMMain plugin) {
		super(plugin, "update-checker");
		this.plugin = plugin;
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerLogin(PlayerJoinEvent e) {
		final Player p = e.getPlayer();
		if(p.hasPermission("OldCombatMechanics.notify")){
			Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, new Runnable () {
				public void run() {

					OCMUpdateChecker updateChecker = new OCMUpdateChecker(plugin);

					// Checking for updates
					updateChecker.sendUpdateMessages(p);
				}
			},20L);
		}
	}
}