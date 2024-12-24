/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.commands;

import kernitus.plugin.OldCombatMechanics.utilities.Config;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static kernitus.plugin.OldCombatMechanics.commands.OCMCommandHandler.Subcommand;

/**
 * Provides tab completion for OCM commands
 */
public class OCMCommandCompleter implements TabCompleter {

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        final List<String> completions = new ArrayList<>();

        if (args.length < 2) {
            completions.addAll(Arrays.stream(Subcommand.values())
                    .filter(arg -> arg.toString().startsWith(args[0]))
                    .filter(arg -> OCMCommandHandler.checkPermissions(sender, arg))
                    .map(Enum::toString).collect(Collectors.toList()));
        } else if (args[0].equalsIgnoreCase(Subcommand.mode.toString())) {
            if (args.length < 3) {
                if (sender.hasPermission("oldcombatmechanics.mode.others")
                        || sender.hasPermission("oldcombatmechanics.mode.own")
                ) {
                    if (sender instanceof Player) { // Get the modesets allowed in the world player is in
                        final World world = ((Player) sender).getWorld();
                        completions.addAll(
                                Config.getWorlds()
                                        // If world not in config, all modesets allowed
                                        .getOrDefault(world.getUID(), Config.getModesets().keySet())
                                        .stream()
                                        .filter(ms -> ms.startsWith(args[1]))
                                        .collect(Collectors.toList()));
                    } else {
                        completions.addAll(Config.getModesets().keySet().stream()
                                .filter(ms -> ms.startsWith(args[1]))
                                .collect(Collectors.toList()));
                    }
                }
            } else if (sender.hasPermission("oldcombatmechanics.mode.others")) {
                completions.addAll(Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(arg -> arg.startsWith(args[2]))
                        .collect(Collectors.toList()));
            }

        }

        return completions;
    }
}
