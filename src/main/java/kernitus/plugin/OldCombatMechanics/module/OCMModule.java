/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.Config;
import kernitus.plugin.OldCombatMechanics.utilities.Messenger;
import kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/**
 * A module providing some specific functionality, e.g. restoring fishing rod knockback.
 */
public abstract class OCMModule implements Listener {

    protected OCMMain plugin;

    private final String configName;
    private final String moduleName;

    /**
     * Creates a new module.
     *
     * @param plugin     the plugin instance
     * @param configName the name of the module in the config
     */
    protected OCMModule(OCMMain plugin, String configName) {
        this.plugin = plugin;
        this.configName = configName;
        this.moduleName = getClass().getSimpleName();
    }

    /**
     * Checks whether this module is globally en/disabled.
     *
     * @return true if this module is globally enabled
     */
    public boolean isEnabled() {
        return Config.moduleEnabled(configName, null);
    }

    /**
     * Checks whether the module is present in the default modeset for the specified world
     *
     * @param world The world to get the default modeset for
     * @return Whether the module is enabled for the found modeset in the given world
     */
    public boolean isEnabled(World world) {
        return Config.moduleEnabled(configName, world);
    }

    /**
     * Whether this module should be enabled for this player given his current modeset
     */
    public boolean isEnabled(@NotNull HumanEntity humanEntity) {
        final World world = humanEntity.getWorld();
        final String modesetName = PlayerStorage.getPlayerData(humanEntity.getUniqueId()).getModesetForWorld(world.getUID());

        if (modesetName == null) {
            debug("No modeset found!", humanEntity);
            debug("No modeset found for " + humanEntity.getName());
            return isEnabled(world);
        }

        // Check if the modeset contains this module's name
        final Set<String> modeset = Config.getModesets().get(modesetName);
        return modeset != null && modeset.contains(configName);
    }

    public boolean isEnabled(@NotNull Entity entity) {
        if (entity instanceof HumanEntity)
            return isEnabled((HumanEntity) entity);
        return isEnabled(entity.getWorld());
    }

    /**
     * Returns if module should be enabled, giving priority to the attacker, if a human.
     * If neither entity is a human, checks if module should be enabled in the defender's world.
     *
     * @param attacker The entity that is performing the attack
     * @param defender The entity that is being attacked
     * @return Whether the module should be enabled for this particular interaction
     */
    public boolean isEnabled(@NotNull Entity attacker, @NotNull Entity defender) {
        if (attacker instanceof HumanEntity) return isEnabled((HumanEntity) attacker);
        if (defender instanceof HumanEntity) return isEnabled((HumanEntity) defender);
        return isEnabled(defender.getWorld());
    }

    /**
     * Checks whether a given setting for this module is enabled.
     *
     * @param name the name of the setting
     * @return true if the setting with that name is enabled. Returns false if the setting did not exist.
     */
    public boolean isSettingEnabled(String name) {
        return plugin.getConfig().getBoolean(configName + "." + name, false);
    }

    /**
     * Returns the configuration section for this module.
     *
     * @return the configuration section for this module
     */
    public ConfigurationSection module() {
        return plugin.getConfig().getConfigurationSection(configName);
    }

    /**
     * Called when the plugin is reloaded. Should re-read all relevant config keys and other resources that might have
     * changed.
     */
    public void reload() {
        // Intentionally left blank! Meant for individual modules to use.
    }

    /**
     * Called when player changes modeset. Re-apply any more permanent changes
     * depending on result of isEnabled(player).
     *
     * @param player The player that changed modeset
     */
    public void onModesetChange(Player player) {
        // Intentionally left blank! Meant for individual modules to use.
    }

    /**
     * Outputs a debug message.
     *
     * @param text the message text
     */
    protected void debug(String text) {
        Messenger.debug("[" + moduleName + "] " + text);
    }

    /**
     * Sends a debug message to the given command sender.
     *
     * @param text   the message text
     * @param sender the sender to send it to
     */
    protected void debug(String text, CommandSender sender) {
        if (Config.debugEnabled()) {
            Messenger.sendNoPrefix(sender, "&8&l[&fDEBUG&8&l][&f" + moduleName + "&8&l]&7 " + text);
        }
    }

    @Override
    public String toString() {
        return Arrays.stream(configName.split("-"))
                .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase(Locale.ROOT))
                .reduce((a, b) -> a + " " + b)
                .orElse(configName);
    }

    /**
     * Get the module's name, as taken from the class name
     *
     * @return The module name, e.g. ModuleDisableAttackCooldown
     */
    public String getModuleName() {
        return moduleName;
    }

    public String getConfigName() {
        return configName;
    }
}
