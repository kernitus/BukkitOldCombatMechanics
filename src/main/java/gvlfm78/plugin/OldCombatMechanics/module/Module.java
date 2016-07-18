package gvlfm78.plugin.OldCombatMechanics.module;

import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import gvlfm78.plugin.OldCombatMechanics.OCMMain;
import gvlfm78.plugin.OldCombatMechanics.utilities.ArrayUtils;
import gvlfm78.plugin.OldCombatMechanics.utilities.Config;
import gvlfm78.plugin.OldCombatMechanics.utilities.Messenger;

/**
 * Created by Rayzr522 on 6/25/16.
 */
public class Module implements Listener {

    protected OCMMain plugin;
    protected String configName;

    private String moduleName;

    public Module(OCMMain plugin, String configName) {
        this.plugin = plugin;
        this.configName = configName;
        moduleName = ArrayUtils.last(getClass().getSimpleName().split("\\."));
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

    protected void debug(String text) {

        Messenger.debug("[DEBUG][" + moduleName + "] " + text);

    }

    protected void debug(String text, Player p) {

        if (Config.debugEnabled()) {
            Messenger.send(p, "&8&l[&fDEBUG&8&l][&f" + moduleName + "&8&l]&7 " + text);
        }

    }

}
