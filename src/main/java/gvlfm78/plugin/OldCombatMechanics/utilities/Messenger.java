package kernitus.plugin.OldCombatMechanics.utilities;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.logging.Logger;

/**
 * Created by Rayzr522 on 6/21/16.
 */
public class Messenger {

    public static boolean DEBUG_ENABLED = false;

    public static void info(String msg, OCMMain plugin) {

        plugin.getLogger().info(msg);

    }

    public static void err(String msg, OCMMain plugin) {

        plugin.getLogger().warning(msg);

    }

    public static void severe(String msg, OCMMain plugin) {

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

    public static void debug(String msg, OCMMain plugin) {

        if (!DEBUG_ENABLED) {
            return;
        }

        plugin.getLogger().info("[DEBUG] " + msg);

    }

}
