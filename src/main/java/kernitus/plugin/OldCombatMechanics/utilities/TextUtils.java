/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities;

import org.bukkit.ChatColor;

public class TextUtils {
    /**
     * Converts ampersand (&) color codes to Minecraft ({@link ChatColor#COLOR_CHAR}) color codes.
     *
     * @param text The text to colourise.
     * @return The colourised text.
     */
    public static String colourise(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    /**
     * Removes all Minecraft ({@link ChatColor#COLOR_CHAR}) colour codes from a string.
     *
     * @param text The text to strip colours from.
     * @return The stripped text.
     */
    public static String stripColour(String text) {
        return ChatColor.stripColor(text);
    }
}
