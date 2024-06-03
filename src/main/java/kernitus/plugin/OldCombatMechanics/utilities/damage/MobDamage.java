/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities.damage;

import com.google.common.collect.ImmutableMap;
import kernitus.plugin.OldCombatMechanics.versions.enchantments.EnchantmentCompat;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public class MobDamage {

    private static final Map<EntityType, Enchantment> enchants;
    private static final Enchantment SMITE = EnchantmentCompat.SMITE.get();
    private static final Enchantment BANE_OF_ARTHROPODS = EnchantmentCompat.BANE_OF_ARTHROPODS.get();

    static {
        Map<String, Enchantment> allMobs = ImmutableMap.<String, Enchantment>builder()
                // Undead (https://minecraft.gamepedia.com/Undead)
                .put("SKELETON", SMITE)
                .put("ZOMBIE", SMITE)
                .put("WITHER", SMITE)
                .put("WITHER_SKELETON", SMITE)
                .put("ZOMBIFIED_PIGLIN", SMITE)
                .put("SKELETON_HORSE", SMITE)
                .put("STRAY", SMITE)
                .put("HUSK", SMITE)
                .put("PHANTOM", SMITE)
                .put("DROWNED", SMITE)
                .put("ZOGLIN", SMITE)
                .put("ZOMBIE_HORSE", SMITE)
                .put("ZOMBIE_VILLAGER", SMITE)

                // Arthropods (https://minecraft.gamepedia.com/Arthropod)
                .put("SPIDER", BANE_OF_ARTHROPODS)
                .put("CAVE_SPIDER", BANE_OF_ARTHROPODS)
                .put("BEE", BANE_OF_ARTHROPODS)
                .put("SILVERFISH", BANE_OF_ARTHROPODS)
                .put("ENDERMITE", BANE_OF_ARTHROPODS)

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

        if (enchantment == null || enchantment != SMITE || enchantment != BANE_OF_ARTHROPODS)
            return 0;

        return 2.5 * item.getEnchantmentLevel(enchantment);
    }

}
