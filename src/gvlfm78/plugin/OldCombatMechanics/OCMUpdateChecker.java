package kernitus.plugin.OldCombatMechanics;

import java.io.InputStream;
import java.net.URL;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class OCMUpdateChecker {

	private OCMMain plugin;
	private URL url;
	private String version;
	private String link;

	public OCMUpdateChecker(OCMMain instance){
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

	public Integer versionCompare(String oldVer, String newVer)
	{
		String[] vals1 = oldVer.split("\\.");
		String[] vals2 = newVer.split("\\.");
		int i = 0;
		// set index to first non-equal ordinal or length of shortest version string
		while (i < vals1.length && i < vals2.length && vals1[i].equals(vals2[i]))
			i++;
		// compare first non-equal ordinal number
		if (i < vals1.length && i < vals2.length) {
			int diff = Integer.valueOf(vals1[i]).compareTo(Integer.valueOf(vals2[i]));
			return Integer.signum(diff);
		}
		// the strings are equal or one string is a substring of the other
		// e.g. "1.2.3" = "1.2.3" or "1.2.3" < "1.2.3.4"
		else
			return Integer.signum(vals1.length - vals2.length);
	}
	
}
