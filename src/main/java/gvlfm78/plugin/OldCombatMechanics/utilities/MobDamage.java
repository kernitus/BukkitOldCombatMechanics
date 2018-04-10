package kernitus.plugin.OldCombatMechanics.utilities;

import com.google.common.collect.ImmutableMap;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * Created by Rayzr522 on 6/21/16.
 */
public class MobDamage {

    private static Map<EntityType, Enchantment> enchants = ImmutableMap.<EntityType, Enchantment>builder()
            // Undead:
            .put(EntityType.SKELETON, Enchantment.DAMAGE_UNDEAD)
            .put(EntityType.ZOMBIE, Enchantment.DAMAGE_UNDEAD)
            .put(EntityType.WITHER, Enchantment.DAMAGE_UNDEAD)
            .put(EntityType.PIG_ZOMBIE, Enchantment.DAMAGE_UNDEAD)
            // Arthropods:
            .put(EntityType.SPIDER, Enchantment.DAMAGE_ARTHROPODS)
            .put(EntityType.CAVE_SPIDER, Enchantment.DAMAGE_ARTHROPODS)
            .put(EntityType.SILVERFISH, Enchantment.DAMAGE_ARTHROPODS)
            .put(EntityType.ENDERMITE, Enchantment.DAMAGE_ARTHROPODS)
            .build();

    public static double applyEntityBasedDamage(EntityType entity, ItemStack item, double startDamage){
        Enchantment ench = enchants.get(entity);

        if(ench == null){
            return startDamage;
        }

        if(ench == Enchantment.DAMAGE_UNDEAD || ench == Enchantment.DAMAGE_ARTHROPODS){
            return startDamage + 2.5 * item.getEnchantmentLevel(ench);
        }

        return startDamage;
    }

}
