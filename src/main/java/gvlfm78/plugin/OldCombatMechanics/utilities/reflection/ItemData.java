package gvlfm78.plugin.OldCombatMechanics.utilities.reflection;

import com.comphenix.example.NbtFactory;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Created by Rayzr522 on 7/11/16.
 */
public class ItemData {

    public static void mark(ItemStack is, String marker) {
        if (!hasMark(is, marker)) {
            NbtFactory.fromItemTag(is).put("[OCM]" + marker, (byte) 1);
        }
    }

    public static void unmark(ItemStack is, String marker) {
        if (hasMark(is, marker)) {
            NbtFactory.fromItemTag(is).remove("[OCM]" + marker);
        }
    }

    public static boolean hasMark(ItemStack is, String marker) {
        return is != null && is.getType() != Material.AIR && NbtFactory.fromItemTag(is).get("[OCM]" + marker) != null;
    }

}
