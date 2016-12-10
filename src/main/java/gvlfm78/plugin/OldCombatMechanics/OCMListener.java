package kernitus.plugin.OldCombatMechanics;

import java.io.File;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import kernitus.plugin.OldCombatMechanics.module.Module;

public class OCMListener extends Module implements Listener {

	private OCMMain plugin;
	private File pluginFile;

	public OCMListener(OCMMain plugin, File pluginFile) {
		super(plugin, "update-checker");
		this.plugin = plugin;
		this.pluginFile = pluginFile;
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerLogin(PlayerJoinEvent e) {
		final Player p = e.getPlayer();
		if(p.hasPermission("OldCombatMechanics.notify")){
			Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, new Runnable () {
				public void run() {

					OCMUpdateChecker updateChecker = new OCMUpdateChecker(plugin, pluginFile);

					// Checking for updates
					updateChecker.sendUpdateMessages(p);
				}
			},20L);
		}
	}
}