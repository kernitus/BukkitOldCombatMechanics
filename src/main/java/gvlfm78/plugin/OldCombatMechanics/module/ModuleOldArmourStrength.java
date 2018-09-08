package gvlfm78.plugin.OldCombatMechanics.module;

import com.codingforcookies.armourequip.ArmourEquipEvent;
import com.comphenix.example.Attributes;
import com.comphenix.example.Attributes.Attribute;
import com.comphenix.example.Attributes.AttributeType;
import gvlfm78.plugin.OldCombatMechanics.OCMMain;
import gvlfm78.plugin.OldCombatMechanics.utilities.ArmourValues;
import gvlfm78.plugin.OldCombatMechanics.utilities.Config;
import gvlfm78.plugin.OldCombatMechanics.utilities.Messenger;
import gvlfm78.plugin.OldCombatMechanics.utilities.reflection.ItemData;
import org.apache.commons.lang.ArrayUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ModuleOldArmourStrength extends Module {
    private static final String ARMOR_MARK = "ArmorModifier";

    private static ModuleOldArmourStrength INSTANCE;

    public ModuleOldArmourStrength(OCMMain plugin){
        super(plugin, "old-armour-strength");
        INSTANCE = this;
    }

    private static ItemStack apply(ItemStack item, boolean enable){
        if(item == null || item.getType() == Material.AIR){
            return item;
        }

        String slot = getSlotForType(item.getType());
        if(slot == null){
            //Not an armour piece
            return item;
        }

        Attributes attributes = new Attributes(item);

        double strength = ArmourValues.getValue(item.getType());
        double toughness = enable ? Config.getConfig().getDouble("old-armour-strength.toughness") : getDefaultToughness(item.getType());

        if(!ItemData.hasMark(item, ARMOR_MARK)){
            for(Attribute attribute : attributes.values()){
                if(attribute.getAttributeType() == AttributeType.GENERIC_ARMOR || attribute.getAttributeType() == AttributeType.GENERIC_ARMOR_TOUGHNESS){
                    // Hasn't been marked but has custom attributes - it must be a custom item.
                    return item;
                }
            }
        }

        ItemData.mark(item, ARMOR_MARK);

        ensureAttributeAmount(attributes, AttributeType.GENERIC_ARMOR, "Armor", slot, strength);
        ensureAttributeAmount(attributes, AttributeType.GENERIC_ARMOR_TOUGHNESS, "ArmorToughness", slot, toughness);

        return item;
    }

    private static void ensureAttributeAmount(Attributes attributes, AttributeType type, String name, String slot, double amount){
        List<Attribute> toRemove = new ArrayList<>();
        boolean found = false;

        // Ensure only one attribute of the right value
        for(Attribute attribute : attributes.values()){
            if(attribute.getAttributeType() == type && attribute.getSlot().equals(slot)){
                if(found || attribute.getAmount() != amount){
                    // Remove others
                    toRemove.add(attribute);
                } else {
                    // Keep this attribute
                    found = true;
                }
            }
        }

        toRemove.forEach(attributes::remove);

        if(!found){
            attributes.add(
                    Attributes.Attribute.newBuilder()
                            .name(name)
                            .type(type)
                            .amount(amount)
                            .slot(slot)
                            .build()
            );

            Messenger.debug(String.format("Added '%s' flag on slot '%s' and set it to %f", name, slot, amount));
        }
    }

    private static int getDefaultToughness(Material mat){
        switch(mat){
            case DIAMOND_CHESTPLATE:
            case DIAMOND_HELMET:
            case DIAMOND_LEGGINGS:
            case DIAMOND_BOOTS:
                return 2;
            default:
                return 0;
        }
    }

    private static String getSlotForType(Material type){
        String typeName = type.toString().toLowerCase(Locale.ROOT);

        if(typeName.endsWith("helmet")){
            return "head";
        } else if(typeName.endsWith("chestplate")){
            return "chest";
        } else if(typeName.endsWith("leggings")){
            return "legs";
        } else if(typeName.endsWith("boots")){
            return "feet";
        }

        return null;
    }

    public static void applyArmour(Player player){
        INSTANCE.setArmourAccordingly(player);
    }

    @EventHandler
    public void onArmourEquip(ArmourEquipEvent e){
        final Player p = e.getPlayer();
        debug("OnArmourEquip was called", p);

        //Equipping
        ItemStack newPiece = e.getNewArmourPiece();
        if(newPiece != null && newPiece.getType() != Material.AIR){
            debug("Equip detected, applying armour value to new armour piece", p);
            e.setNewArmourPiece(apply(newPiece, isEnabled(p.getWorld())));
        }

        //Unequipping
        ItemStack oldPiece = e.getOldArmourPiece();
        if(oldPiece != null && oldPiece.getType() != Material.AIR){
            debug("Unequip detected, applying armour value to old armour piece", p);
            e.setOldArmourPiece(apply(oldPiece, false));
        }

    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e){
        final Player player = e.getPlayer();
        debug("onPlayerJoin armour event was called", player);
        setArmourAccordingly(player);
    }

    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent e){
        final Player player = e.getPlayer();
        debug("onPlayerLeave armour event was called", player);
        setArmourToDefault(player);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e){
        final Player player = e.getPlayer();
        debug("onWorldChange armour event was called", player);
        setArmourAccordingly(player);
    }

    private void setArmourToDefault(final Player player){
        // Tells method that module is disabled in this world
        setArmourAccordingly(player, false);
    }

    private void setArmourAccordingly(final Player player){
        setArmourAccordingly(player, isEnabled(player.getWorld()));
    }

    private void setArmourAccordingly(final Player player, boolean enabled){
        final PlayerInventory inv = player.getInventory();
        ItemStack[] armours = inv.getContents();
        // Check the whole inventory for armour pieces

        for(int i = 0; i < armours.length; i++){
            ItemStack piece = armours[i];

            if(piece != null && piece.getType() != Material.AIR){
                Messenger.debug("Attempting to apply armour value to item", player);

                //If this piece is one of the ones being worn right now
                if(ArrayUtils.contains(inv.getArmorContents(), armours[i]))
                    armours[i] = apply(piece, enabled); //Apply/remove values according state of module in this world
                else armours[i] = apply(piece, false); //Otherwise set values back to default
            }
        }

        player.getInventory().setContents(armours);
    }
}
