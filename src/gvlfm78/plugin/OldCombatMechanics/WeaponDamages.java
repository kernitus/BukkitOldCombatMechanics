package kernitus.plugin.OldCombatMechanics;

import org.bukkit.Material;

/**
 * Created by Rayzr522 on 6/14/16.
 */
public enum WeaponDamages {
    GOLD_AXE(4),
    WOOD_AXE(4),
    STONE_AXE(5),
    IRON_AXE(6),
    DIAMOND_AXE(7);

    private int damage;

    WeaponDamages(int damage) {
        this.damage = damage;
    }

    public int getDamage() {
        return damage;
    }

    public int getDamage(Material mat) {
        try {
            return valueOf(mat.name()).damage;
        } catch (Exception e) {
            return -1;
        }
    }
}
