package kernitus.plugin.OldCombatMechanics.utilities.damage;

import com.google.common.collect.ImmutableMap;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public class MobDamage {

    private static final Map<EntityType, Enchantment> enchants;

    static {
        Map<String, Enchantment> allMobs = ImmutableMap.<String, Enchantment>builder()

                // Undead (https://minecraft.gamepedia.com/Undead)
                .put("SKELETON", Enchantment.DAMAGE_UNDEAD)
                .put("ZOMBIE", Enchantment.DAMAGE_UNDEAD)
                .put("WITHER", Enchantment.DAMAGE_UNDEAD)
                .put("WITHER_SKELETON", Enchantment.DAMAGE_UNDEAD)
                .put("ZOMBIFIED_PIGLIN", Enchantment.DAMAGE_UNDEAD)
                .put("SKELETON_HORSE", Enchantment.DAMAGE_UNDEAD)
                .put("STRAY", Enchantment.DAMAGE_UNDEAD)
                .put("HUSK", Enchantment.DAMAGE_UNDEAD)
                .put("PHANTOM", Enchantment.DAMAGE_UNDEAD)
                .put("DROWNED", Enchantment.DAMAGE_UNDEAD)
                .put("ZOGLIN", Enchantment.DAMAGE_UNDEAD)

                // Arthropods (https://minecraft.gamepedia.com/Arthropod)
                .put("SPIDER", Enchantment.DAMAGE_ARTHROPODS)
                .put("CAVE_SPIDER", Enchantment.DAMAGE_ARTHROPODS)
                .put("BEE", Enchantment.DAMAGE_ARTHROPODS)
                .put("SILVERFISH", Enchantment.DAMAGE_ARTHROPODS)
                .put("ENDERMITE", Enchantment.DAMAGE_ARTHROPODS)

                .build();

        ImmutableMap.Builder<EntityType, Enchantment> enchantsBuilder = ImmutableMap.builder();

        // Add these individually because some may not exist in the Minecraft version we're running
        allMobs.keySet().forEach(entityName -> {
            try {
                final EntityType entityType = EntityType.valueOf(entityName);
                final Enchantment enchantment = allMobs.get(entityName);
                enchantsBuilder.put(entityType, enchantment);
            } catch(IllegalArgumentException ignored) {} // Mob not supported in this MC version
        });
        enchants = enchantsBuilder.build();
    }

    public static double applyEntityBasedDamage(EntityType entity, ItemStack item, double startDamage){
        Enchantment enchantment = enchants.get(entity);

        if(enchantment == null) return startDamage;

        if(enchantment == Enchantment.DAMAGE_UNDEAD || enchantment == Enchantment.DAMAGE_ARTHROPODS)
            return startDamage + 2.5 * item.getEnchantmentLevel(enchantment);

        return startDamage;
    }

}
