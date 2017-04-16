package gvlfm78.plugin.OldCombatMechanics.utilities;

import org.bukkit.Material;

//Default minecraft tool damages
public enum ToolDamage {

WOODEN_SWORD(4), WOODEN_SHOVEL(2.5F), WOODEN_PICKAXE(2), WOODEN_AXE(7), WOODEN_HOE(1),
STONE_SWORD(5), STONE_SHOVEL(3.5F), STONE_PICKAXE(3), STONE_AXE(9), STONE_HOE(1),
IRON_SWORD(6), IRON_SHOVEL(4.5F), IRON_PICKAXE(4), IRON_AXE(9), IRON_HOE(1),
GOLDEN_SWORD(4), GOLDEN_SHOVEL(2.5F), GOLDEN_PICKAXE(2), GOLDEN_AXE(7), GOLDEN_HOE(1),
DIAMOND_SWORD(7), DIAMOND_SHOVEL(5.5F), DIAMOND_PICKAXE(5), DIAMOND_AXE(9), DIAMOND_HOE(1);


	private float damage;
	
	private ToolDamage(float damage){
		this.damage = damage;
	}
	
	public float getDamage(){
		return damage;
	}
	
	public static float getDamage(String mat){
		return valueOf(mat).damage;
	}
	
	public static float getDamage(Material mat){
		return getDamage(mat.toString());
	}
}