/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.api;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fired after a player's stored modeset changes and before OldCombatMechanics reapplies module state.
 */
public class PlayerModesetChangeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final World world;
    private final String previousModeset;
    private final String newModeset;
    private final Reason reason;

    public PlayerModesetChangeEvent(
            @NotNull Player player,
            @NotNull World world,
            @Nullable String previousModeset,
            @Nullable String newModeset,
            @NotNull Reason reason
    ) {
        this.player = player;
        this.world = world;
        this.previousModeset = previousModeset;
        this.newModeset = newModeset;
        this.reason = reason;
    }

    @NotNull
    public Player getPlayer() {
        return player;
    }

    @NotNull
    public World getWorld() {
        return world;
    }

    @Nullable
    public String getPreviousModeset() {
        return previousModeset;
    }

    @Nullable
    public String getNewModeset() {
        return newModeset;
    }

    @NotNull
    public Reason getReason() {
        return reason;
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

    public enum Reason {
        API,
        COMMAND,
        WORLD_CHANGE,
        JOIN
    }
}
