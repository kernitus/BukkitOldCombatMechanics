/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.Config;
import kernitus.plugin.OldCombatMechanics.utilities.Messenger;
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

    private final String configName;
    private final String moduleName;

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
     * Checks whether the module is enabled in the given world.
     *
     * @param world the world to check. Null to check whether it is globally disabled
     * @return true if the module is enabled in that world
     */
    public boolean isEnabled(World world){
        return Config.moduleEnabled(configName, world);
    }

    /**
     * Checks whether this module is globally en/disabled.
     *
     * @return true if this module is globally enabled
     */
    public boolean isEnabled(){
        return isEnabled(null);
    }

    /**
     * Checks whether a given setting for this module is enabled.
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
