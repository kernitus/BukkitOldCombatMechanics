package kernitus.plugin.OldCombatMechanics.utilities;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

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

}
