/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.commands

import kernitus.plugin.OldCombatMechanics.commands.OCMCommandHandler.Subcommand
import kernitus.plugin.OldCombatMechanics.utilities.Config
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

/**
 * Provides tab completion for OCM commands
 */
class OCMCommandCompleter : TabCompleter {
    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<String>
    ): List<String> {
        if (args.size < 2) {
            return Subcommand.entries
                .filter { it.name.startsWith(args[0], ignoreCase = true) }
                .filter { OCMCommandHandler.checkPermissions(sender, it) }
                .map { it.name }
        }

        if (args[0].equals(Subcommand.mode.name, ignoreCase = true)) {
            if (args.size < 3) {
                if (sender.hasPermission("oldcombatmechanics.mode.others") ||
                    sender.hasPermission("oldcombatmechanics.mode.own")
                ) {
                    val modesets = if (sender is Player) {
                        // Get the modesets allowed in the world player is in
                        Config.worlds.getOrDefault(sender.world.uid, Config.modesets.keys)
                    } else {
                        Config.modesets.keys
                    }
                    return modesets.filter { it.startsWith(args[1], ignoreCase = true) }
                }
            } else if (sender.hasPermission("oldcombatmechanics.mode.others")) {
                return Bukkit.getOnlinePlayers()
                    .map { it.name }
                    .filter { it.startsWith(args[2], ignoreCase = true) }
            }
        }

        return emptyList()
    }
}
