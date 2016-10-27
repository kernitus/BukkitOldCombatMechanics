package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import org.bukkit.inventory.Recipe;
import java.util.Iterator;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;


/**
 * Created by Detobel36 on 10/27/16.
 */

public class ModuleShieldCrafting extends Module {

	public ModuleShieldCrafting(OCMMain plugin) {
		super(plugin, "disable-shield-craft");
		removeShieldCrafting();
	}

	public void removeShieldCrafting() {
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
	}
}