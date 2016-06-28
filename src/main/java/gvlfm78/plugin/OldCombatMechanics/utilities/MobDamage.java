package gvlfm78.plugin.OldCombatMechanics.utilities;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;

/**
 * Created by Rayzr522 on 6/21/16.
 */
public class MobDamage {

    private static HashMap<EntityType, Enchantment> enchants = new HashMap<EntityType, Enchantment>();

    static {

        enchants.put(EntityType.SKELETON, Enchantment.DAMAGE_UNDEAD);
        enchants.put(EntityType.ZOMBIE, Enchantment.DAMAGE_UNDEAD);
        enchants.put(EntityType.WITHER, Enchantment.DAMAGE_UNDEAD);
        enchants.put(EntityType.PIG_ZOMBIE, Enchantment.DAMAGE_UNDEAD);

        enchants.put(EntityType.SPIDER, Enchantment.DAMAGE_ARTHROPODS);
        enchants.put(EntityType.CAVE_SPIDER, Enchantment.DAMAGE_ARTHROPODS);
        enchants.put(EntityType.SILVERFISH, Enchantment.DAMAGE_ARTHROPODS);
        enchants.put(EntityType.ENDERMITE, Enchantment.DAMAGE_ARTHROPODS);

    }

    public static Enchantment getEnchant(EntityType entity) {

        return (enchants.containsKey(entity) ? enchants.get(entity) : null);

    }

    public static double applyEntityBasedDamage(EntityType entity, ItemStack item, double startDamage) {

        Enchantment ench = getEnchant(entity);

        if (ench == null) {
            return startDamage;
        }

        if (ench == Enchantment.DAMAGE_UNDEAD || ench == Enchantment.DAMAGE_ARTHROPODS) {

            return startDamage + 2.5 * item.getEnchantmentLevel(ench);

        }

        return startDamage;

    }

}
