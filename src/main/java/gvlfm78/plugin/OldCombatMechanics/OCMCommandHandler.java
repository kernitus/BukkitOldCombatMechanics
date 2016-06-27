package kernitus.plugin.OldCombatMechanics;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;

public class OCMCommandHandler implements CommandExecutor {

	private OCMMain plugin;

	public OCMCommandHandler(OCMMain instance) {
		this.plugin = instance;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

		// Using cmd.getLabel() will always return the main label,
		// even if you're using an alias.
		if (cmd.getLabel().equalsIgnoreCase("OldCombatMechanics")) {

			if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {// Reloads config

				Config.reload();

				for (World world : Bukkit.getServer().getWorlds()) {
					
					boolean plugin_active = Config.moduleEnabled("disable-attack-cooldown",world);

					for (Player player : world.getPlayers()) {

						AttributeInstance attribute = player.getAttribute(Attribute.GENERIC_ATTACK_SPEED);
						double baseValue = attribute.getBaseValue();
						
						if (plugin_active) { // Setting to no cooldown

							Messenger.debug("Enabling cooldown for " + player.getName(), plugin);
							double GAS = plugin.getConfig().getDouble("disable-attack-cooldown.general-attack-speed");
				    		if (baseValue!=GAS){
								attribute.setBaseValue(GAS);
								player.saveData();
							}
						} else { // Re-enabling cooldown
							Messenger.debug("Disabling cooldown for " + player.getName(), plugin);
							if (baseValue!=4) {
								attribute.setBaseValue(4);
								player.saveData();
							}

						}

					}

				}

				sender.sendMessage(ChatColor.GOLD + "OldCombatMechanics " + ChatColor.GREEN + "Config file reloaded");

			} else {// Tells user about available commands

				OCMUpdateChecker updateChecker = new OCMUpdateChecker(plugin);

				PluginDescriptionFile pdf = plugin.getDescription();

				sender.sendMessage(ChatColor.GOLD + "OldCombatMechanics by kernitus and Rayzr522 version " + pdf.getVersion());
				sender.sendMessage(ChatColor.GREEN + "You can use " + ChatColor.ITALIC + "/ocm reload "
						+ ChatColor.RESET + ChatColor.GREEN + "to reload the config file");

				// Update check
				if (plugin.getConfig().getBoolean("update-checker")) {

					if (updateChecker.updateNeeded()) {

						sender.sendMessage(ChatColor.BLUE + "An update of OldCombatMechanics to version "
								+ updateChecker.getVersion() + " is available!");

						sender.sendMessage(
								ChatColor.BLUE + "Link to download it: " + ChatColor.GRAY + updateChecker.getLink());
					}

				}

			}

		}

		return false;

	}

}