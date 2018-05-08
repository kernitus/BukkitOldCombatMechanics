package gvlfm78.plugin.OldCombatMechanics.module;

import gvlfm78.plugin.OldCombatMechanics.OCMMain;
import gvlfm78.plugin.OldCombatMechanics.utilities.ConfigUtils;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class ModuleDisableCrafting extends Module {

    private List<Material> denied;

    public ModuleDisableCrafting(OCMMain plugin){
        super(plugin, "disable-crafting");
        reload();
    }

    @Override
    public void reload(){
        denied = ConfigUtils.loadMaterialList(module(), "denied");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareItemCraft(PrepareItemCraftEvent e){
        if(e.getViewers().size() < 1) return;

        World world = e.getViewers().get(0).getWorld();
        if(!isEnabled(world)) return;

        CraftingInventory inv = e.getInventory();
        ItemStack result = inv.getResult();

        if(result != null && denied.contains(result.getType()))
            inv.setResult(null);
    }
}