package gvlfm78.plugin.OldCombatMechanics.utilities;

import gvlfm78.plugin.OldCombatMechanics.OCMMain;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.logging.Logger;

/**
 * Created by Rayzr522 on 6/21/16.
 */
public class Messenger {

    public static boolean DEBUG_ENABLED = false;
    public static OCMMain plugin;

    public static void Initialise(OCMMain plugin) {
        Messenger.plugin = plugin;
    }

    public static void info(String msg) {
        plugin.getLogger().info(msg);
    }

    public static void err(String msg) {
        plugin.getLogger().warning(msg);
    }

    public static void severe(String msg) {
        Logger logger = plugin.getLogger();

        logger.severe("------------------------------------------------------------");
        logger.severe("OldCombatMechanics has encountered a serious problem:");
        logger.severe(msg);
        logger.severe("------------------------------------------------------------");
    }

    public static void send(Player p, String msg) {
        p.sendMessage(TextUtils.colorize(msg));
    }

    public static void send(CommandSender s, String msg) {
        s.sendMessage(TextUtils.stripColor(msg));
    }

    public static void debug(String msg) {
        if(!DEBUG_ENABLED) return;
        plugin.getLogger().info("[DEBUG] " + msg);
    }

    public static void debug(String msg, Player p){
        if(!DEBUG_ENABLED) return;
        p.sendMessage("[DEBUG] " + msg);
    }
}
