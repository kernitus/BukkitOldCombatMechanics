/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities.storage;

import kernitus.plugin.OldCombatMechanics.ModuleLoader;
import kernitus.plugin.OldCombatMechanics.api.PlayerModuleOverride;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory per-player module override store.
 */
public final class PlayerModuleOverrides {

    private static final Map<UUID, Map<String, PlayerModuleOverride>> overridesByPlayer = new ConcurrentHashMap<>();

    private PlayerModuleOverrides() {
    }

    public static void setOverride(@NotNull UUID playerId, @NotNull String moduleName, @NotNull PlayerModuleOverride state) {
        final String normalised = ModuleLoader.normaliseModuleName(moduleName);
        if (state == PlayerModuleOverride.DEFAULT) {
            clearOverride(playerId, normalised);
            return;
        }

        overridesByPlayer
                .computeIfAbsent(playerId, ignored -> new ConcurrentHashMap<>())
                .put(normalised, state);
    }

    public static @NotNull PlayerModuleOverride getOverride(@NotNull UUID playerId, @NotNull String moduleName) {
        final Map<String, PlayerModuleOverride> perPlayer = overridesByPlayer.get(playerId);
        if (perPlayer == null) return PlayerModuleOverride.DEFAULT;

        return perPlayer.getOrDefault(ModuleLoader.normaliseModuleName(moduleName), PlayerModuleOverride.DEFAULT);
    }

    public static boolean clearOverride(@NotNull UUID playerId, @NotNull String moduleName) {
        final Map<String, PlayerModuleOverride> perPlayer = overridesByPlayer.get(playerId);
        if (perPlayer == null) return false;

        final String normalised = ModuleLoader.normaliseModuleName(moduleName);
        final PlayerModuleOverride removed = perPlayer.remove(normalised);
        if (perPlayer.isEmpty()) {
            overridesByPlayer.remove(playerId);
        }
        return removed != null;
    }

    public static boolean clearAll(@NotNull UUID playerId) {
        return overridesByPlayer.remove(playerId) != null;
    }

    public static void clearAll() {
        overridesByPlayer.clear();
    }
}
