package kernitus.plugin.OldCombatMechanics;

import java.io.File;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.gravitydevelopment.updater.Updater;

public class OCMUpdateChecker {

	private OCMMain plugin;
	private final File pluginFile;
	private final SpigotUpdateChecker SUC;

	public OCMUpdateChecker(OCMMain plugin, File pluginFile){
		this.plugin = plugin;
		this.pluginFile = pluginFile;
		SUC = new SpigotUpdateChecker(plugin, 19510);
	}

	private String[] getUpdateMessages(){
		String[] updateMessages = new String[2];
		if(Bukkit.getVersion().toLowerCase().contains("spigot")){
			//Get messages from Spigot update checker
			if(SUC.getResult().name().equalsIgnoreCase("UPDATE_AVAILABLE")){
				//An update is available
				updateMessages[0] = ChatColor.BLUE + "An update for OldCombatMechanics to version " + SUC.getVersion() + " is available!";
				updateMessages[1] = ChatColor.BLUE + "Click here to download it: " + ChatColor.GRAY + "https://www.spigotmc.org/resources/oldcombatmechanics.19510/updates";
			}
		}
		else{//Get messages from bukkit update checker
			Updater updater = new Updater(plugin, 98233, pluginFile, Updater.UpdateType.NO_DOWNLOAD, false);
			if(updater.getResult().equals(Updater.UpdateResult.UPDATE_AVAILABLE)){
				updateMessages[0] = ChatColor.BLUE + "An update for OldCombatMechanics to version " + updater.getLatestName().replaceAll("[A-Za-z\\s]", "") + " is available!";
				updateMessages[1] = ChatColor.BLUE + "Click here to download it: " + ChatColor.GRAY + updater.getLatestFileLink();
			}
		}
		return updateMessages;
	}

	public void sendUpdateMessages(Player p){//Sends messages to a player
		for(String message : getUpdateMessages()){
			if(message!=null && !message.isEmpty())//If there was no update/check is disabled message will be null
				p.sendMessage(message);
		}
	}
	public void sendUpdateMessages(Logger l){//Sends messages to console
		for(String message : getUpdateMessages()){
			message = ChatColor.stripColor(message);
			if(message!=null && !message.isEmpty())//If there was no update/check is disabled message will be null
				l.info(message);
		}
	}
	public void sendUpdateMessages(CommandSender s){
		if(s instanceof Player){
			Player p = (Player) s;
			sendUpdateMessages(p);
		}
		else
			sendUpdateMessages(plugin.getLogger());
	}
}