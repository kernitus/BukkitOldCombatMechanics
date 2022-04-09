package kernitus.plugin.OldCombatMechanics.tester;

import kernitus.plugin.OldCombatMechanics.utilities.Messenger;
import org.bukkit.command.CommandSender;

public class TesterUtils {

    public static void assertEquals(double a, double b, String testName, CommandSender... senders){
        for (CommandSender sender : senders) {
            if(a == b){
                Messenger.sendNormalMessage(sender, "&aPASSED &f" + testName + " [" + a + "/" + b + "]");
            } else{
                Messenger.sendNormalMessage(sender, "&cFAILED &f" + testName + " [" + a + "/" + b + "]");
            }
        }
    }
}
