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
    
    private static OCMMain plugin;
    
    public ModuleShieldCrafting(OCMMain plugin) {
        super(plugin, "disable-shield-craft");
        this.plugin = plugin;
        
        removeShieldCrafting();
    }
    
    public static void removeShieldCrafting() {
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
