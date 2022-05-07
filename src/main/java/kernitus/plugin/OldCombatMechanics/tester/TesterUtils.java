/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.tester;

import kernitus.plugin.OldCombatMechanics.utilities.Messenger;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;

public class TesterUtils {

    public static final class PlayerInfo {
        Location location;
        int maximumNoDamageTicks;
        ItemStack[] inventoryContents;

        public PlayerInfo(Location location, int maximumNoDamageTicks, ItemStack[] inventoryContents) {
            this.location = location;
            this.maximumNoDamageTicks = maximumNoDamageTicks;
            this.inventoryContents = inventoryContents;
        }
    }

    public static void assertEquals(double a, double b, Tally tally, String testName, CommandSender... senders) {
        if (a == b) {
            tally.passed();
            for (CommandSender sender : senders)
                Messenger.sendNormalMessage(sender, "&aPASSED &f" + testName + " [" + a + "/" + b + "]");
        } else {
            tally.failed();
            for (CommandSender sender : senders)
                Messenger.sendNormalMessage(sender, "&cFAILED &f" + testName + " [" + a + "/" + b + "]");
        }
    }
}
