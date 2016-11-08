package gvlfm78.plugin.OldCombatMechanics.module;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.PrepareItemCraftEvent;

import gvlfm78.plugin.OldCombatMechanics.OCMMain;


/**
 * Created by Detobel36 on 27/10/16
 * Changed by gvlfm78
 */

public class ModuleShieldCrafting extends Module {

	public ModuleShieldCrafting(OCMMain plugin) {
		super(plugin, "disable-shield-craft");
	}

	/*public void removeShieldCrafting() {
		// Remove Recipes
		Iterator<Recipe> recipes = plugin.getServer().recipeIterator();

		while (recipes.hasNext()) {
			Recipe recipe = recipes.next();
			ItemStack result = recipe.getResult();

			if (result.getType() == Material.SHIELD) {
				recipes.remove();
				break;
			}
		}
	}*/
	@EventHandler (priority = EventPriority.HIGHEST)
	public void onItemCraft (PrepareItemCraftEvent e) {
	        if (e.getViewers().size() < 1) {
	                return;
	        }
		World world = e.getViewers().get(0).getWorld();
		if(!isEnabled(world)) return;

		if(e.getRecipe().getResult().getType().equals(Material.SHIELD)) {
			e.getInventory().setResult(null);
		}
	}
}