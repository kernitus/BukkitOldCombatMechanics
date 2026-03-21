/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.api;

import kernitus.plugin.OldCombatMechanics.ModuleLoader;
import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.module.OCMModule;
import kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerModuleOverrides;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class OldCombatMechanicsAPIImpl implements OldCombatMechanicsAPI {

    private final OCMMain plugin;

    public OldCombatMechanicsAPIImpl(@NotNull OCMMain plugin) {
        this.plugin = plugin;
    }

    @Override
    public void forceEnableModuleForPlayer(@NotNull UUID playerId, @NotNull String moduleName) {
        validateModuleName(moduleName);
        PlayerModuleOverrides.setOverride(playerId, moduleName, PlayerModuleOverride.FORCE_ENABLED);
        notifyPlayerStateChanged(playerId);
    }

    @Override
    public void forceDisableModuleForPlayer(@NotNull UUID playerId, @NotNull String moduleName) {
        validateModuleName(moduleName);
        PlayerModuleOverrides.setOverride(playerId, moduleName, PlayerModuleOverride.FORCE_DISABLED);
        notifyPlayerStateChanged(playerId);
    }

    @Override
    public void clearModuleOverrideForPlayer(@NotNull UUID playerId, @NotNull String moduleName) {
        validateModuleName(moduleName);
        if (PlayerModuleOverrides.clearOverride(playerId, moduleName)) {
            notifyPlayerStateChanged(playerId);
        }
    }

    @Override
    public void clearAllModuleOverridesForPlayer(@NotNull UUID playerId) {
        if (PlayerModuleOverrides.clearAll(playerId)) {
            notifyPlayerStateChanged(playerId);
        }
    }

    @Override
    public @NotNull PlayerModuleOverride getModuleOverrideForPlayer(@NotNull UUID playerId, @NotNull String moduleName) {
        validateModuleName(moduleName);
        return PlayerModuleOverrides.getOverride(playerId, moduleName);
    }

    @Override
    public boolean isModuleEnabledForPlayer(@NotNull Player player, @NotNull String moduleName) {
        return Objects.requireNonNull(getConfigurableModule(moduleName)).isEnabled(player);
    }

    @Override
    public @NotNull Set<String> getConfigurableModules() {
        return ModuleLoader.getConfigurableModuleNames();
    }

    private @NotNull OCMModule getConfigurableModule(@NotNull String moduleName) {
        final OCMModule module = ModuleLoader.getConfigurableModule(moduleName);
        if (module == null) {
            throw new IllegalArgumentException("Unknown or non-configurable module: " + moduleName);
        }
        return module;
    }

    private void validateModuleName(@NotNull String moduleName) {
        getConfigurableModule(moduleName);
    }

    private void notifyPlayerStateChanged(@NotNull UUID playerId) {
        final Player onlinePlayer = Bukkit.getPlayer(playerId);
        if (onlinePlayer == null) return;

        if (Bukkit.isPrimaryThread()) {
            ModuleLoader.notifyPlayerStateChanged(onlinePlayer);
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> ModuleLoader.notifyPlayerStateChanged(onlinePlayer));
    }
}
