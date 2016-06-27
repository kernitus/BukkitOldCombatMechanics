package gvlfm78.plugin.OldCombatMechanics.module;

import gvlfm78.plugin.OldCombatMechanics.Config;
import gvlfm78.plugin.OldCombatMechanics.OCMMain;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.Listener;

/**
 * Created by Rayzr522 on 6/25/16.
 */
public class Module implements Listener {

    protected OCMMain plugin;
    protected String configName;

    public Module(OCMMain plugin, String configName) {
        this.plugin = plugin;
        this.configName = configName;
    }

    public boolean isEnabled(World world) {

        return Config.moduleEnabled(configName, world);

    }

    public boolean isEnabled() {

        return isEnabled(null);

    }

    public boolean isSettingEnabled(String name) {

        return plugin.getConfig().getBoolean(configName + "." + name);

    }

    public ConfigurationSection module() {

        return plugin.getConfig().getConfigurationSection(configName);

    }

}
