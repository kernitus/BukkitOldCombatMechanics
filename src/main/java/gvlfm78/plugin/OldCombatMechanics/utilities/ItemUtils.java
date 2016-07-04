package gvlfm78.plugin.OldCombatMechanics.utilities;

import gvlfm78.plugin.OldCombatMechanics.utilities.parseCommand.ParseCommand;
import gvlfm78.plugin.OldCombatMechanics.utilities.parseCommand.ParseEnchantment;
import gvlfm78.plugin.OldCombatMechanics.utilities.parseCommand.ParseName;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;

/**
 * Created by Rayzr522 on 7/4/16.
 */
public class ItemUtils {

    private static List<ParseCommand> parsers = Arrays.asList(new ParseEnchantment(), new ParseName());

    public static final ItemStack ERROR = new ItemStack(Material.BARRIER, 0);

    public static ItemStack enchantItem(ItemStack base, Enchant... enchantments) {

        for (Enchant ench : enchantments) {

            base.addEnchantment(ench.getType(), ench.getLevel());

        }

        return base;

    }

    public static class Enchant {

        private int level;
        private Enchantment type;

        public Enchant(Enchantment type, int level) {
            this.level = level;
            this.type = type;
        }

        public int getLevel() {
            return level;
        }

        public Enchantment getType() {
            return type;
        }

    }

    public static ItemStack makeItem(String description) {

        String[] statements = description.split(",");

        if (statements.length < 1) {

            System.out.println("Requires more words");
            return ERROR;

        }

        String typeString = statements[0].trim();

        int amount = 0;

        try {
            amount = Integer.parseInt(typeString.split(" ")[0]);
            typeString = typeString.replaceFirst("^[0-9]+", "");
        } catch (Exception e) {
            amount = 1;
        }

        typeString = typeString.trim();

        Material type = null;

        try {

            type = Material.valueOf(typeString.replace(" ", "_").toUpperCase());

        } catch (Exception e) {

            System.out.println("Invalid type '" + typeString + "'");
            return ERROR;

        }

        ItemStack output = new ItemStack(type, amount);

        for (int i = 1; i < statements.length; i++) {

            String statement = statements[i].trim();

            for (ParseCommand cmd : parsers) {

                for (String str : cmd.getDescriptors()) {

                    if (statement.startsWith(str)) {

                        output = cmd.apply(output, statement.replaceFirst(str, "").trim());
                        break;

                    }

                }

            }

        }

        return output;

    }

}
