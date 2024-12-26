/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities

import kernitus.plugin.OldCombatMechanics.ModuleLoader
import kernitus.plugin.OldCombatMechanics.ModuleLoader.toggleModules
import kernitus.plugin.OldCombatMechanics.OCMMain
import kernitus.plugin.OldCombatMechanics.module.OCMModule
import kernitus.plugin.OldCombatMechanics.utilities.damage.EntityDamageByEntityListener.Companion.INSTANCE
import kernitus.plugin.OldCombatMechanics.utilities.damage.WeaponDamages
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import java.io.InputStreamReader
import java.util.*
import java.util.function.Consumer
import java.util.logging.Level

object Config {
    private const val CONFIG_NAME = "config.yml"
    private lateinit var plugin: OCMMain
    private lateinit var config: FileConfiguration
    val modesets: MutableMap<String, MutableSet<String>> = HashMap()
    val worlds: MutableMap<UUID, Set<String>> = HashMap()

    fun initialise(plugin: OCMMain) {
        Config.plugin = plugin
        config = plugin.config
        // Make sure to separately call reload()
    }

    /**
     * @return Whether config was changed or not
     */
    private fun checkConfigVersion(): Boolean {
        val defaultConfig = YamlConfiguration.loadConfiguration(
            InputStreamReader(plugin.getResource(CONFIG_NAME)!!)
        )

        if (config.getInt("config-version") != defaultConfig.getInt("config-version")) {
            plugin.upgradeConfig()
            reload()
            return true
        }

        return false
    }


    fun reload() {
        if (plugin.doesConfigExist()) {
            plugin.reloadConfig()
            config = plugin.config
        } else plugin.upgradeConfig()

        // checkConfigVersion will call #reload() again anyways
        if (checkConfigVersion()) return

        Messenger.reloadConfig(
            config.getBoolean("debug.enabled"), config.getString("message-prefix")!!
        )

        WeaponDamages.initialise(plugin) //Reload weapon damages from config

        //Set EntityDamagedByEntityListener to enabled if either of these modules is enabled
        INSTANCE.enabled =
            (moduleEnabled("old-tool-damage") || moduleEnabled("old-potion-effects") || moduleEnabled("old-critical-hits"))

        reloadModesets()
        reloadWorlds()

        // Dynamically registers / unregisters all event listeners for optimal performance
        toggleModules()

        ModuleLoader.modules.forEach(Consumer { module: OCMModule ->
            try {
                module.reload()
            } catch (e: Exception) {
                plugin.logger.log(Level.WARNING, "Error reloading module '$module'", e)
            }
        })
    }

    private fun reloadModesets() {
        modesets.clear()

        val moduleNames = ModuleLoader.modules.map { it.configName }.toSet()
        val modesetsSection = config.getConfigurationSection("modesets")
            ?: throw IllegalStateException("Modesets section is missing from config.yml")

        // A set to keep track of all the modules that are already in a modeset
        val modulesInModesets: MutableSet<String> = HashSet()

        // Iterate over each modeset
        for (key in modesetsSection.getKeys(false)) {
            // Retrieve the list of module names for the current modeset
            val moduleList = modesetsSection.getStringList(key)
            val moduleSet: MutableSet<String> = HashSet(moduleList)

            // Add the current modeset and its modules to the map
            modesets[key] = moduleSet

            // Add all modules in the current modeset to the tracking set
            modulesInModesets.addAll(moduleSet)
        }

        // Find modules not present in any modeset
        val modulesNotInAnyModeset: MutableSet<String> = HashSet(moduleNames)
        modulesNotInAnyModeset.removeAll(modulesInModesets)

        // Add any module not present in any modeset to all modesets
        modesets.values.forEach { it.addAll(modulesNotInAnyModeset) }
    }

    private fun reloadWorlds() {
        worlds.clear()

        val worldsSection = config.getConfigurationSection("worlds")
            ?: throw IllegalStateException("Worlds section is missing from config.yml!")

        // Iterate over each world
        for (worldName in worldsSection.getKeys(false)) {
            val world = Bukkit.getWorld(worldName)
            if (world == null) {
                Messenger.warn("Configured world $worldName not found, skipping (might be loaded later?)...")
                continue
            }
            addWorld(world, worldsSection)
        }
    }

    fun addWorld(world: World) {
        val worldsSection = config.getConfigurationSection("worlds")
            ?: throw IllegalStateException("Worlds section is missing from config.yml!")
        addWorld(world, worldsSection)
    }

    fun addWorld(world: World, worldsSection: ConfigurationSection) {
        // Retrieve the list of modeset names for the current world
        // Using a linkedhashset to remove duplicates but retain insertion order (important for default modeset)
        val modesetsSet = LinkedHashSet(worldsSection.getStringList(world.name))

        // Add the current world and its modesets to the map
        worlds[world.uid] = modesetsSet
    }

    fun removeWorld(world: World) = worlds.remove(world.uid)

    /**
     * Get the default modeset for the given world.
     *
     * @param worldId The UUID for the world to check the allowed modesets for
     * @return The default modeset, if found, else null.
     */
    fun getDefaultModeset(worldId: UUID): Set<String>? {
        if (!worlds.containsKey(worldId)) return null

        val set = worlds[worldId]
        if (set.isNullOrEmpty()) return null

        val iterator = set.iterator()
        if (iterator.hasNext()) {
            val modesetName = iterator.next()
            if (modesets.containsKey(modesetName)) {
                return modesets[modesetName]
            }
        }

        return null
    }

    /**
     * Checks whether the module is present in the default modeset for the specified world,
     * or globally, is no world is specified
     *
     * @param moduleName The name of the module to check
     * @param world The world to get the default modeset for
     * @return Whether the module is enabled for the found modeset
     */
    fun moduleEnabled(moduleName: String, world: World? = null): Boolean {
        val section = config.getConfigurationSection(moduleName)

        if (section == null) {
            plugin.logger.warning("Tried to check module '$moduleName', but it didn't exist!")
            return false
        }

        if (!section.getBoolean("enabled")) return false
        if (world == null) return true // Only checking if module is globally enabled


        val defaultModeset = getDefaultModeset(world.uid) ?: return true
        // If no default modeset found, the module should be enabled

        // Check if module is in default modeset
        return defaultModeset.contains(moduleName)
    }

    fun debugEnabled() = moduleEnabled("debug", null)

    fun moduleSettingEnabled(moduleName: String, moduleSettingName: String) =
        config.getBoolean("$moduleName.$moduleSettingName")

    /**
     * Only use if you can't access config through plugin instance
     *
     * @return config.yml instance
     */
    fun getConfig(): FileConfiguration = plugin.config
}
