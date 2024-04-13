/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.commands;

import kernitus.plugin.OldCombatMechanics.ModuleLoader;
import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.Config;
import kernitus.plugin.OldCombatMechanics.utilities.Messenger;
import kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerData;
import kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class OCMCommandHandler implements CommandExecutor {
    private static final String NO_PERMISSION = "&cYou need the permission '%s' to do that!";

    private final OCMMain plugin;

    enum Subcommand {reload, mode }

    public OCMCommandHandler(OCMMain instance) {
        this.plugin = instance;
    }

    private void help(OCMMain plugin, CommandSender sender) {
        final PluginDescriptionFile description = plugin.getDescription();

        Messenger.sendNoPrefix(sender, ChatColor.DARK_GRAY + Messenger.HORIZONTAL_BAR);
        Messenger.sendNoPrefix(sender, "&6&lOldCombatMechanics&e by &ckernitus&e and &cRayzr522&e version &6%s", description.getVersion());

        if (checkPermissions(sender, Subcommand.reload))
            Messenger.sendNoPrefix(sender, "&eYou can use &c/ocm reload&e to reload the config file");
        if (checkPermissions(sender, Subcommand.mode))
            Messenger.sendNoPrefix(sender,
                    Config.getConfig().getString("mode-messages.message-usage",
                            "&4ERROR: &rmode-messages.message-usage string missing"));

        Messenger.sendNoPrefix(sender, ChatColor.DARK_GRAY + Messenger.HORIZONTAL_BAR);
    }

    private void reload(CommandSender sender) {
        Config.reload();
        Messenger.sendNoPrefix(sender, "&6&lOldCombatMechanics&e config file reloaded");
    }

    private void mode(CommandSender sender, String[] args) {
        if (args.length < 2) {
            if(sender instanceof Player) {
                final Player player = ((Player) sender);
                final PlayerData playerData = PlayerStorage.getPlayerData(player.getUniqueId());
                String modeName = playerData.getModesetForWorld(player.getWorld().getUID());
                if(modeName == null || modeName.isEmpty()) modeName = "unknown";

                Messenger.send(sender,
                        Config.getConfig().getString("mode-messages.mode-status",
                                "&4ERROR: &rmode-messages.mode-status string missing"),
                                modeName
                        );
            }
            Messenger.send(sender,
                    Config.getConfig().getString("mode-messages.message-usage",
                            "&4ERROR: &rmode-messages.message-usage string missing"));
            return;
        }

        final String modesetName = args[1].toLowerCase(Locale.ROOT);

        if (!Config.getModesets().containsKey(modesetName)) {
            Messenger.send(sender,
                    Config.getConfig().getString("mode-messages.invalid-modeset",
                            "&4ERROR: &rmode-messages.invalid-modeset string missing"));
            return;
        }

        Player player = null;
        if (args.length < 3) {
            if (sender instanceof Player) {
                if (sender.hasPermission("oldcombatmechanics.mode.own"))
                    player = (Player) sender;
            }
            else {
                Messenger.send(sender,
                        Config.getConfig().getString("mode-messages.invalid-player",
                                "&4ERROR: &rmode-messages.invalid-player string missing"));
                return;
            }
        } else if (sender.hasPermission("oldcombatmechanics.mode.others"))
            player = Bukkit.getPlayer(args[2]);

        if (player == null) {
            Messenger.send(sender,
                    Config.getConfig().getString("mode-messages.invalid-player",
                            "&4ERROR: &rmode-messages.invalid-player string missing"));
            return;
        }

        final UUID worldId = player.getWorld().getUID();
        final Set<String> worldModesets = Config.getWorlds().get(worldId);

        // If modesets null it means not configured, so all are allowed
        if(worldModesets != null && !worldModesets.contains(modesetName)){ // Modeset not allowed in current world
            Messenger.send(sender,
                    Config.getConfig().getString("mode-messages.invalid-modeset",
                            "&4ERROR: &rmode-messages.invalid-modeset string missing"));
            return;
        }

        final PlayerData playerData = PlayerStorage.getPlayerData(player.getUniqueId());
        playerData.setModesetForWorld(worldId, modesetName);
        PlayerStorage.setPlayerData(player.getUniqueId(), playerData);
        PlayerStorage.scheduleSave();

        Messenger.send(sender,
                Config.getConfig().getString("mode-messages.mode-set",
                        "&4ERROR: &rmode-messages.mode-set string missing"),
                modesetName
        );

        // Re-apply things like attack speed and collision team
        final Player playerCopy = player;
        ModuleLoader.getModules().forEach(module -> module.onModesetChange(playerCopy));
    }

    /*
    private void test(OCMMain plugin, CommandSender sender) {
        final Location location = sender instanceof Player ?
                ((Player) sender).getLocation() :
                sender.getServer().getWorlds().get(0).getSpawnLocation();

        new InGameTester(plugin).performTests(sender, location);
    }
     */

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
                                /*
                            case test:
                                test(plugin, sender);
                                break;
                                 */
                            case mode:
                                mode(sender, args);
                                break;
                            default:
                                throw new CommandNotRecognisedException();
                        }
                    }
                } catch (IllegalArgumentException e) {
                    throw new CommandNotRecognisedException();
                }
            } catch (CommandNotRecognisedException e) {
                Messenger.send(sender, "Subcommand not recognised!");
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
            Messenger.sendNoPrefix(sender, NO_PERMISSION, "oldcombatmechanics." + subcommand);
        return hasPermission;
    }
}