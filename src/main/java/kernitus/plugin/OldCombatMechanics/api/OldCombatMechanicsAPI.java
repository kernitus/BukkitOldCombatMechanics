/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.api;

import org.bukkit.entity.Player;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Public API for managing per-player module overrides.
 *
 * Obtain an instance via:
 * <pre>
 * Bukkit.getServicesManager()
 *     .getRegistration(OldCombatMechanicsAPI.class)
 *     .getProvider();
 * </pre>
 */
public interface OldCombatMechanicsAPI {

    /**
     * Forces moduleName on for playerId, overriding the global config.
     * Persists until cleared.
     *
     * @throws IllegalArgumentException if moduleName is unknown.
     */
    void forceEnableModuleForPlayer(UUID playerId, String moduleName);

    /**
     * Forces moduleName on for player, overriding the global config.
     * Persists until cleared.
     *
     * @throws IllegalArgumentException if moduleName is unknown.
     */
    default void forceEnableModuleForPlayer(Player player, String moduleName) {
        forceEnableModuleForPlayer(player.getUniqueId(), moduleName);
    }

    /**
     * Forces moduleName off for playerId, overriding the global config.
     * Persists until cleared.
     *
     * @throws IllegalArgumentException if moduleName is unknown.
     */
    void forceDisableModuleForPlayer(UUID playerId, String moduleName);

    /**
     * Forces moduleName off for player, overriding the global config.
     * Persists until cleared.
     *
     * @throws IllegalArgumentException if moduleName is unknown.
     */
    default void forceDisableModuleForPlayer(Player player, String moduleName) {
        forceDisableModuleForPlayer(player.getUniqueId(), moduleName);
    }

    /**
     * Clears the override for moduleName for playerId, reverting to the global config.
     * No-op if no override is set.
     *
     * @throws IllegalArgumentException if moduleName is unknown.
     */
    void clearModuleOverrideForPlayer(UUID playerId, String moduleName);

    /**
     * Clears the override for moduleName for player, reverting to the global config.
     * No-op if no override is set.
     *
     * @throws IllegalArgumentException if moduleName is unknown.
     */
    default void clearModuleOverrideForPlayer(Player player, String moduleName) {
        clearModuleOverrideForPlayer(player.getUniqueId(), moduleName);
    }

    /**
     * Clears all module overrides for playerId, reverting to the global config.
     * No-op if no overrides are set.
     */
    void clearAllModuleOverridesForPlayer(UUID playerId);

    /**
     * Clears all module overrides for player, reverting to the global config.
     * No-op if no overrides are set.
     */
    default void clearAllModuleOverridesForPlayer(Player player) {
        clearAllModuleOverridesForPlayer(player.getUniqueId());
    }

    /**
     * Returns the current override state for moduleName for playerId.
     * Returns PlayerModuleOverride.DEFAULT if no override is set.
     *
     * @throws IllegalArgumentException if moduleName is unknown.
     */
    PlayerModuleOverride getModuleOverrideForPlayer(UUID playerId, String moduleName);

    /**
     * Returns the current override state for moduleName for player.
     * Returns PlayerModuleOverride.DEFAULT if no override is set.
     *
     * @throws IllegalArgumentException if moduleName is unknown.
     */
    default PlayerModuleOverride getModuleOverrideForPlayer(Player player, String moduleName) {
        return getModuleOverrideForPlayer(player.getUniqueId(), moduleName);
    }

    /**
     * Returns all active overrides for playerId as a map of module name to PlayerModuleOverride.
     * Only modules with a non-DEFAULT override are included.
     */
    Map<String, PlayerModuleOverride> getModuleOverridesForPlayer(UUID playerId);

    /**
     * Returns all active overrides for player as a map of module name to PlayerModuleOverride.
     * Only modules with a non-DEFAULT override are included.
     */
    default Map<String, PlayerModuleOverride> getModuleOverridesForPlayer(Player player) {
        return getModuleOverridesForPlayer(player.getUniqueId());
    }

    /**
     * Sets multiple module overrides for playerId at once.
     * Entries with PlayerModuleOverride.DEFAULT are treated as clears.
     *
     * @throws IllegalArgumentException if any module name is unknown.
     */
    void setModuleOverridesForPlayer(UUID playerId, Map<String, PlayerModuleOverride> overrides);

    /**
     * Sets multiple module overrides for player at once.
     * Entries with PlayerModuleOverride.DEFAULT are treated as clears.
     *
     * @throws IllegalArgumentException if any module name is unknown.
     */
    default void setModuleOverridesForPlayer(Player player, Map<String, PlayerModuleOverride> overrides) {
        setModuleOverridesForPlayer(player.getUniqueId(), overrides);
    }

    /**
     * Returns whether moduleName is effectively enabled for playerId,
     * accounting for both the global config and any per-player override.
     *
     * @throws IllegalArgumentException if moduleName is unknown.
     */
    boolean isModuleEnabledForPlayer(UUID playerId, String moduleName);

    /**
     * Returns whether moduleName is effectively enabled for player,
     * accounting for both the global config and any per-player override.
     *
     * @throws IllegalArgumentException if moduleName is unknown.
     */
    default boolean isModuleEnabledForPlayer(Player player, String moduleName) {
        return isModuleEnabledForPlayer(player.getUniqueId(), moduleName);
    }

    /**
     * Returns whether playerId has any non-default override set.
     */
    boolean hasAnyOverrideForPlayer(UUID playerId);

    /**
     * Returns whether player has any non-default override set.
     */
    default boolean hasAnyOverrideForPlayer(Player player) {
        return hasAnyOverrideForPlayer(player.getUniqueId());
    }

    /**
     * Returns the names of all modules that support per-player overrides.
     */
    Set<String> getModuleNames();
}