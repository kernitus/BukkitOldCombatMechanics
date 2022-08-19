/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.tester;

import kernitus.plugin.OldCombatMechanics.utilities.Messenger;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.command.CommandSender;

import java.util.Collection;

public class TesterUtils {

    /**
     * Checks whether the two values are equal, prints the result and updates the tally
     * @param a The expected value
     * @param b The actual value
     * @param tally The tally to update the result of the test with
     * @param testName The name of the test being run
     * @param senders The command senders to message with the result of the test
     */
    public static void assertEquals(float a, float b, Tally tally, String testName, CommandSender... senders) {
        // Due to cooldown effects, numbers can be very close (e.g. 1.0000000149011612 == 1.0)
        // These are equivalent when using floats, which is what the server is using anyway
        if (a == b) {
            tally.passed();
            for (CommandSender sender : senders)
                Messenger.sendNormalMessage(sender, "&aPASSED &f" + testName + " [E: " + a + " / A: " + b + "]");
        } else {
            tally.failed();
            for (CommandSender sender : senders)
                Messenger.sendNormalMessage(sender, "&cFAILED &f" + testName + " [E: " + a + " / A: " + b + "]");
        }
    }

    public static double getAttributeModifierSum(Collection<AttributeModifier> modifiers){
        double sum = 0;
        for (AttributeModifier modifier : modifiers) {
            final double value = modifier.getAmount();
            switch (modifier.getOperation()) {
                case ADD_SCALAR -> sum += Math.abs(value);
                case ADD_NUMBER -> sum += value;
                case MULTIPLY_SCALAR_1 -> sum *= value;
            }
        }
        return sum;
    }
}
