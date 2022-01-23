package kernitus.plugin.OldCombatMechanics.utilities;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.Objects;
import java.util.logging.Level;

/**
 * Created by Rayzr522 on 6/21/16.
 */
public class Messenger {

    public static final String HORIZONTAL_BAR = ChatColor.STRIKETHROUGH + "----------------------------------------------------";
    static boolean DEBUG_ENABLED = false;
    private static OCMMain plugin;

    public static void initialise(OCMMain plugin) {
        Messenger.plugin = plugin;
    }

    public static void info(String message, Object... args) {
        plugin.getLogger().info(TextUtils.stripColor(String.format(message, args)));
    }

    public static void warn(Throwable e, String message, Object... args) {
        plugin.getLogger().log(Level.WARNING, TextUtils.stripColor(String.format(message, args)), e);
    }

    public static void warn(String message, Object... args) {
        plugin.getLogger().log(Level.WARNING, TextUtils.stripColor(String.format(message, args)));
    }

    /**
     * This will format any ampersand (&) color codes,
     * format any args passed to it using {@link String#format(String, Object...)},
     * and then send the message to the specified {@link CommandSender}.
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

    /**
     * This will add the prefix to the message, format any ampersand (&) color codes,
     * format any args passed to it using {@link String#format(String, Object...)},
     * and then send the message to the specified {@link CommandSender}.
     *
     * @param sender  The {@link CommandSender} to send the message to.
     * @param message The message to send.
     * @param prefix  The prefix to the message
     * @param args    The args to format the message with.
     */
    public static void sendWithPrefix(CommandSender sender, String message, String prefix, Object... args) {
        send(sender, prefix + " " + message, args);
    }

    public static void sendNormalMessage(CommandSender sender, String message, Object... args) {
        if (Config.getConfig().getBoolean("show-prefix", true))
            sendWithPrefix(sender, message, "&6[OCM]&r", args);
        else
            sendWithPrefix(sender, message, "&r", args);
    }

    public static void sendDebugMessage(CommandSender sender, String message, Object... args) {
        if (Config.getConfig().getBoolean("show-prefix", true))
            sendWithPrefix(sender, message, "&1[Debug]&r", args);
        else
            sendWithPrefix(sender, message, "&r", args);
    }

    public static void debug(String message, Throwable throwable) {
        if(DEBUG_ENABLED) plugin.getLogger().log(Level.INFO, message, throwable);
    }

    public static void debug(String message, Object... args) {
        if(DEBUG_ENABLED) info("[DEBUG] " + message, args);
    }

    public static void debug(CommandSender sender, String message, Object... args) {
        if(DEBUG_ENABLED) sendDebugMessage(sender, message, args);
    }
}
