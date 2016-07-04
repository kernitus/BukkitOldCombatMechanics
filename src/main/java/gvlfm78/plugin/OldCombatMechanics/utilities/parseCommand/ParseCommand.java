package gvlfm78.plugin.OldCombatMechanics.utilities.parseCommand;

import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Created by Rayzr522 on 7/4/16.
 */
public abstract class ParseCommand {

    public abstract List<String> getDescriptors();

    public abstract ItemStack apply(ItemStack base, String args);

}
