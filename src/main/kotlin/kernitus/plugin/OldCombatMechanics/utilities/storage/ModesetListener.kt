/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities.storage

import kernitus.plugin.OldCombatMechanics.OCMMain
import kernitus.plugin.OldCombatMechanics.module.OCMModule
import kernitus.plugin.OldCombatMechanics.utilities.Config
import kernitus.plugin.OldCombatMechanics.utilities.Messenger
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.world.WorldLoadEvent
import org.bukkit.event.world.WorldUnloadEvent
import java.util.*

/**
 * Listens to players changing world / spawning etc.
 * and updates modeset accordingly
 */
class ModesetListener(plugin: OCMMain) : OCMModule(plugin, "modeset-listener") {
    override fun isEnabled() = true

    override fun isEnabled(world: World) = true

    @EventHandler(priority = EventPriority.LOWEST)
    fun onPlayerChangedWorld(event: PlayerChangedWorldEvent) {
        val player = event.player
        val playerId = player.uniqueId
        val playerData = PlayerStorage.getPlayerData(playerId)
        val modesetFromName = playerData.getModesetForWorld(event.from.uid)
        updateModeset(player, player.world.uid, modesetFromName)
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        updateModeset(player, player.world.uid, null)
    }

    @EventHandler(ignoreCancelled = false)
    fun onWorldLoad(event: WorldLoadEvent) {
        val world = event.world
        Config.addWorld(world)
        Messenger.info("Loaded configured world " + world.name)
    }

    @EventHandler(ignoreCancelled = false)
    fun onWorldUnload(event: WorldUnloadEvent) {
        val world = event.world
        Config.removeWorld(world)
        Messenger.info("Unloaded configured world " + world.name)
    }

    companion object {
        private fun updateModeset(player: Player, worldId: UUID, modesetFromName: String?) {
            val playerId = player.uniqueId
            val playerData = PlayerStorage.getPlayerData(playerId)
            val originalModeset = playerData.getModesetForWorld(worldId)
            var modesetName = playerData.getModesetForWorld(worldId)

            // Get modesets allowed in to world
            var allowedModesets = Config.worlds[worldId]
            if (allowedModesets.isNullOrEmpty()) allowedModesets = Config.modesets.keys

            // If they don't have a modeset in toWorld yet
            if (modesetName == null) {
                modesetName = modesetFromName?.takeIf { allowedModesets.contains(it) } ?: allowedModesets.firstOrNull()
            }

            // If the modeset changed, set and save
            if (originalModeset == null || originalModeset != modesetName) {
                playerData.setModesetForWorld(worldId, modesetName)
                PlayerStorage.setPlayerData(playerId, playerData)
                PlayerStorage.scheduleSave()

                Messenger.send(
                    player,
                    Config.getConfig().getString("mode-messages.mode-set")
                        ?: "&4ERROR: &rmode-messages.mode-set string missing",
                    modesetName ?: "unknown"
                )
            }
        }
    }
}
