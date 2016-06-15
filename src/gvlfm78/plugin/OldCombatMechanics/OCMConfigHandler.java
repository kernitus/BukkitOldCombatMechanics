package gvlfm78.plugin.OldCombatMechanics;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public class OCMConfigHandler {
	private OCMMain plugin;

	public OCMConfigHandler(OCMMain instance) {
		this.plugin = instance;
	}

	public void upgradeConfig() {
		plugin.saveDefaultConfig();
	}

	public void setupConfigyml() {
		if (!getFile("config.yml").exists())
			setupConfig("config.yml");
	}

	public void setupConfig(String fileName) {
		plugin.saveResource(fileName, false);
		plugin.getLogger().info("Config file " + fileName + " generated");
	}

	public YamlConfiguration getConfig(String fileName) {
		return YamlConfiguration.loadConfiguration(getFile(fileName));
	}

	public File getFile(String fileName) {
		return new File(plugin.getDataFolder() + File.separator + fileName);
	}
	public boolean doesConfigymlExist(){
		File file = getFile("config.yml");
		return file.exists() ? true : false;
	}
}
