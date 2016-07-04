package gvlfm78.plugin.OldCombatMechanics.utilities;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Created by Rayzr522 on 7/4/16.
 */
public class Chatter {

    public static final String HORIZONTAL_BAR = ChatColor.STRIKETHROUGH + "----------------------------------------------------";

    /**
     * This will format any "&" color codes and send the message to the command sender
     *
     * @param s The command sender to send the message to
     * @param msg The message to send
     */
    public static void send(CommandSender s, String msg) {

        s.sendMessage(TextUtils.colorize(msg));

    }

    /**
     * This will format any "&" color codes and send the message to the player
     *
     * @param p The player to send the message to
     * @param msg The message to send
     */
    public static void send(Player p, String msg) {

        p.sendMessage(TextUtils.colorize(msg));

    }

}
