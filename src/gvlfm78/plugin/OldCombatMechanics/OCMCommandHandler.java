package kernitus.plugin.OldCombatMechanics;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.PluginDescriptionFile;

public class OCMCommandHandler implements CommandExecutor{

	protected OCMUpdateChecker updateChecker;

	private OCMMain plugin;
	public OCMCommandHandler(OCMMain instance){
		this.plugin = instance;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if(label.equalsIgnoreCase("oldcombatmechanics")||label.equalsIgnoreCase("ocm")){
			PluginDescriptionFile pdf = plugin.getDescription();
			
			sender.sendMessage(ChatColor.GOLD+"OldCombatMechanics by kernitus version "+pdf.getVersion());

			//Update check
			this.updateChecker = new OCMUpdateChecker(plugin, "http://dev.bukkit.org/bukkit-plugins/oldcombatmechanics/files.rss");
			this.updateChecker.updateNeeded();
			if(plugin.getConfig().getBoolean("settings.checkForUpdates")){
				if(this.updateChecker.updateNeeded()){
					sender.sendMessage(ChatColor.BLUE+"An update of OldCombatMechanics to version " + this.updateChecker.getVersion()+"is available!");
					sender.sendMessage(ChatColor.BLUE+"Link to download it:"+ChatColor.GRAY+this.updateChecker.getLink());
				}
			}
		}
		return false;
	}

}