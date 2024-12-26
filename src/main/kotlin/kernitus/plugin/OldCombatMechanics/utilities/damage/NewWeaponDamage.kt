/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities.damage

import org.bukkit.Material

/**
 * Default 1.9 Minecraft tool damage values
 */
enum class NewWeaponDamage(val damage: Float) {
    // common values
    STONE_SWORD(5f), STONE_SHOVEL(3.5f), STONE_PICKAXE(3f), STONE_AXE(9f), STONE_HOE(1f),
    IRON_SWORD(6f), IRON_SHOVEL(4.5f), IRON_PICKAXE(4f), IRON_AXE(9f), IRON_HOE(1f),
    DIAMOND_SWORD(7f), DIAMOND_SHOVEL(5.5f), DIAMOND_PICKAXE(5f), DIAMOND_AXE(9f), DIAMOND_HOE(1f),

    // pre-1.13 values
    STONE_SPADE(3.5f), IRON_SPADE(4.5f), DIAMOND_SPADE(5.5f),
    WOOD_SWORD(4f), WOOD_SPADE(2.5f), WOOD_PICKAXE(2f), WOOD_AXE(7f), WOOD_HOE(1f),
    GOLD_SWORD(4f), GOLD_SPADE(2.5f), GOLD_PICKAXE(2f), GOLD_AXE(7f), GOLD_HOE(1f),

    // post-1.13 values
    WOODEN_SWORD(4f), WOODEN_SHOVEL(2.5f), WOODEN_PICKAXE(2f), WOODEN_AXE(7f), WOODEN_HOE(1f),
    GOLDEN_SWORD(4f), GOLDEN_SHOVEL(2.5f), GOLDEN_PICKAXE(2f), GOLDEN_AXE(7f), GOLDEN_HOE(1f),
    NETHERITE_SWORD(8f), NETHERITE_SHOVEL(6.5f), NETHERITE_PICKAXE(6f), NETHERITE_AXE(10f), NETHERITE_HOE(1f);

    companion object {
        fun getDamage(mat: String?): Float {
            return valueOf(mat!!).damage
        }

        @JvmStatic
        fun getDamage(mat: Material): Float {
            return getDamage(mat.toString())
        }
    }
}