/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.api

import kernitus.plugin.OldCombatMechanics.ModuleLoader
import kernitus.plugin.OldCombatMechanics.OCMMain
import kernitus.plugin.OldCombatMechanics.module.OCMModule
import kernitus.plugin.OldCombatMechanics.utilities.Config
import kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerModuleOverrides
import kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.entity.Player
import java.util.Locale

class OldCombatMechanicsAPIImpl(private val plugin: OCMMain) : OldCombatMechanicsAPI {

    override fun getModesetNames(): Set<String> = Config.getModesetNames()

    override fun getAllowedModesets(world: World): Set<String> = Config.getAllowedModesets(world)

    override fun getModesetForPlayer(player: Player): String? =
        PlayerStorage.getPlayerData(player.uniqueId).getModesetForWorld(player.world.uid)

    override fun setModesetForPlayer(player: Player, modesetName: String) {
        val normalisedModesetName = modesetName.lowercase(Locale.ROOT)
        validateModesetForPlayer(player, normalisedModesetName)

        val worldId = player.world.uid
        val playerData = PlayerStorage.getPlayerData(player.uniqueId)
        val previousModeset = playerData.getModesetForWorld(worldId)
        if (previousModeset == normalisedModesetName) {
            return
        }

        playerData.setModesetForWorld(worldId, normalisedModesetName)
        PlayerStorage.setPlayerData(player.uniqueId, playerData)
        PlayerStorage.scheduleSave()
        fireModesetChangeEvent(player, previousModeset, normalisedModesetName, PlayerModesetChangeEvent.Reason.API)
        notifyPlayerStateChanged(player)
    }

    override fun forceEnableModuleForPlayer(player: Player, moduleName: String) {
        setSingleModuleOverride(player, moduleName, PlayerModuleOverride.FORCE_ENABLED)
    }

    override fun forceDisableModuleForPlayer(player: Player, moduleName: String) {
        setSingleModuleOverride(player, moduleName, PlayerModuleOverride.FORCE_DISABLED)
    }

    override fun clearModuleOverrideForPlayer(player: Player, moduleName: String) {
        val normalisedModuleName = validateModuleName(moduleName)
        val previousOverride = PlayerModuleOverrides.getOverride(player, normalisedModuleName)
        if (previousOverride != PlayerModuleOverride.DEFAULT && PlayerModuleOverrides.clearOverride(player, normalisedModuleName)) {
            fireModuleOverrideChangeEvent(player, normalisedModuleName, previousOverride, PlayerModuleOverride.DEFAULT)
            notifyPlayerStateChanged(player)
        }
    }

    override fun clearAllModuleOverridesForPlayer(player: Player) {
        val changedOverrides = getModuleOverridesForPlayer(player).toSortedMap()
        if (changedOverrides.isNotEmpty() && PlayerModuleOverrides.clearAll(player)) {
            changedOverrides.forEach { (moduleName, previousOverride) ->
                fireModuleOverrideChangeEvent(player, moduleName, previousOverride, PlayerModuleOverride.DEFAULT)
            }
            notifyPlayerStateChanged(player)
        }
    }

    override fun getModuleOverrideForPlayer(player: Player, moduleName: String): PlayerModuleOverride {
        val normalisedModuleName = validateModuleName(moduleName)
        return PlayerModuleOverrides.getOverride(player, normalisedModuleName)
    }

    override fun getModuleOverridesForPlayer(player: Player): Map<String, PlayerModuleOverride> =
        ModuleLoader.getConfigurableModuleNames()
            .associateWith { PlayerModuleOverrides.getOverride(player, it) }
            .filter { (_, override) -> override != PlayerModuleOverride.DEFAULT }

    override fun setModuleOverridesForPlayer(player: Player, overrides: Map<String, PlayerModuleOverride>) {
        val validatedOverrides = validateModuleOverrides(overrides)
        val changes = validatedOverrides
            .mapNotNull { (moduleName, override) ->
                val previousOverride = PlayerModuleOverrides.getOverride(player, moduleName)
                if (previousOverride == override) null else ModuleOverrideChange(moduleName, previousOverride, override)
            }
            .sortedBy { it.moduleName }

        if (changes.isEmpty()) {
            return
        }

        changes.forEach { (moduleName, _, override) ->
            if (override == PlayerModuleOverride.DEFAULT) {
                PlayerModuleOverrides.clearOverride(player, moduleName)
            } else {
                PlayerModuleOverrides.setOverride(player, moduleName, override)
            }
        }
        changes.forEach { (moduleName, previousOverride, newOverride) ->
            fireModuleOverrideChangeEvent(player, moduleName, previousOverride, newOverride)
        }
        notifyPlayerStateChanged(player)
    }

    override fun isModuleEnabledForPlayer(player: Player, moduleName: String): Boolean =
        getConfigurableModule(moduleName).isEnabled(player)

    override fun hasAnyOverrideForPlayer(player: Player): Boolean =
        ModuleLoader.getConfigurableModuleNames()
            .any { PlayerModuleOverrides.getOverride(player, it) != PlayerModuleOverride.DEFAULT }

    override fun getModuleNames(): Set<String> = ModuleLoader.getConfigurableModuleNames()

    private fun validateModesetForPlayer(player: Player, modesetName: String) {
        if (!Config.getModesets().containsKey(modesetName)) {
            throw IllegalArgumentException("Unknown modeset: $modesetName")
        }
        if (!Config.getAllowedModesets(player.world).contains(modesetName)) {
            throw IllegalArgumentException("Modeset is not allowed in the player's current world: $modesetName")
        }
    }

    private fun getConfigurableModule(moduleName: String): OCMModule =
        ModuleLoader.getConfigurableModule(moduleName)
            ?: throw IllegalArgumentException("Unknown or non-configurable module: $moduleName")

    private fun validateModuleName(moduleName: String): String {
        val normalisedModuleName = ModuleLoader.normaliseModuleName(moduleName)
        getConfigurableModule(normalisedModuleName)
        return normalisedModuleName
    }

    private fun validateModuleOverrides(overrides: Map<String, PlayerModuleOverride>): List<Pair<String, PlayerModuleOverride>> {
        val seenModuleNames = mutableSetOf<String>()
        return (overrides as Map<*, *>).map { (moduleName, override) ->
            val validatedModuleName = moduleName as? String
                ?: throw IllegalArgumentException("Module override name must not be null")
            val validatedOverride = override as? PlayerModuleOverride
                ?: throw IllegalArgumentException("Module override value for $validatedModuleName must not be null")
            val normalisedModuleName = validateModuleName(validatedModuleName)
            if (!seenModuleNames.add(normalisedModuleName)) {
                throw IllegalArgumentException("Duplicate module override name: $normalisedModuleName")
            }
            normalisedModuleName to validatedOverride
        }
    }

    private fun setSingleModuleOverride(player: Player, moduleName: String, newOverride: PlayerModuleOverride) {
        val normalisedModuleName = validateModuleName(moduleName)
        val previousOverride = PlayerModuleOverrides.getOverride(player, normalisedModuleName)
        if (previousOverride == newOverride) {
            return
        }
        PlayerModuleOverrides.setOverride(player, normalisedModuleName, newOverride)
        fireModuleOverrideChangeEvent(player, normalisedModuleName, previousOverride, newOverride)
        notifyPlayerStateChanged(player)
    }

    private fun fireModesetChangeEvent(
        player: Player,
        previousModeset: String?,
        newModeset: String?,
        reason: PlayerModesetChangeEvent.Reason,
    ) {
        Bukkit.getPluginManager().callEvent(
            PlayerModesetChangeEvent(player, player.world, previousModeset, newModeset, reason),
        )
    }

    private fun fireModuleOverrideChangeEvent(
        player: Player,
        moduleName: String,
        previousOverride: PlayerModuleOverride,
        newOverride: PlayerModuleOverride,
    ) {
        Bukkit.getPluginManager().callEvent(
            PlayerModuleOverrideChangeEvent(player, moduleName, previousOverride, newOverride),
        )
    }

    private fun notifyPlayerStateChanged(player: Player) {
        if (Bukkit.isPrimaryThread()) {
            ModuleLoader.notifyPlayerStateChanged(player)
            return
        }
        Bukkit.getScheduler().runTask(plugin, Runnable { ModuleLoader.notifyPlayerStateChanged(player) })
    }

    private data class ModuleOverrideChange(
        val moduleName: String,
        val previousOverride: PlayerModuleOverride,
        val newOverride: PlayerModuleOverride,
    )
}
