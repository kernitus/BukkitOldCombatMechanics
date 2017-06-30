package gvlfm78.plugin.OldCombatMechanics.utilities;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.Objects;

/**
 * Created by Rayzr522 on 7/4/16.
 */
public class Chatter {

    public static final String HORIZONTAL_BAR = ChatColor.STRIKETHROUGH + "----------------------------------------------------";

    /**
     * This will format any ampersand (&) color codes, format any args passed to it using {@link String#format(String, Object...)}, and then send the message to the specified {@link CommandSender}.
     *
     * @param sender  The {@link CommandSender} to send the message to.
     * @param message The message to send.
     * @param args    The args to format the message with.
     */
    public static void send(CommandSender sender, String message, Object... args) {
        Objects.requireNonNull(sender, "sender cannot be null!");
        Objects.requireNonNull(message, "message cannot be null!");

        sender.sendMessage(TextUtils.colorize(String.format(message, args)));
    }

}
