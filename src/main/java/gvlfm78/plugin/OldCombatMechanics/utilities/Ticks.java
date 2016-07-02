package gvlfm78.plugin.OldCombatMechanics.utilities;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Created by Rayzr522 on 6/28/16.
 */
public class Ticks {

    public static long current() {

        return Bukkit.getServer().getWorlds().get(0).getFullTime();

    }

    public static long current(Player player) {

        return player.getWorld().getFullTime();

    }

}
