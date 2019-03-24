package gvlfm78.plugin.OldCombatMechanics.module;

import gvlfm78.plugin.OldCombatMechanics.OCMMain;
import gvlfm78.plugin.OldCombatMechanics.utilities.Config;
import gvlfm78.plugin.OldCombatMechanics.utilities.Messenger;
import org.apache.commons.lang.WordUtils;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.Listener;

/**
 * A module providing some specific functionality, e.g. restoring fishing rod knockback.
 */
public abstract class Module implements Listener {

    protected OCMMain plugin;

    private String configName;
    private String moduleName;

    /**
     * Creates a new module.
     *
     * @param plugin     the plugin instance
     * @param configName the name of the module in the config
     */
    protected Module(OCMMain plugin, String configName){
        this.plugin = plugin;
        this.configName = configName;
        this.moduleName = getClass().getSimpleName();
    }

    /**
     * Checks whether the plugin is enabled in the given world.
     *
     * @param world the world to check. Null to check whether it is globally disabled
     * @return true if the plugin is enabled in that world
     */
    public boolean isEnabled(World world){
        return Config.moduleEnabled(configName, world);
    }

    /**
     * Checks whether this plugin is globally en/disabled.
     *
     * @return true if this plugin is globally enabled
     */
    public boolean isEnabled(){
        return isEnabled(null);
    }

    /**
     * Checks whether a given setting for this plugin is enabled.
     *
     * @param name the name of the setting
     * @return true if the setting with that name is enabled. Returns false if the setting did not exist.
     */
    public boolean isSettingEnabled(String name){
        return plugin.getConfig().getBoolean(configName + "." + name, false);
    }

    /**
     * Returns the configuration section for this module.
     *
     * @return the configuration section for this module
     */
    public ConfigurationSection module(){
        return plugin.getConfig().getConfigurationSection(configName);
    }

    /**
     * Called when the plugin is reloaded. Should re-read all relevant config keys and other resources that might have
     * changed.
     */
    public void reload(){
        // Intentionally left blank! Meant for individual modules to use.
    }

    /**
     * Outputs a debug message.
     *
     * @param text the message text
     */
    protected void debug(String text){
        Messenger.debug("[" + moduleName + "] " + text);
    }

    /**
     * Sends a debug message to the given command sender.
     *
     * @param text   the message text
     * @param sender the sender to send it to
     */
    protected void debug(String text, CommandSender sender){
        if(Config.debugEnabled()){
            Messenger.send(sender, "&8&l[&fDEBUG&8&l][&f" + moduleName + "&8&l]&7 " + text);
        }
    }

    @Override
    public String toString(){
        return WordUtils.capitalizeFully(configName.replaceAll("-", " "));
    }
}
