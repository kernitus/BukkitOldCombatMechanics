package gvlfm78.plugin.OldCombatMechanics;

import gvlfm78.plugin.OldCombatMechanics.utilities.Chatter;
import gvlfm78.plugin.OldCombatMechanics.utilities.Config;
import org.bukkit.ChatColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;

import java.io.File;

public class OCMCommandHandler implements CommandExecutor {
    private static final String NO_PERMISSION = "&cYou need the permission '%s' to do that!";

    private OCMMain plugin;
    private File pluginFile;

    public OCMCommandHandler(OCMMain instance, File pluginFile) {
        this.plugin = instance;
        this.pluginFile = pluginFile;
    }

    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length < 1) {//Tell them about available commands
            OCMUpdateChecker updateChecker = new OCMUpdateChecker(plugin, pluginFile);

            PluginDescriptionFile pdf = plugin.getDescription();

            Chatter.send(sender, ChatColor.DARK_GRAY + Chatter.HORIZONTAL_BAR);

            Chatter.send(sender, "&6&lOldCombatMechanics&e by &cgvlfm78&e and &cRayzr522&e version &6" + pdf.getVersion());
            Chatter.send(sender, "&eYou can use &c/ocm reload&e to reload the config file");

            if (plugin.getConfig().getBoolean("enableIndividualToggle") && sender.hasPermission("oldcombatmechanics.toggle") && sender instanceof Player) {
                Chatter.send(sender, "&eYou can use &c/ocm toggle&e to turn your attack cooldown on/off");
            }

            Chatter.send(sender, ChatColor.DARK_GRAY + Chatter.HORIZONTAL_BAR);

            // Update check
            updateChecker.sendUpdateMessages(sender);

            return true;
        }

        // Get the sub-command
        String sub = args[0].toLowerCase();

        if (sub.equals("reload")) {// Reloads config
            if (!sender.hasPermission("oldcombatmechanics.reload")) {
                Chatter.send(sender, NO_PERMISSION, "oldcombatmechanics.reload");
                return true;
            }

            Config.reload();

            Chatter.send(sender, "&6&lOldCombatMechanics&e config file reloaded");

            return true;
        } else if (sub.equals("toggle") && plugin.getConfig().getBoolean("enableIndividualToggle") && sender instanceof Player) {
            if (!sender.hasPermission("oldcombatmechanics.toggle")) {
                Chatter.send(sender, NO_PERMISSION, "oldcombatmechanics.toggle");
                return true;
            }

            // Toggle their cooldown
            Player p = (Player) sender;
            double GAS = plugin.getConfig().getDouble("disable-attack-cooldown.generic-attack-speed");

            AttributeInstance attribute = p.getAttribute(Attribute.GENERIC_ATTACK_SPEED);
            double baseValue = attribute.getBaseValue();
            String message = "&1[OCM] &aAttack cooldown ";

            if (baseValue == GAS) { // Toggle
                GAS = 4;
                message += "enabled";
            } else {
                message += "disabled";
            }

            attribute.setBaseValue(GAS);
            p.saveData();
            Chatter.send(p, message);

            return true;
        }

        return false;
    }

}