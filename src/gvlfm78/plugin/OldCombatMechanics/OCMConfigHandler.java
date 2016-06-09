package kernitus.plugin.OldCombatMechanics;

import java.io.File;

import org.bukkit.configuration.file.YamlConfiguration;

public class OCMConfigHandler {
	private OCMMain plugin;

	public OCMConfigHandler(OCMMain instance) {
		this.plugin = instance;
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
}
