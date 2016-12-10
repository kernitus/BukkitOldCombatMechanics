package gvlfm78.plugin.OldCombatMechanics;

import java.io.File;

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

import gvlfm78.plugin.OldCombatMechanics.utilities.Chatter;
import gvlfm78.plugin.OldCombatMechanics.utilities.Config;
import gvlfm78.plugin.OldCombatMechanics.utilities.Messenger;

public class OCMCommandHandler implements CommandExecutor {

	private OCMMain plugin;
	private File pluginFile;

	public OCMCommandHandler(OCMMain instance, File pluginFile) {
		this.plugin = instance;
		this.pluginFile = pluginFile;
	}

	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

		// Using cmd.getLabel() will always return the main label,
		// even if you're using an alias.
		if (cmd.getLabel().equalsIgnoreCase("OldCombatMechanics")) {

			if (args.length > 0 && args[0].equalsIgnoreCase("reload") && sender.hasPermission("oldcombatmechanics.reload")) {// Reloads config

				Config.reload();

				for (World world : Bukkit.getServer().getWorlds()) {

					boolean plugin_active = Config.moduleEnabled("disable-attack-cooldown", world);

					for (Player player : world.getPlayers()) {

						AttributeInstance attribute = player.getAttribute(Attribute.GENERIC_ATTACK_SPEED);
						double baseValue = attribute.getBaseValue();

						if (plugin_active) { // Setting to no cooldown

							Messenger.debug("Enabling cooldown for " + player.getName());
							double GAS = plugin.getConfig().getDouble("disable-attack-cooldown.general-attack-speed");
							if (baseValue != GAS) {
								attribute.setBaseValue(GAS);
								player.saveData();
							}
						} else { // Re-enabling cooldown
							Messenger.debug("Disabling cooldown for " + player.getName());
							if (baseValue != 4) {
								attribute.setBaseValue(4);
								player.saveData();
							}

						}

					}

				}

				Chatter.send(sender, "&6&lOldCombatMechanics&e config file reloaded");

			} else if( (args.length > 0 && args[0].equalsIgnoreCase("toggle")) && plugin.getConfig().getBoolean("enableIndividualToggle")
					&& sender.hasPermission("oldcombatmechanics.toggle") && sender instanceof Player){
				//Toggle their cooldown
				Player p = (Player) sender;
				double GAS = plugin.getConfig().getDouble("disable-attack-cooldown.general-attack-speed");

				AttributeInstance attribute = p.getAttribute(Attribute.GENERIC_ATTACK_SPEED);
				double baseValue = attribute.getBaseValue();
				String message = "";

				if (baseValue == GAS) {// They're enabled, let's disable it
					GAS = 4;
					message = "&1[OCM] &aAttack cooldown enabled";
				}
				else
					message = "&1[OCM] &4Attack cooldown disabled";

				attribute.setBaseValue(GAS);
				p.saveData();
				Chatter.send(p, message);

			} else { //Tell them about available commands
				OCMUpdateChecker updateChecker = new OCMUpdateChecker(plugin, pluginFile);

				PluginDescriptionFile pdf = plugin.getDescription();

				Chatter.send(sender, ChatColor.DARK_GRAY + Chatter.HORIZONTAL_BAR);

				Chatter.send(sender, "&6&lOldCombatMechanics&e by &cgvlfm78&e and &cRayzr522&e version &6" + pdf.getVersion());
				Chatter.send(sender, "&eYou can use &c/ocm reload&e to reload the config file");
				
				if(plugin.getConfig().getBoolean("enableIndividualToggle") && sender.hasPermission("oldcombatmechanics.toggle") && sender instanceof Player)
					Chatter.send(sender, "&eYou can use &c/ocm toggle&e to turn your attack cooldown on/off");

				Chatter.send(sender, ChatColor.DARK_GRAY + Chatter.HORIZONTAL_BAR);

				// Update check
				updateChecker.sendUpdateMessages(sender);
			}

		}

		return false;

	}

}