package kernitus.plugin.OldCombatMechanics;

import kernitus.plugin.OldCombatMechanics.module.ModuleAttackCooldown;
import kernitus.plugin.OldCombatMechanics.tester.InGameTester;
import kernitus.plugin.OldCombatMechanics.utilities.Config;
import kernitus.plugin.OldCombatMechanics.utilities.Messenger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Locale;

public class OCMCommandHandler implements CommandExecutor {
    private static final String NO_PERMISSION = "&cYou need the permission '%s' to do that!";

    private final OCMMain plugin;
    private final File pluginFile;

    public OCMCommandHandler(OCMMain instance, File pluginFile) {
        this.plugin = instance;
        this.pluginFile = pluginFile;
    }

    // todo command suggestions (if enabledIndividualToggle disabled, or no perms, don't show suggestions)

    private void help(OCMMain plugin, CommandSender sender) {
        final FileConfiguration config = plugin.getConfig();
        final PluginDescriptionFile description = plugin.getDescription();

        Messenger.send(sender, ChatColor.DARK_GRAY + Messenger.HORIZONTAL_BAR);
        Messenger.send(sender, "&6&lOldCombatMechanics&e by &ckernitus&e and &cRayzr522&e version &6%s", description.getVersion());
        Messenger.send(sender, "&eYou can use &c/ocm reload&e to reload the config file");

        if (sender instanceof Player && sender.hasPermission("oldcombatmechanics.toggle")
                && config.getBoolean("enableIndividualToggle"))
            Messenger.send(sender, "&eYou can use &c/ocm toggle&e to turn your attack cooldown on/off");

        Messenger.send(sender, ChatColor.DARK_GRAY + Messenger.HORIZONTAL_BAR);

        // Check for updates
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> new UpdateChecker(plugin, pluginFile).sendUpdateMessages(sender));
    }

    private void reload(CommandSender sender) {
        if (sender.hasPermission("oldcombatmechanics.reload")) {
            Config.reload();
            Messenger.send(sender, "&6&lOldCombatMechanics&e config file reloaded");
        } else Messenger.send(sender, NO_PERMISSION, "oldcombatmechanics.reload");
    }

    private void toggle(OCMMain plugin, CommandSender sender, String[] args) {
        final FileConfiguration config = plugin.getConfig();

        if (!sender.hasPermission("oldcombatmechanics.toggle")) {
            Messenger.send(sender, NO_PERMISSION, "oldcombatmechanics.toggle");
        } else {
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
    }

    private void test(OCMMain plugin, CommandSender sender, String[] args) {
        if (!sender.hasPermission("oldcombatmechanics.reload")) {
            Messenger.send(sender, NO_PERMISSION, "oldcombatmechanics.reload");
        } else {
            Player player1 = null, player2 = null;
            if (args.length > 2) {
                player1 = Bukkit.getPlayer(args[1]);
                player2 = Bukkit.getPlayer(args[2]);
            } else if (sender instanceof Player && args.length > 1) {
                player1 = ((Player) sender);
                player2 = Bukkit.getPlayer(args[1]);
            } else {
                Messenger.send(sender, "&1[OCM] &eCommand usage: /ocm test <player1> <player2>");
            }
            if (player1 != null && player2 != null)
                new InGameTester(plugin).performTests(player1, player2);
        }
    }

    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String[] args) {
        // Show them the available commands
        if (args.length < 1) {
            help(plugin, sender);
        } else {
            // Otherwise, get the subcommand
            final String subcommand = args[0].toLowerCase(Locale.ROOT);
            final FileConfiguration config = plugin.getConfig();

            if (subcommand.equals("reload")) {
                reload(sender);
            } else if (config.getBoolean("enableIndividualToggle") && subcommand.equals("toggle")) {
                toggle(plugin, sender, args);
            } else if (subcommand.equals("test")) {
                test(plugin, sender, args);
            }
        }
        return true;
    }
}