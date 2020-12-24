package kernitus.plugin.OldCombatMechanics;

import kernitus.plugin.OldCombatMechanics.utilities.Config;
import kernitus.plugin.OldCombatMechanics.utilities.Messenger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
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

    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String[] args) {

        final FileConfiguration config = plugin.getConfig();

        // Show them the available commands
        if (args.length < 1) {
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

            return true;
        }

        // Otherwise, get the subcommand
        final String subcommand = args[0].toLowerCase(Locale.ROOT);

        if (subcommand.equals("reload")) {
            if (sender.hasPermission("oldcombatmechanics.reload")) {
                Config.reload();
                Messenger.send(sender, "&6&lOldCombatMechanics&e config file reloaded");
            } else Messenger.send(sender, NO_PERMISSION, "oldcombatmechanics.reload");

            return true;
        } else if (sender instanceof Player && config.getBoolean("enableIndividualToggle") && subcommand.equals("toggle")) {
            if (!sender.hasPermission("oldcombatmechanics.toggle")) {
                Messenger.send(sender, NO_PERMISSION, "oldcombatmechanics.toggle");
                return true;
            }

            // Toggle their cooldown
            final Player player = (Player) sender;
            double speed = config.getDouble("disable-attack-cooldown.generic-attack-speed");

            final AttributeInstance attribute = player.getAttribute(Attribute.GENERIC_ATTACK_SPEED);
            final double baseValue = attribute.getBaseValue();
            String message = "&1[OCM] &aAttack cooldown ";

            if (baseValue == speed) { // Toggle
                speed = 4;
                message += "enabled";
            } else {
                message += "disabled";
            }

            attribute.setBaseValue(speed);
            player.saveData();
            Messenger.send(player, message);

            return true;
        }

        return false;
    }

}