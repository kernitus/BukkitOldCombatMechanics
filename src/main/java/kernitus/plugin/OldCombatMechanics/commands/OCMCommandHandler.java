/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.commands;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.module.ModuleAttackCooldown;
import kernitus.plugin.OldCombatMechanics.utilities.Config;
import kernitus.plugin.OldCombatMechanics.utilities.Messenger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class OCMCommandHandler implements CommandExecutor {
    private static final String NO_PERMISSION = "&cYou need the permission '%s' to do that!";

    private final OCMMain plugin;
    private final File pluginFile;

    enum Subcommand {reload, toggle, enable, disable}

    public OCMCommandHandler(OCMMain instance, File pluginFile) {
        this.plugin = instance;
        this.pluginFile = pluginFile;
    }

    private void help(OCMMain plugin, CommandSender sender) {
        final PluginDescriptionFile description = plugin.getDescription();

        Messenger.send(sender, ChatColor.DARK_GRAY + Messenger.HORIZONTAL_BAR);
        Messenger.send(sender, "&6&lOldCombatMechanics&e by &ckernitus&e and &cRayzr522&e version &6%s", description.getVersion());

        if (checkPermissions(sender, Subcommand.reload))
            Messenger.send(sender, "&eYou can use &c/ocm reload&e to reload the config file");
        if (checkPermissions(sender, Subcommand.toggle))
            Messenger.send(sender, "&eYou can use &c/ocm toggle [player] [on/off] &e to turn attack cooldown on/off");
        if (checkPermissions(sender, Subcommand.enable) || checkPermissions(sender, Subcommand.disable))
            Messenger.send(sender, "&eYou can use &c/ocm <enable/disable> [world] &e to toggle cooldown for the server or world");

        Messenger.send(sender, ChatColor.DARK_GRAY + Messenger.HORIZONTAL_BAR);


    }

    private void reload(CommandSender sender) {
        Config.reload();
        Messenger.send(sender, "&6&lOldCombatMechanics&e config file reloaded");
    }

    private void toggle(OCMMain plugin, CommandSender sender, String[] args) {
        final FileConfiguration config = plugin.getConfig();

        Player player = null;
        ModuleAttackCooldown.PVPMode mode = null;

        if (args.length >= 2) {
            player = Bukkit.getPlayer(args[1]);

            if (args.length >= 3) {
                if (args[2].equalsIgnoreCase("on")) mode = ModuleAttackCooldown.PVPMode.NEW_PVP;
                else if (args[2].equalsIgnoreCase("off")) mode = ModuleAttackCooldown.PVPMode.OLD_PVP;
            }
        }

        if (player == null && sender instanceof Player) player = (Player) sender;
        if (player == null) {
            final String message = config.getString("disable-attack-cooldown.message-usage",
                    "&4ERROR: &rdisable-attack-cooldown.message-usage string missing");
            Messenger.sendNormalMessage(sender, message);
            return;
        }

        if (mode == null) {
            ModuleAttackCooldown.PVPMode oldMode = ModuleAttackCooldown.PVPMode.getModeForPlayer(player);
            mode = oldMode == ModuleAttackCooldown.PVPMode.NEW_PVP ?
                    ModuleAttackCooldown.PVPMode.OLD_PVP : ModuleAttackCooldown.PVPMode.NEW_PVP;
        }

        final String message = config.getString("disable-attack-cooldown.message-" +
                                (mode == ModuleAttackCooldown.PVPMode.NEW_PVP ? "enabled" : "disabled"),
                        "&4ERROR: &rdisable-attack-cooldown.message strings missing")
                .replaceAll("%player%", player.getDisplayName());

        ModuleAttackCooldown.setAttackSpeed(player, mode);
        Messenger.sendNormalMessage(sender, message);
    }

    /*
    private void test(OCMMain plugin, CommandSender sender) {
        final Location location = sender instanceof Player ?
                ((Player) sender).getLocation() :
                sender.getServer().getWorlds().get(0).getSpawnLocation();

        new InGameTester(plugin).performTests(sender, location);
    }
     */

    private void wideToggle(CommandSender sender, String[] args, ModuleAttackCooldown.PVPMode mode) {
        final Set<World> worlds = args.length > 1 ?
                Arrays.asList(args).subList(1, args.length).stream().map(Bukkit::getWorld).filter(Objects::nonNull).collect(Collectors.toSet())
                : new HashSet<>(Bukkit.getWorlds());

        worlds.stream().map(World::getPlayers).forEach(players -> players.forEach(
                player -> ModuleAttackCooldown.setAttackSpeed(player, mode)));

        // Do not use method reference to get world name because with 1.18 method was moved from World to WorldInfo
        final String message = (mode == ModuleAttackCooldown.PVPMode.NEW_PVP ? "Enabled" : "Disabled") + " cooldown for worlds: " +
                worlds.stream().map(w -> w.getName()).reduce((a, b) -> a + ", " + b).orElse("none!");
        Messenger.sendNormalMessage(sender, message);
    }

    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String[] args) {
        if (args.length < 1) {
            help(plugin, sender);
        } else {
            try {
                try {
                    final Subcommand subcommand = Subcommand.valueOf(args[0].toLowerCase(Locale.ROOT));
                    if (checkPermissions(sender, subcommand, true)) {
                        switch (subcommand) {
                            case reload:
                                reload(sender);
                                break;
                            case toggle:
                                toggle(plugin, sender, args);
                                break;
                            //case test: test(plugin, sender);
                            //    break;
                            case enable:
                                wideToggle(sender, args, ModuleAttackCooldown.PVPMode.NEW_PVP);
                                break;
                            case disable:
                                wideToggle(sender, args, ModuleAttackCooldown.PVPMode.OLD_PVP);
                                break;
                            default:
                                throw new CommandNotRecognisedException();
                        }
                    }
                } catch (IllegalArgumentException e) {
                    throw new CommandNotRecognisedException();
                }
            } catch (CommandNotRecognisedException e) {
                Messenger.sendNormalMessage(sender, "Subcommand not recognised!");
            }
        }
        return true;
    }

    private static class CommandNotRecognisedException extends IllegalArgumentException {
    }

    static boolean checkPermissions(CommandSender sender, Subcommand subcommand) {
        return checkPermissions(sender, subcommand, false);
    }

    static boolean checkPermissions(CommandSender sender, Subcommand subcommand, boolean sendMessage) {
        final boolean hasPermission = sender.hasPermission("oldcombatmechanics." + subcommand);
        if (sendMessage && !hasPermission)
            Messenger.send(sender, NO_PERMISSION, "oldcombatmechanics." + subcommand);
        return hasPermission;
    }
}