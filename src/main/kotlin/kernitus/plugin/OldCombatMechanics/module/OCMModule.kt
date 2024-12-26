/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module

import kernitus.plugin.OldCombatMechanics.OCMMain
import kernitus.plugin.OldCombatMechanics.utilities.Config.debugEnabled
import kernitus.plugin.OldCombatMechanics.utilities.Config.modesets
import kernitus.plugin.OldCombatMechanics.utilities.Config.moduleEnabled
import kernitus.plugin.OldCombatMechanics.utilities.Messenger
import kernitus.plugin.OldCombatMechanics.utilities.Messenger.sendNoPrefix
import kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage.getPlayerData
import org.bukkit.World
import org.bukkit.command.CommandSender
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Entity
import org.bukkit.entity.HumanEntity
import org.bukkit.entity.Player
import org.bukkit.event.Listener

/**
 * A module providing some specific functionality, e.g. restoring fishing rod knockback.
 */
abstract class OCMModule protected constructor(protected var plugin: OCMMain, val configName: String) : Listener {
    /**
     * Get the module's name, as taken from the class name
     *
     * @return The module name, e.g. ModuleDisableAttackCooldown
     */
    val moduleName: String = javaClass.simpleName

    /**
     * Checks whether this module is globally en/disabled.
     *
     * @return true if this module is globally enabled
     */
    open fun isEnabled() = moduleEnabled(configName, null)

    /**
     * Checks whether the module is present in the default modeset for the specified world
     *
     * @param world The world to get the default modeset for
     * @return Whether the module is enabled for the found modeset in the given world
     */
    open fun isEnabled(world: World) = moduleEnabled(configName, world)

    /**
     * Whether this module should be enabled for this player given his current modeset
     */
    fun isEnabled(humanEntity: HumanEntity): Boolean {
        val world = humanEntity.world
        val modesetName = getPlayerData(humanEntity.uniqueId).getModesetForWorld(world.uid)

        if (modesetName == null) {
            debug("No modeset found!", humanEntity)
            debug("No modeset found for " + humanEntity.name)
            return isEnabled(world)
        }

        // Check if the modeset contains this module's name
        val modeset = modesets[modesetName]
        return modeset != null && modeset.contains(configName)
    }

    fun isEnabled(entity: Entity): Boolean {
        if (entity is HumanEntity) return isEnabled(entity)
        return isEnabled(entity.world)
    }

    /**
     * Returns if module should be enabled, giving priority to the attacker, if a human.
     * If neither entity is a human, checks if module should be enabled in the defender's world.
     *
     * @param attacker The entity that is performing the attack
     * @param defender The entity that is being attacked
     * @return Whether the module should be enabled for this particular interaction
     */
    fun isEnabled(attacker: Entity, defender: Entity): Boolean {
        if (attacker is HumanEntity) return isEnabled(attacker)
        if (defender is HumanEntity) return isEnabled(defender)
        return isEnabled(defender.world)
    }

    /**
     * Checks whether a given setting for this module is enabled.
     *
     * @param name the name of the setting
     * @return true if the setting with that name is enabled. Returns false if the setting did not exist.
     */
    fun isSettingEnabled(name: String) = plugin.config.getBoolean("$configName.$name", false)

    /**
     * Returns the configuration section for this module.
     *
     * @return the configuration section for this module
     */
    fun module(): ConfigurationSection {
        val section = plugin.config.getConfigurationSection(configName)
            ?: throw IllegalStateException("Module $configName has no configuration section!")
        return section
    }

    /**
     * Called when the plugin is reloaded. Should re-read all relevant config keys and other resources that might have
     * changed.
     */
    open fun reload() {
        // Intentionally left blank! Meant for individual modules to use.
    }

    /**
     * Called when player changes modeset. Re-apply any more permanent changes
     * depending on result of isEnabled(player).
     *
     * @param player The player that changed modeset
     */
    open fun onModesetChange(player: Player) {
        // Intentionally left blank! Meant for individual modules to use.
    }

    /**
     * Outputs a debug message.
     *
     * @param text the message text
     */
    protected fun debug(text: String) = Messenger.debug("[$moduleName] $text")

    /**
     * Sends a debug message to the given command sender.
     *
     * @param text   the message text
     * @param sender the sender to send it to
     */
    protected fun debug(text: String, sender: CommandSender) {
        if (debugEnabled()) {
            sendNoPrefix(
                sender, "&8&l[&fDEBUG&8&l][&f$moduleName&8&l]&7 $text"
            )
        }
    }

    override fun toString(): String {
        return configName.split("-").filter { it.isNotEmpty() }.takeIf { it.isNotEmpty() }?.joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { it.uppercaseChar() }
        } ?: configName
    }

}
