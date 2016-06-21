package gvlfm78.plugin.OldCombatMechanics;

import java.util.logging.Logger;

/**
 * Created by Rayzr522 on 6/21/16.
 */
public class Messenger {

    private static OCMMain plugin;
    public static boolean DEBUG_ENABLED = false;

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

    public static void debug(String msg) {

        if (!DEBUG_ENABLED) {
            return;
        }

        plugin.getLogger().info("[DEBUG] " + msg);

    }

}
