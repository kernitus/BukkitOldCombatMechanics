package kernitus.plugin.OldCombatMechanics;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.PluginDescriptionFile;

public class OCMCommandHandler implements CommandExecutor{

	private OCMMain plugin;
	public OCMCommandHandler(OCMMain instance){
		this.plugin = instance;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if(label.equalsIgnoreCase("oldcombatmechanics")||label.equalsIgnoreCase("ocm")){
			if(args.length>0&&args[0].equalsIgnoreCase("reload")){//Reloads config
				plugin.reloadConfig();
				sender.sendMessage(ChatColor.GOLD+"OldCombatMechanics "+ChatColor.GREEN+"Config file reloaded");
			}
			else{//Tells user about available commands
				OCMUpdateChecker updateChecker = new OCMUpdateChecker(plugin);

				PluginDescriptionFile pdf = plugin.getDescription();

				sender.sendMessage(ChatColor.GOLD+"OldCombatMechanics by kernitus version "+pdf.getVersion());
				sender.sendMessage(ChatColor.GREEN+"You can use "+ChatColor.ITALIC+"/ocm reload "+ChatColor.RESET+ChatColor.GREEN+"to reload the config file");

				//Update check
				if(plugin.getConfig().getBoolean("update-checker")){
					if(updateChecker.updateNeeded()){
						sender.sendMessage(ChatColor.BLUE+"An update of OldCombatMechanics to version " + updateChecker.getVersion()+" is available!");
						sender.sendMessage(ChatColor.BLUE+"Link to download it: "+ChatColor.GRAY+updateChecker.getLink());
					}
				}
			}
		}
		return false;
	}

}