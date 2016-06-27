package gvlfm78.plugin.OldCombatMechanics;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.RegisteredListener;

/**
 * Created by Rayzr522 on 6/14/16.
 */
public class Config {

    public static final int CONFIG_VERSION = 3;

    private static OCMMain plugin;
    private static FileConfiguration config;

    /*
    private static HashMap<String, Boolean> moduleStatus = null;
    private static List<String> modules = Arrays.asList("disable-attack-cooldown", "disable-player-collisions", "disable-sword-sweep", "old-tool-damage", "old-golden-apples");
    */

    public static void Initialise(OCMMain plugin) {

        Config.plugin = plugin;
        config = plugin.getConfig();

        if (config.getInt("config-version") != CONFIG_VERSION) {

            plugin.getLogger().warning("Config version does not match, backing up old config and creating a new one");
            plugin.upgradeConfig();
            reload();

        } else { load(); }
    }
    

    public static void reload() {

        if (plugin.doesConfigymlExist()) {
            plugin.reloadConfig();
            config = plugin.getConfig();
        } else
            plugin.upgradeConfig();

        plugin.restartTask(); //Restart no-collisions check
        plugin.restartSweepTask(); //Restart sword sweep check
        load();
        
        // Listeners checks
    	ArrayList<RegisteredListener> rls = HandlerList.getRegisteredListeners(plugin);
    	
    	for(RegisteredListener rl : rls){
    		Listener l = rl.getListener();
    		//Check if l is any of our module listeners
    		//If so, check if it should be enabled
    		//If so, check if it is disabled and enabled it
    		//If the module should be disabled check if it is enabled and disabled it
    		// by doing     		HandlerList.unregisterAll(listener);
    	}

    }

    private static void load() {

        WeaponDamages.Initialise(plugin); //Reload weapon damages from config
        Messenger.DEBUG_ENABLED = config.getBoolean("debug.enabled");

    }

    public static boolean moduleEnabled(String name, World world) {

        ConfigurationSection section = config.getConfigurationSection(name);

        if (section == null) {
            System.err.println("Tried to check module '" + name + "', but it didn't exist!");
            return false;
        }

        if (section.getBoolean("enabled")) {

            if (world != null && section.getList("worlds").size() > 0 && !section.getList("worlds").contains(world.getName())) {

                return false;

            }

            return true;

        }

        return false;

    }

    public static boolean moduleEnabled(String name) {
        return moduleEnabled(name, null);
    }

    public static boolean debugEnabled() {
        return moduleEnabled("debug", null);
    }

    public static List<?> getWorlds(String moduleName) {
        return config.getList(moduleName + ".worlds");
    }

    public static boolean moduleSettingEnabled(String moduleName, String moduleSettingName) {

        return config.getBoolean(moduleName + "." + moduleSettingName);

    }

    public static void setModuleSetting(String moduleName, String moduleSettingName, boolean value) {

        config.set(moduleName + "." + moduleSettingName, value);
        plugin.saveConfig();

    }

}
