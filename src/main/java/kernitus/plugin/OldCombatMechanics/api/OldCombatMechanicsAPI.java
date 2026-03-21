/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.api;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.UUID;

/**
 * Public API for managing per-player module overrides.
 */
public interface OldCombatMechanicsAPI {

    void forceEnableModuleForPlayer(@NotNull UUID playerId, @NotNull String moduleName);

    void forceDisableModuleForPlayer(@NotNull UUID playerId, @NotNull String moduleName);

    void clearModuleOverrideForPlayer(@NotNull UUID playerId, @NotNull String moduleName);

    void clearAllModuleOverridesForPlayer(@NotNull UUID playerId);

    @NotNull
    PlayerModuleOverride getModuleOverrideForPlayer(@NotNull UUID playerId, @NotNull String moduleName);

    boolean isModuleEnabledForPlayer(@NotNull Player player, @NotNull String moduleName);

    @NotNull
    Set<String> getConfigurableModules();
}
