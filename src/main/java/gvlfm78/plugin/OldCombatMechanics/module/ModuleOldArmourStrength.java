package gvlfm78.plugin.OldCombatMechanics.module;

import com.codingforcookies.armourequip.ArmourEquipEvent;
import com.comphenix.example.Attributes;
import gvlfm78.plugin.OldCombatMechanics.OCMMain;
import gvlfm78.plugin.OldCombatMechanics.utilities.reflection.ItemData;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.inventory.ItemStack;

public class ModuleOldArmourStrength extends Module {

    private static ModuleOldArmourStrength INSTANCE;

    public ModuleOldArmourStrength(OCMMain plugin) {
        super(plugin, "old-armour-strength");
        INSTANCE = this;

    }

    @EventHandler
    public void onArmourEquip(ArmourEquipEvent e) {

        debug("Test999", e.getPlayer());
        ItemStack newPiece = e.getNewArmourPiece();

        if (newPiece != null && newPiece.getType() != Material.AIR) {
            Player p = e.getPlayer();
            debug("Test123", p);

            e.setNewArmourPiece(apply(newPiece));


            //They equipped a valid new piece of armour

            // Check if there is armour toughness on piece, if so, remove it

            // Check if the armour strength on the piece is the same as in the config for that piece
            // if not, set the amour strength to the value in the config
        }
    }

    private ItemStack apply(ItemStack is) {

        if (ItemData.hasMark(is, "ArmorModifier")) {
            return is;
        }

        Attributes attributes = new Attributes(is);
        attributes.add(Attributes.Attribute.newBuilder().name("Armor").type(Attributes.AttributeType.GENERIC_ARMOR).amount(20).build());
        is = attributes.getStack();

        ItemData.mark(is, "ArmorModifier");

        return is;

    }

}
