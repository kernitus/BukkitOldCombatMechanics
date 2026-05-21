/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.api

import kernitus.plugin.OldCombatMechanics.ModuleLoader
import kernitus.plugin.OldCombatMechanics.OCMMain
import kernitus.plugin.OldCombatMechanics.module.OCMModule
import kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerModuleOverrides
import org.bukkit.Bukkit
import org.bukkit.entity.Player

class OldCombatMechanicsAPIImpl(private val plugin: OCMMain) : OldCombatMechanicsAPI {

    override fun forceEnableModuleForPlayer(player: Player, moduleName: String) {
        validateModuleName(moduleName)
        PlayerModuleOverrides.setOverride(player, moduleName, PlayerModuleOverride.FORCE_ENABLED)
        notifyPlayerStateChanged(player)
    }

    override fun forceDisableModuleForPlayer(player: Player, moduleName: String) {
        validateModuleName(moduleName)
        PlayerModuleOverrides.setOverride(player, moduleName, PlayerModuleOverride.FORCE_DISABLED)
        notifyPlayerStateChanged(player)
    }

    override fun clearModuleOverrideForPlayer(player: Player, moduleName: String) {
        validateModuleName(moduleName)
        if (PlayerModuleOverrides.clearOverride(player, moduleName)) {
            notifyPlayerStateChanged(player)
        }
    }

    override fun clearAllModuleOverridesForPlayer(player: Player) {
        if (PlayerModuleOverrides.clearAll(player)) {
            notifyPlayerStateChanged(player)
        }
    }

    override fun getModuleOverrideForPlayer(player: Player, moduleName: String): PlayerModuleOverride {
        validateModuleName(moduleName)
        return PlayerModuleOverrides.getOverride(player, moduleName)
    }

    override fun getModuleOverridesForPlayer(player: Player): Map<String, PlayerModuleOverride> =
        ModuleLoader.getConfigurableModuleNames()
            .associateWith { PlayerModuleOverrides.getOverride(player, it) }
            .filter { (_, override) -> override != PlayerModuleOverride.DEFAULT }

    override fun setModuleOverridesForPlayer(player: Player, overrides: Map<String, PlayerModuleOverride>) {
        val validatedOverrides = validateModuleOverrides(overrides)
        validatedOverrides.forEach { (moduleName, override) ->
            if (override == PlayerModuleOverride.DEFAULT) {
                PlayerModuleOverrides.clearOverride(player, moduleName)
            } else {
                PlayerModuleOverrides.setOverride(player, moduleName, override)
            }
        }
        notifyPlayerStateChanged(player)
    }

    override fun isModuleEnabledForPlayer(player: Player, moduleName: String): Boolean =
        getConfigurableModule(moduleName).isEnabled(player)

    override fun hasAnyOverrideForPlayer(player: Player): Boolean =
        ModuleLoader.getConfigurableModuleNames()
            .any { PlayerModuleOverrides.getOverride(player, it) != PlayerModuleOverride.DEFAULT }

    override fun getModuleNames(): Set<String> = ModuleLoader.getConfigurableModuleNames()

    private fun getConfigurableModule(moduleName: String): OCMModule =
        ModuleLoader.getConfigurableModule(moduleName)
            ?: throw IllegalArgumentException("Unknown or non-configurable module: $moduleName")

    private fun validateModuleName(moduleName: String) {
        getConfigurableModule(moduleName)
    }

    private fun validateModuleOverrides(overrides: Map<String, PlayerModuleOverride>): List<Pair<String, PlayerModuleOverride>> =
        (overrides as Map<*, *>).map { (moduleName, override) ->
            val validatedModuleName = moduleName as? String
                ?: throw IllegalArgumentException("Module override name must not be null")
            val validatedOverride = override as? PlayerModuleOverride
                ?: throw IllegalArgumentException("Module override value for $validatedModuleName must not be null")
            validateModuleName(validatedModuleName)
            validatedModuleName to validatedOverride
        }

    private fun notifyPlayerStateChanged(player: Player) {
        if (Bukkit.isPrimaryThread()) {
            ModuleLoader.notifyPlayerStateChanged(player)
            return
        }
        Bukkit.getScheduler().runTask(plugin, Runnable { ModuleLoader.notifyPlayerStateChanged(player) })
    }
}
