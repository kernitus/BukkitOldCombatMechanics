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
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;

import java.io.File;

public class OCMCommandHandler implements CommandExecutor {
    private static final String NO_PERMISSION = "&cYou need the permission '%s' to do that!";

    private OCMMain plugin;
    private File pluginFile;

    public OCMCommandHandler(OCMMain instance, File pluginFile){
        this.plugin = instance;
        this.pluginFile = pluginFile;
    }

    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args){
        if(args.length < 1){//Tell them about available commands
            PluginDescriptionFile pdf = plugin.getDescription();

            Messenger.send(sender, ChatColor.DARK_GRAY + Messenger.HORIZONTAL_BAR);

            Messenger.send(sender, "&6&lOldCombatMechanics&e by &ckernitus&e and &cRayzr522&e version &6%s", pdf.getVersion());
            Messenger.send(sender, "&eYou can use &c/ocm reload&e to reload the config file");

            if(plugin.getConfig().getBoolean("enableIndividualToggle") && sender.hasPermission("oldcombatmechanics.toggle") && sender instanceof Player){
                Messenger.send(sender, "&eYou can use &c/ocm toggle&e to turn your attack cooldown on/off");
            }

            Messenger.send(sender, ChatColor.DARK_GRAY + Messenger.HORIZONTAL_BAR);

            // Check for updates
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> new UpdateChecker(plugin, pluginFile).sendUpdateMessages(sender));

            return true;
        }

        // Get the sub-command
        String sub = args[0].toLowerCase();

        if(sub.equals("reload")){// Reloads config
            if(!sender.hasPermission("oldcombatmechanics.reload")){
                Messenger.send(sender, NO_PERMISSION, "oldcombatmechanics.reload");
                return true;
            }

            Config.reload();

            Messenger.send(sender, "&6&lOldCombatMechanics&e config file reloaded");

            return true;
        } else if(sub.equals("toggle") && plugin.getConfig().getBoolean("enableIndividualToggle") && sender instanceof Player){
            if(!sender.hasPermission("oldcombatmechanics.toggle")){
                Messenger.send(sender, NO_PERMISSION, "oldcombatmechanics.toggle");
                return true;
            }

            // Toggle their cooldown
            Player p = (Player) sender;
            double GAS = plugin.getConfig().getDouble("disable-attack-cooldown.generic-attack-speed");

            AttributeInstance attribute = p.getAttribute(Attribute.GENERIC_ATTACK_SPEED);
            double baseValue = attribute.getBaseValue();
            String message = "&1[OCM] &aAttack cooldown ";

            if(baseValue == GAS){ // Toggle
                GAS = 4;
                message += "enabled";
            } else {
                message += "disabled";
            }

            attribute.setBaseValue(GAS);
            p.saveData();
            Messenger.send(p, message);

            return true;
        }

        return false;
    }

}