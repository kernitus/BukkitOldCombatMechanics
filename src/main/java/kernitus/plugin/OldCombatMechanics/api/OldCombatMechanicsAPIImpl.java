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

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class OldCombatMechanicsAPIImpl implements OldCombatMechanicsAPI {

    private final OCMMain plugin;

    public OldCombatMechanicsAPIImpl(OCMMain plugin) {
        this.plugin = plugin;
    }

    @Override
    public void forceEnableModuleForPlayer(UUID playerId, String moduleName) {
        validateModuleName(moduleName);
        PlayerModuleOverrides.setOverride(playerId, moduleName, PlayerModuleOverride.FORCE_ENABLED);
        notifyPlayerStateChanged(playerId);
    }

    @Override
    public void forceDisableModuleForPlayer(UUID playerId, String moduleName) {
        validateModuleName(moduleName);
        PlayerModuleOverrides.setOverride(playerId, moduleName, PlayerModuleOverride.FORCE_DISABLED);
        notifyPlayerStateChanged(playerId);
    }

    @Override
    public void clearModuleOverrideForPlayer(UUID playerId, String moduleName) {
        validateModuleName(moduleName);
        if (PlayerModuleOverrides.clearOverride(playerId, moduleName)) {
            notifyPlayerStateChanged(playerId);
        }
    }

    @Override
    public void clearAllModuleOverridesForPlayer(UUID playerId) {
        if (PlayerModuleOverrides.clearAll(playerId)) {
            notifyPlayerStateChanged(playerId);
        }
    }

    @Override
    public PlayerModuleOverride getModuleOverrideForPlayer(UUID playerId, String moduleName) {
        validateModuleName(moduleName);
        return PlayerModuleOverrides.getOverride(playerId, moduleName);
    }

    @Override
    public Map<String, PlayerModuleOverride> getModuleOverridesForPlayer(UUID playerId) {
        return ModuleLoader.getConfigurableModuleNames().stream()
                .collect(Collectors.toMap(
                        name -> name,
                        name -> PlayerModuleOverrides.getOverride(playerId, name)
                ))
                .entrySet().stream()
                .filter(e -> e.getValue() != PlayerModuleOverride.DEFAULT)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public void setModuleOverridesForPlayer(UUID playerId, Map<String, PlayerModuleOverride> overrides) {
        overrides.keySet().forEach(this::validateModuleName);
        overrides.forEach((moduleName, override) -> {
            if (override == PlayerModuleOverride.DEFAULT) {
                PlayerModuleOverrides.clearOverride(playerId, moduleName);
            } else {
                PlayerModuleOverrides.setOverride(playerId, moduleName, override);
            }
        });
        notifyPlayerStateChanged(playerId);
    }

    @Override
    public boolean isModuleEnabledForPlayer(UUID playerId, String moduleName) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return getModuleOverrideForPlayer(playerId, moduleName) == PlayerModuleOverride.FORCE_ENABLED;
        }
        return getConfigurableModule(moduleName).isEnabled(player);
    }

    @Override
    public boolean hasAnyOverrideForPlayer(UUID playerId) {
        return ModuleLoader.getConfigurableModuleNames().stream()
                .anyMatch(name -> PlayerModuleOverrides.getOverride(playerId, name) != PlayerModuleOverride.DEFAULT);
    }

    @Override
    public Set<String> getModuleNames() {
        return ModuleLoader.getConfigurableModuleNames();
    }

    private OCMModule getConfigurableModule(String moduleName) {
        OCMModule module = ModuleLoader.getConfigurableModule(moduleName);
        if (module == null) {
            throw new IllegalArgumentException("Unknown or non-configurable module: " + moduleName);
        }
        return module;
    }

    private void validateModuleName(String moduleName) {
        getConfigurableModule(moduleName);
    }

    private void notifyPlayerStateChanged(UUID playerId) {
        Player onlinePlayer = Bukkit.getPlayer(playerId);
        if (onlinePlayer == null) return;

        if (Bukkit.isPrimaryThread()) {
            ModuleLoader.notifyPlayerStateChanged(onlinePlayer);
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> ModuleLoader.notifyPlayerStateChanged(onlinePlayer));
    }
}