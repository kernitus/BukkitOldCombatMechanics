/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.updater

import kernitus.plugin.OldCombatMechanics.OCMMain
import kernitus.plugin.OldCombatMechanics.UpdateChecker
import kernitus.plugin.OldCombatMechanics.module.OCMModule
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.player.PlayerJoinEvent

class ModuleUpdateChecker(plugin: OCMMain?) : OCMModule(plugin, "update-checker") {
    @EventHandler
    fun onPlayerLogin(e: PlayerJoinEvent) {
        val player = e.player
        if (player.hasPermission("OldCombatMechanics.notify")) Bukkit.getScheduler().runTaskLaterAsynchronously(
            plugin,
            Runnable { UpdateChecker(plugin).performUpdate() }, 20L
        )
    }
}
