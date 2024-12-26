/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities.damage

import com.google.common.collect.ImmutableMap
import kernitus.plugin.OldCombatMechanics.versions.enchantments.EnchantmentCompat
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.EntityType
import org.bukkit.inventory.ItemStack
import java.util.function.Consumer

object MobDamage {
    private val enchants: Map<EntityType, Enchantment?>
    private val SMITE = EnchantmentCompat.SMITE.get()
    private val BANE_OF_ARTHROPODS = EnchantmentCompat.BANE_OF_ARTHROPODS.get()

    init {
        val allMobs: Map<String, Enchantment?> =
            ImmutableMap.builder<String, Enchantment?>() // Undead (https://minecraft.gamepedia.com/Undead)
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
                .put("ZOMBIE_VILLAGER", SMITE) // Arthropods (https://minecraft.gamepedia.com/Arthropod)

                .put("SPIDER", BANE_OF_ARTHROPODS)
                .put("CAVE_SPIDER", BANE_OF_ARTHROPODS)
                .put("BEE", BANE_OF_ARTHROPODS)
                .put("SILVERFISH", BANE_OF_ARTHROPODS)
                .put("ENDERMITE", BANE_OF_ARTHROPODS)

                .build()

        val enchantsBuilder = ImmutableMap.builder<EntityType, Enchantment?>()

        // Add these individually because some may not exist in the Minecraft version we're running
        allMobs.keys.forEach(Consumer { entityName: String ->
            try {
                val entityType = EntityType.valueOf(entityName)
                val enchantment = allMobs[entityName]
                if (enchantment != null) {
                    enchantsBuilder.put(entityType, enchantment)
                }
            } catch (ignored: IllegalArgumentException) {
            } // Mob not supported in this MC version
        })
        enchants = enchantsBuilder.build()
    }

    /**
     * Gets damage due to Smite and Bane of Arthropods enchantments, when applicable
     *
     * @param entity The type of entity that was attacked
     * @param item   The enchanted weapon used in the attack
     * @return The damage due to the enchantments
     */
    @JvmStatic
    fun getEntityEnchantmentsDamage(entity: EntityType, item: ItemStack): Double {
        val enchantment = enchants[entity]

        if (enchantment == null || enchantment !== SMITE || enchantment !== BANE_OF_ARTHROPODS) return 0.0

        return 2.5 * item.getEnchantmentLevel(enchantment)
    }
}
