package gvlfm78.plugin.OldCombatMechanics;

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

						//System.out.println("OCMCommandHandler.onCommand");
						//System.out.println("baseValue = " + baseValue);

						if (plugin_active) { // Setting to no cooldown

							System.out.println("Enabling cooldown for " + player.getName());
				    		if (baseValue != 1024) {

								attribute.setBaseValue(1024);
								player.saveData();

							}

						} else { // Re-enabling cooldown

							System.out.println("Disabling cooldown for " + player.getName());
							if (baseValue == 1024) {

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

				sender.sendMessage(ChatColor.GOLD + "OldCombatMechanics by gvlfm78 and Rayzr522 version " + pdf.getVersion());
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