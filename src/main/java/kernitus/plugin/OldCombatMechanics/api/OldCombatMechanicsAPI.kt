/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.api

import org.bukkit.entity.Player

/**
 * Public API for managing per-player module overrides.
 *
 * Obtain an instance via:
 * ```
 * Bukkit.getServicesManager()
 *     .getRegistration(OldCombatMechanicsAPI::class.java)
 *     .provider
 * ```
 */
interface OldCombatMechanicsAPI {

    /**
     * Forces [moduleName] on for [player], overriding the global config.
     * Persists until cleared.
     *
     * @throws IllegalArgumentException if [moduleName] is unknown.
     */
    fun forceEnableModuleForPlayer(player: Player, moduleName: String)

    /**
     * Forces [moduleName] off for [player], overriding the global config.
     * Persists until cleared.
     *
     * @throws IllegalArgumentException if [moduleName] is unknown.
     */
    fun forceDisableModuleForPlayer(player: Player, moduleName: String)

    /**
     * Clears the override for [moduleName] for [player], reverting to the global config.
     * No-op if no override is set.
     *
     * @throws IllegalArgumentException if [moduleName] is unknown.
     */
    fun clearModuleOverrideForPlayer(player: Player, moduleName: String)

    /**
     * Clears all module overrides for [player], reverting to the global config.
     * No-op if no overrides are set.
     */
    fun clearAllModuleOverridesForPlayer(player: Player)

    /**
     * Returns the current override state for [moduleName] for [player].
     * Returns [PlayerModuleOverride.DEFAULT] if no override is set.
     *
     * @throws IllegalArgumentException if [moduleName] is unknown.
     */
    fun getModuleOverrideForPlayer(player: Player, moduleName: String): PlayerModuleOverride

    /**
     * Returns all active overrides for [player] as a map of module name to [PlayerModuleOverride].
     * Only modules with a non-DEFAULT override are included.
     */
    fun getModuleOverridesForPlayer(player: Player): Map<String, PlayerModuleOverride>

    /**
     * Sets multiple module overrides for [player] at once.
     * Entries with [PlayerModuleOverride.DEFAULT] are treated as clears.
     *
     * @throws IllegalArgumentException if any module name is unknown.
     */
    fun setModuleOverridesForPlayer(player: Player, overrides: Map<String, PlayerModuleOverride>)

    /**
     * Returns whether [moduleName] is effectively enabled for [player],
     * accounting for both the global config and any per-player override.
     *
     * @throws IllegalArgumentException if [moduleName] is unknown.
     */
    fun isModuleEnabledForPlayer(player: Player, moduleName: String): Boolean

    /**
     * Returns whether [player] has any non-default override set.
     */
    fun hasAnyOverrideForPlayer(player: Player): Boolean

    /**
     * Returns the names of all modules that support per-player overrides.
     */
    fun getModuleNames(): Set<String>
}