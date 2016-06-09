package gvlfm78.plugin.OldCombatMechanics;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.FileConfiguration;
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
		FileConfiguration config = plugin.getConfig();

		// Checking for updates
		if (p.isOp()) {
			if (config.getBoolean("update-checker")) {
				if (plugin.getConfig().getBoolean("settings.checkForUpdates")) {
					if (updateChecker.updateNeeded()) {
						p.sendMessage("An update for OldCombatMechanics to version " + updateChecker.getVersion()
								+ " is available!");
						p.sendMessage("Click here to download it: " + updateChecker.getLink());
					}
				}
			}
		}
		if (config.getBoolean("plugin-active")) {// Setting to no cooldown
			AttributeInstance attribute = p.getAttribute(Attribute.GENERIC_ATTACK_SPEED);
			double baseValue = attribute.getBaseValue();
			if (baseValue != 1024) {
				attribute.setBaseValue(1024);
				p.saveData();
			}
		} else {// Re-enabling cooldown
			AttributeInstance attribute = p.getAttribute(Attribute.GENERIC_ATTACK_SPEED);
			double baseValue = attribute.getBaseValue();
			if (baseValue != 4) {
				attribute.setBaseValue(4);
				p.saveData();
			}
		}
		
		if (config.getBoolean("disable-player-collisions")) {
			
			p.setCollidable(false);
		
		} else {
			
			p.setCollidable(true);
			
		}
		
	}
}