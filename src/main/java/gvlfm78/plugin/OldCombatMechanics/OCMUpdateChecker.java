package gvlfm78.plugin.OldCombatMechanics;

import java.io.File;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import gvlfm78.plugin.OldCombatMechanics.module.Module;
import net.gravitydevelopment.updater.Updater;

public class OCMUpdateChecker extends Module implements Listener {

	private OCMMain plugin;
	private File pluginFile;
	private final SpigotUpdateChecker SUC;

	public OCMUpdateChecker(OCMMain plugin, File pluginFile) {
		super(plugin, "update-checker");
		this.plugin = plugin;
		this.pluginFile = pluginFile;
		SUC = new SpigotUpdateChecker(plugin, 19510);
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerLogin(PlayerJoinEvent e) {
		final Player p = e.getPlayer();
		if(p.hasPermission("OldCombatMechanics.notify")){
			Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, ()  -> {

				OCMUpdateChecker updateChecker = new OCMUpdateChecker(plugin, pluginFile);

				// Checking for updates
				updateChecker.sendUpdateMessages(p);
			} , 20L);
		}
	}

	private String[] getUpdateMessages(){
		String[] updateMessages = new String[2];
		boolean spigotChecker = false;
		switch(module().getString("mode").toLowerCase()){
		case "spigot": spigotChecker = true; break;
		case "auto":
			if(Bukkit.getVersion().toLowerCase().contains("spigot"))
				spigotChecker = true;
		}

		if(spigotChecker){
			debug("Using spigot update checker");
			//Get messages from Spigot update checker
			if(SUC.getResult().name().equalsIgnoreCase("UPDATE_AVAILABLE")){
				//An update is available
				updateMessages[0] = ChatColor.BLUE + "An update for OldCombatMechanics to version " + SUC.getVersion() + " is available!";
				updateMessages[1] = ChatColor.BLUE + "Click here to download it: " + ChatColor.GRAY + "https://www.spigotmc.org/resources/oldcombatmechanics.19510/updates";
			}
		}
		else{//Get messages from bukkit update checker
			debug("Using bukkit update checker");
			Updater updater = new Updater(plugin, 98233, pluginFile, Updater.UpdateType.NO_DOWNLOAD, false);
			if(updater.getResult().equals(Updater.UpdateResult.UPDATE_AVAILABLE)){
				//Updater knows local and remote versions are different, but not if it's an update
				String remoteVersion = updater.getLatestName().replaceAll("[A-Za-z\\s]", "");
				if(shouldUpdate(plugin.getDescription().getVersion(), remoteVersion)){
					updateMessages[0] = ChatColor.BLUE + "An update for OldCombatMechanics to version " + remoteVersion + " is available!";
					updateMessages[1] = ChatColor.BLUE + "Click here to download it: " + ChatColor.GRAY + updater.getLatestFileLink();
				}
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
	public boolean shouldUpdate(String localVersion, String remoteVersion) {
		return versionCompare(localVersion, remoteVersion) < 0;
		//return !localVersion.equalsIgnoreCase(remoteVersion);
	}
	public Integer versionCompare(String oldVer, String newVer){
		String[] vals1 = oldVer.split("\\.");
		String[] vals2 = newVer.split("\\.");
		int i = 0;
		// set index to first non-equal ordinal or length of shortest version string
		while (i < vals1.length && i < vals2.length && vals1[i].equals(vals2[i]))
			i++;
		// compare first non-equal ordinal number
		if (i < vals1.length && i < vals2.length){
			int diff = Integer.valueOf(vals1[i]).compareTo(Integer.valueOf(vals2[i]));
			return Integer.signum(diff);
		}
		// the strings are equal or one string is a substring of the other
		// e.g. "1.2.3" = "1.2.3" or "1.2.3" < "1.2.3.4"
		else
			return Integer.signum(vals1.length - vals2.length);
	}
}