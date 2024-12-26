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
import java.util.*
import java.util.stream.Collectors

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
        val completions: MutableList<String> = ArrayList()

        if (args.size < 2) {
            completions.addAll(
                Arrays.stream(Subcommand.entries.toTypedArray())
                    .filter { arg: Subcommand -> arg.toString().startsWith(args[0]) }
                    .filter { arg: Subcommand -> OCMCommandHandler.checkPermissions(sender, arg) }
                    .map { obj: Subcommand -> obj.toString() }.collect(Collectors.toList())
            )
        } else if (args[0].equals(Subcommand.mode.toString(), ignoreCase = true)) {
            if (args.size < 3) {
                if (sender.hasPermission("oldcombatmechanics.mode.others")
                    || sender.hasPermission("oldcombatmechanics.mode.own")
                ) {
                    if (sender is Player) { // Get the modesets allowed in the world player is in
                        val world = sender.world
                        completions.addAll(
                            Config.worlds // If world not in config, all modesets allowed
                                .getOrDefault(world.uid, Config.getModesets().keys)
                                .stream()
                                .filter { ms: String -> ms.startsWith(args[1]) }
                                .collect(Collectors.toList()))
                    } else {
                        completions.addAll(
                            Config.getModesets().keys.stream()
                                .filter { ms: String -> ms.startsWith(args[1]) }
                                .collect(Collectors.toList()))
                    }
                }
            } else if (sender.hasPermission("oldcombatmechanics.mode.others")) {
                completions.addAll(
                    Bukkit.getOnlinePlayers().stream()
                        .map { obj: Player -> obj.name }
                        .filter { arg: String -> arg.startsWith(args[2]) }
                        .collect(Collectors.toList()))
            }
        }

        return completions
    }
}
