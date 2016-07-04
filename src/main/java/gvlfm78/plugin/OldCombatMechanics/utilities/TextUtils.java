
package gvlfm78.plugin.OldCombatMechanics.utilities;

import org.bukkit.ChatColor;

public class TextUtils {

	public static String colorize(String text) {

		return ChatColor.translateAlternateColorCodes('&', text);

	}

	public static String uncolorize(String text) {

		return text.replace(ChatColor.COLOR_CHAR, '&');

	}

	public static String stripColor(String text) {

		return ChatColor.stripColor(text);

	}

}
