/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics.utilities.storage;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.module.OCMModule;
import kernitus.plugin.OldCombatMechanics.utilities.Config;
import kernitus.plugin.OldCombatMechanics.utilities.Messenger;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.util.Set;
import java.util.UUID;

/**
 * Listens to players changing world / spawning etc.
 * and updates modeset accordingly
 */
public class ModesetListener extends OCMModule {

    public ModesetListener(OCMMain plugin) {
        super(plugin, "modeset-listener");
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        final Player player = event.getPlayer();
        final UUID playerId = player.getUniqueId();
        final PlayerData playerData = PlayerStorage.getPlayerData(playerId);
        final String modesetFromName = playerData.getModesetForWorld(event.getFrom().getUID());
        updateModeset(player, player.getWorld().getUID(), modesetFromName);
    }

    private static void updateModeset(Player player, UUID worldId, String modesetFromName) {
        final UUID playerId = player.getUniqueId();
        final PlayerData playerData = PlayerStorage.getPlayerData(playerId);
        final String originalModeset = playerData.getModesetForWorld(worldId);
        String modesetName = playerData.getModesetForWorld(worldId);

        // Get modesets allowed in to world
        Set<String> allowedModesets = Config.getWorlds().get(worldId);
        if (allowedModesets == null || allowedModesets.isEmpty())
            allowedModesets = Config.getModesets().keySet();

        // If they don't have a modeset in toWorld yet
        if (modesetName == null) {
            // Try to use modeset of world they are coming from
            if (modesetFromName != null && allowedModesets.contains(modesetFromName))
                modesetName = modesetFromName;
            else // Otherwise, if the from modeset is not allowed, use default for to world
                modesetName = allowedModesets.stream().findFirst().orElse(null);
        }

        // If the modeset changed, set and save
        if (originalModeset == null || !originalModeset.equals(modesetName)) {
            playerData.setModesetForWorld(worldId, modesetName);
            PlayerStorage.setPlayerData(playerId, playerData);
            PlayerStorage.scheduleSave();

            Messenger.send(player,
                    Config.getConfig().getString("mode-messages.mode-set",
                            "&4ERROR: &rmode-messages.mode-set string missing"),
                    modesetName
            );
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        updateModeset(player, player.getWorld().getUID(), null);
    }

    @EventHandler(ignoreCancelled = false)
    public void onWorldLoad(WorldLoadEvent event) {
        final World world = event.getWorld();
        Config.addWorld(world);
        Messenger.info("Loaded configured world " + world.getName());
    }

    @EventHandler(ignoreCancelled = false)
    public void onWorldUnload(WorldUnloadEvent event) {
        final World world = event.getWorld();
        Config.removeWorld(world);
        Messenger.info("Unloaded configured world " + world.getName());
    }
}
