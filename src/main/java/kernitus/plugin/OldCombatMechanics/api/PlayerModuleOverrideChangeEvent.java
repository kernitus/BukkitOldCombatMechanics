/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired after a player's stored module override changes and before OldCombatMechanics reapplies module state.
 */
public class PlayerModuleOverrideChangeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String moduleName;
    private final PlayerModuleOverride previousOverride;
    private final PlayerModuleOverride newOverride;

    public PlayerModuleOverrideChangeEvent(
            @NotNull Player player,
            @NotNull String moduleName,
            @NotNull PlayerModuleOverride previousOverride,
            @NotNull PlayerModuleOverride newOverride
    ) {
        this.player = player;
        this.moduleName = moduleName;
        this.previousOverride = previousOverride;
        this.newOverride = newOverride;
    }

    @NotNull
    public Player getPlayer() {
        return player;
    }

    @NotNull
    public String getModuleName() {
        return moduleName;
    }

    @NotNull
    public PlayerModuleOverride getPreviousOverride() {
        return previousOverride;
    }

    @NotNull
    public PlayerModuleOverride getNewOverride() {
        return newOverride;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
