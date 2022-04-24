/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.commands;

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
import java.util.stream.Stream;

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
        } else {
            if (args[0].equalsIgnoreCase(Subcommand.test.toString())) {
                if (args.length < 4) {
                    completions.addAll(Bukkit.getOnlinePlayers().stream()
                            .filter(p -> {
                                if (args.length < 3) return true;
                                Player argPlayer = Bukkit.getPlayer(args[1]);
                                return argPlayer != null && argPlayer.getWorld().equals(p.getWorld());
                            })
                            .map(Player::getName)
                            .filter(arg -> arg.startsWith(args[args.length - 1])
                                    && (args.length < 3 || !arg.equalsIgnoreCase(args[1])))
                            .collect(Collectors.toList()));
                }
            } else if (args[0].equalsIgnoreCase(Subcommand.toggle.toString())) {
                if (args.length < 3) {
                    completions.addAll(Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(arg -> arg.startsWith(args[1]))
                            .collect(Collectors.toList()));
                } else {
                    completions.addAll(Stream.of("on", "off").filter(arg -> arg.startsWith(args[2])).collect(Collectors.toList()));
                }
            } else if (args[0].equalsIgnoreCase(Subcommand.enable.toString()) || args[0].equalsIgnoreCase(Subcommand.disable.toString())) {
                completions.addAll(Bukkit.getWorlds().stream()
                        .map(World::getName)
                        .filter(name -> !Arrays.asList(args).subList(1,args.length).contains(name))
                        .filter(arg -> arg.startsWith(args[args.length - 1]))
                        .collect(Collectors.toList()));
            }
        }

        return completions;
    }
}
