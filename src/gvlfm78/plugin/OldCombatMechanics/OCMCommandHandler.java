package kernitus.plugin.OldCombatMechanics;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class OCMCommandHandler implements CommandExecutor{
	
	private OCMMain plugin;
	public OCMCommandHandler(OCMMain instance){
		this.plugin = instance;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if(label.equalsIgnoreCase("oldcombatmechanics")||label.equalsIgnoreCase("ocm")){
			sender.sendMessage("OldCombatMechanics version "+plugin.getDescription().getVersion());
		}
		return false;
	}

}
