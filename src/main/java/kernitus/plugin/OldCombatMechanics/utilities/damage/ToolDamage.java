/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities.damage;

import org.bukkit.Material;

/**
 * Default 1.9 Minecraft tool damage values
 */
public enum ToolDamage {

    // common values
    STONE_SWORD(5), STONE_SHOVEL(3.5F), STONE_PICKAXE(3), STONE_AXE(9), STONE_HOE(1),
    IRON_SWORD(6), IRON_SHOVEL(4.5F), IRON_PICKAXE(4), IRON_AXE(9), IRON_HOE(1),
    DIAMOND_SWORD(7), DIAMOND_SHOVEL(5.5F), DIAMOND_PICKAXE(5), DIAMOND_AXE(9), DIAMOND_HOE(1),

    // pre-1.13 values
    STONE_SPADE(3.5F), IRON_SPADE(4.5F), DIAMOND_SPADE(5.5F),
    WOOD_SWORD(4), WOOD_SPADE(2.5F), WOOD_PICKAXE(2), WOOD_AXE(7), WOOD_HOE(1),
    GOLD_SWORD(4), GOLD_SPADE(2.5F), GOLD_PICKAXE(2), GOLD_AXE(7), GOLD_HOE(1),

    // post-1.13 values
    WOODEN_SWORD(4), WOODEN_SHOVEL(2.5F), WOODEN_PICKAXE(2), WOODEN_AXE(7), WOODEN_HOE(1),
    GOLDEN_SWORD(4), GOLDEN_SHOVEL(2.5F), GOLDEN_PICKAXE(2), GOLDEN_AXE(7), GOLDEN_HOE(1),
    NETHERITE_SWORD(8), NETHERITE_SHOVEL(6.5F), NETHERITE_PICKAXE(6), NETHERITE_AXE(10), NETHERITE_HOE(1);

    private final float damage;

    ToolDamage(float damage) {
        this.damage = damage;
    }

    public static float getDamage(String mat) {
        return valueOf(mat).damage;
    }

    public static float getDamage(Material mat) {
        return getDamage(mat.toString());
    }

    public float getDamage() {
        return damage;
    }
}