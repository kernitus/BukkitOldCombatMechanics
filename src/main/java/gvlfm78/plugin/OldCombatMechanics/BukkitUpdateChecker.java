package kernitus.plugin.OldCombatMechanics;

import java.io.InputStream;
import java.net.URL;

import javax.xml.parsers.DocumentBuilderFactory;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class BukkitUpdateChecker {

	private OCMMain plugin;
	private URL url;
	private String version;
	private String link;

	public BukkitUpdateChecker(OCMMain instance){
		this.plugin = instance;
	}

	public boolean updateNeeded(){
		if(plugin.getConfig().getBoolean("update-checker")){
			try {
				url = new URL("http://dev.bukkit.org/bukkit-plugins/oldcombatmechanics/files.rss");
				InputStream input = this.url.openConnection().getInputStream();
				Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(input);

				Node latestFile = document.getElementsByTagName("item").item(0);
				NodeList children = latestFile.getChildNodes();

				this.version = children.item(1).getTextContent().replaceAll("[a-zA-Z ]", "");
				this.link = children.item(3).getTextContent();
				if(versionCompare(plugin.getDescription().getVersion(),this.version)<0){
					return true;
				}
			} catch (Exception uhe){
				plugin.getServer().getLogger().severe("OCM Could not check for updates");
			}
		}
		return false;
	}

	public String getVersion(){
		return this.version;
	}

	public String getLink(){
		return this.link;
	}

	public Integer versionCompare(String oldVer, String newVer){
		String[] vals1 = oldVer.split("\\.");
		String[] vals2 = newVer.split("\\.");
		int i = 0;
		while (i < vals1.length && i < vals2.length && vals1[i].equals(vals2[i]))
			i++;
		if (i < vals1.length && i < vals2.length) {
			int diff = Integer.valueOf(vals1[i]).compareTo(Integer.valueOf(vals2[i]));
			return Integer.signum(diff);
		}
		else
			return Integer.signum(vals1.length - vals2.length);
	}
	public String[] updateMessages(){//Saving update messages to an array for the other methods to use
		String[] updateMessages = new String[2];

		FileConfiguration config = plugin.getConfig();
		if (config.getBoolean("update-checker")) {
			if (config.getBoolean("settings.checkForUpdates")) {
				if (updateNeeded()) {
					updateMessages[0] = ChatColor.BLUE + "An update for OldCombatMechanics to version " + getVersion() + " is available!";
					updateMessages[1] = ChatColor.BLUE + "Click here to download it: " + ChatColor.GRAY + getLink();
				}
			}
		}
		return updateMessages;
	}
}
