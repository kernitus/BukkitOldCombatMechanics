package kernitus.plugin.OldCombatMechanics.utilities.damage;

import com.google.common.collect.ImmutableMap;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public class MobDamage {

    private static final Map<EntityType, Enchantment> enchants = ImmutableMap.<EntityType, Enchantment>builder()

            // Undead (https://minecraft.gamepedia.com/Undead)
            .put(EntityType.SKELETON, Enchantment.DAMAGE_UNDEAD)
            .put(EntityType.ZOMBIE, Enchantment.DAMAGE_UNDEAD)
            .put(EntityType.WITHER, Enchantment.DAMAGE_UNDEAD)
            .put(EntityType.WITHER_SKELETON, Enchantment.DAMAGE_UNDEAD)
            .put(EntityType.ZOMBIFIED_PIGLIN, Enchantment.DAMAGE_UNDEAD)
            .put(EntityType.SKELETON_HORSE, Enchantment.DAMAGE_UNDEAD)
            .put(EntityType.STRAY, Enchantment.DAMAGE_UNDEAD)
            .put(EntityType.HUSK, Enchantment.DAMAGE_UNDEAD)
            .put(EntityType.PHANTOM, Enchantment.DAMAGE_UNDEAD)
            .put(EntityType.DROWNED, Enchantment.DAMAGE_UNDEAD)
            .put(EntityType.ZOGLIN, Enchantment.DAMAGE_UNDEAD)

            // Arthropods (https://minecraft.gamepedia.com/Arthropod)
            .put(EntityType.SPIDER, Enchantment.DAMAGE_ARTHROPODS)
            .put(EntityType.CAVE_SPIDER, Enchantment.DAMAGE_ARTHROPODS)
            .put(EntityType.BEE, Enchantment.DAMAGE_ARTHROPODS)
            .put(EntityType.SILVERFISH, Enchantment.DAMAGE_ARTHROPODS)
            .put(EntityType.ENDERMITE, Enchantment.DAMAGE_ARTHROPODS)

            .build();

    public static double applyEntityBasedDamage(EntityType entity, ItemStack item, double startDamage){
        Enchantment enchantment = enchants.get(entity);

        if(enchantment == null) return startDamage;

        if(enchantment == Enchantment.DAMAGE_UNDEAD || enchantment == Enchantment.DAMAGE_ARTHROPODS)
            return startDamage + 2.5 * item.getEnchantmentLevel(enchantment);

        return startDamage;
    }

}
