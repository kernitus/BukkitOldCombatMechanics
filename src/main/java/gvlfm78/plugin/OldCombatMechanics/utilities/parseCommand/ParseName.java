package gvlfm78.plugin.OldCombatMechanics.utilities.parseCommand;

import gvlfm78.plugin.OldCombatMechanics.utilities.TextUtils;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;

/**
 * Created by Rayzr522 on 7/4/16.
 */
public class ParseName extends ParseCommand {

    private List<String> descriptors = Arrays.asList("named", "with name", "name");

    public List<String> getDescriptors() {
        return descriptors;
    }

    public ItemStack apply(ItemStack base, String args) {

        if (args.length() <= 0) {
            return base;
        }

        String name = TextUtils.colorize(args);

        ItemMeta meta = base.getItemMeta();

        meta.setDisplayName(name);

        base.setItemMeta(meta);

        return base;

    }

}
