/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities.reflection;

import com.comphenix.example.NbtFactory;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public class ItemData {

    public static void mark(ItemStack is, String marker){
        if(!hasMark(is, marker)){
            NbtFactory.fromItemTag(is).put("[OCM]" + marker, (byte) 1);
        }
    }

    public static void unmark(ItemStack is, String marker){
        if(hasMark(is, marker)){
            NbtFactory.fromItemTag(is).remove("[OCM]" + marker);
        }
    }

    public static boolean hasMark(ItemStack is, String marker){
        return is != null && is.getType() != Material.AIR && NbtFactory.fromItemTag(is).get("[OCM]" + marker) != null;
    }

}
