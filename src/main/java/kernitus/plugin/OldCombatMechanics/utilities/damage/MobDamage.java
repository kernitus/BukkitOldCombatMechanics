/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
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
                .put("SKELETON", Enchantment.SMITE)
                .put("ZOMBIE", Enchantment.SMITE)
                .put("WITHER", Enchantment.SMITE)
                .put("WITHER_SKELETON", Enchantment.SMITE)
                .put("ZOMBIFIED_PIGLIN", Enchantment.SMITE)
                .put("SKELETON_HORSE", Enchantment.SMITE)
                .put("STRAY", Enchantment.SMITE)
                .put("HUSK", Enchantment.SMITE)
                .put("PHANTOM", Enchantment.SMITE)
                .put("DROWNED", Enchantment.SMITE)
                .put("ZOGLIN", Enchantment.SMITE)
                .put("ZOMBIE_HORSE", Enchantment.SMITE)
                .put("ZOMBIE_VILLAGER", Enchantment.SMITE)

                // Arthropods (https://minecraft.gamepedia.com/Arthropod)
                .put("SPIDER", Enchantment.BANE_OF_ARTHROPODS)
                .put("CAVE_SPIDER", Enchantment.BANE_OF_ARTHROPODS)
                .put("BEE", Enchantment.BANE_OF_ARTHROPODS)
                .put("SILVERFISH", Enchantment.BANE_OF_ARTHROPODS)
                .put("ENDERMITE", Enchantment.BANE_OF_ARTHROPODS)

                .build();

        ImmutableMap.Builder<EntityType, Enchantment> enchantsBuilder = ImmutableMap.builder();

        // Add these individually because some may not exist in the Minecraft version we're running
        allMobs.keySet().forEach(entityName -> {
            try {
                final EntityType entityType = EntityType.valueOf(entityName);
                final Enchantment enchantment = allMobs.get(entityName);
                enchantsBuilder.put(entityType, enchantment);
            } catch (IllegalArgumentException ignored) {
            } // Mob not supported in this MC version
        });
        enchants = enchantsBuilder.build();
    }

    /**
     * Gets damage due to Smite and Bane of Arthropods enchantments, when applicable
     *
     * @param entity The type of entity that was attacked
     * @param item   The enchanted weapon used in the attack
     * @return The damage due to the enchantments
     */
    public static double getEntityEnchantmentsDamage(EntityType entity, ItemStack item) {
        final Enchantment enchantment = enchants.get(entity);

        if (enchantment == null || enchantment != Enchantment.SMITE || enchantment != Enchantment.BANE_OF_ARTHROPODS)
            return 0;

        return 2.5 * item.getEnchantmentLevel(enchantment);
    }

}
