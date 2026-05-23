/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.api

import org.bukkit.entity.Player
import org.bukkit.World

/**
 * Java-facing public API for managing per-player module overrides.
 *
 * Overrides are online-session-only, in-memory state for currently online players.
 * They are cleared when the player quits, when OldCombatMechanics is disabled, or
 * when a plugin explicitly clears them through this API.
 *
 * API methods must be called from the Bukkit main server thread. The current
 * contract does not guarantee async-safe access to Bukkit [Player] state,
 * module state-change notifications, or future implementation details.
 *
 * Overrides are shared runtime state. If multiple plugins set an override for
 * the same player and module, the last write wins. Any plugin that clears that
 * override clears the same shared state for that player and module.
 *
 * Only configurable modules are valid inputs for methods that accept a module
 * name. Internal or otherwise non-configurable modules are rejected with
 * [IllegalArgumentException] and are not returned by [getModuleNames].
 *
 * Effective module state is resolved by checking per-player overrides first,
 * then configured disabled modules, configured always-enabled modules, and
 * finally the player's modeset and world configuration.
 *
 * Keep this surface friendly to Java callers. Future public API additions should use
 * simple JVM-visible types and should avoid Kotlin-only shapes such as suspend functions,
 * default parameters, function types, sealed or value classes, and companion-only access
 * patterns.
 *
 * Obtain an instance from Bukkit's services manager and always null-check the
 * registration before using it. The service is only available while
 * OldCombatMechanics is loaded and enabled:
 * ```
 * RegisteredServiceProvider<OldCombatMechanicsAPI> registration = Bukkit.getServicesManager()
 *     .getRegistration(OldCombatMechanicsAPI.class);
 * if (registration == null) {
 *     return;
 * }
 * OldCombatMechanicsAPI api = registration.getProvider();
 * ```
 */
interface OldCombatMechanicsAPI {

    /**
     * Returns all configured modeset names in config iteration order.
     */
    fun getModesetNames(): Set<String>

    /**
     * Returns the modesets allowed in [world] as an unmodifiable defensive copy.
     *
     * Iteration order follows the configured world list when present, the
     * `worlds.__default__` list for worlds without their own list, or the
     * configured modeset order when no world list applies.
     */
    fun getAllowedModesets(world: World): Set<String>

    /**
     * Returns the stored modeset for [player] in their current world, or null
     * when no player-specific modeset has been stored yet.
     */
    fun getModesetForPlayer(player: Player): String?

    /**
     * Stores [modesetName] for [player] in their current world.
     *
     * The input name is normalised with [java.util.Locale.ROOT] before
     * validation and storage. Unknown modesets, or modesets not allowed in the
     * player's current world, are rejected.
     *
     * @throws IllegalArgumentException if [modesetName] is unknown or disallowed.
     */
    fun setModesetForPlayer(player: Player, modesetName: String)

    /**
     * Forces configurable [moduleName] on for online [player]. The override is
     * online-session-only and is cleared when explicitly cleared, when [player]
     * quits, or when OldCombatMechanics is disabled.
     *
     * @throws IllegalArgumentException if [moduleName] is unknown or non-configurable.
     */
    fun forceEnableModuleForPlayer(player: Player, moduleName: String)

    /**
     * Forces configurable [moduleName] off for online [player]. The override is
     * online-session-only and is cleared when explicitly cleared, when [player]
     * quits, or when OldCombatMechanics is disabled.
     *
     * @throws IllegalArgumentException if [moduleName] is unknown or non-configurable.
     */
    fun forceDisableModuleForPlayer(player: Player, moduleName: String)

    /**
     * Clears the override for configurable [moduleName] for [player], reverting
     * to configured module behaviour.
     * No-op if no override is set.
     *
     * @throws IllegalArgumentException if [moduleName] is unknown or non-configurable.
     */
    fun clearModuleOverrideForPlayer(player: Player, moduleName: String)

    /**
     * Clears all module overrides for [player], reverting to configured module behaviour.
     * No-op if no overrides are set.
     */
    fun clearAllModuleOverridesForPlayer(player: Player)

    /**
     * Returns the current override state for configurable [moduleName] for [player].
     * Returns [PlayerModuleOverride.DEFAULT] if no override is set.
     *
     * @throws IllegalArgumentException if [moduleName] is unknown or non-configurable.
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
     * All entries are validated before any override state changes are applied.
     *
     * @throws IllegalArgumentException if any module name is unknown, non-configurable, or null,
     * or if any override value is null.
     */
    fun setModuleOverridesForPlayer(player: Player, overrides: Map<String, PlayerModuleOverride>)

    /**
     * Returns whether configurable [moduleName] is effectively enabled for [player],
     * accounting for configured rules and any per-player override.
     *
     * @throws IllegalArgumentException if [moduleName] is unknown or non-configurable.
     */
    fun isModuleEnabledForPlayer(player: Player, moduleName: String): Boolean

    /**
     * Returns whether [player] has any non-default override set.
     */
    fun hasAnyOverrideForPlayer(player: Player): Boolean

    /**
     * Returns the names of all configurable modules that support per-player overrides.
     */
    fun getModuleNames(): Set<String>
}
