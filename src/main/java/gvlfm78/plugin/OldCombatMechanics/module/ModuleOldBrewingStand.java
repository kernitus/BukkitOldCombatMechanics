package gvlfm78.plugin.OldCombatMechanics.module;

import gvlfm78.plugin.OldCombatMechanics.OCMMain;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.BrewingStand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;

/**
 * Makes brewing stands not require fuel.
 */
public class ModuleOldBrewingStand extends Module {

    public ModuleOldBrewingStand(OCMMain plugin){
        super(plugin, "old-brewing-stand");
    }

    @EventHandler
    public void onBrew(BrewEvent e){
        Block block = e.getBlock();

        if(!isEnabled(block.getWorld()))
            return;

        BlockState blockState = block.getState();

        refuel(blockState);
    }

    private void refuel(BlockState blockState){
        if(!(blockState instanceof BrewingStand))
            return;

        BrewingStand brewingStand = (BrewingStand) blockState;

        brewingStand.setFuelLevel(20);
        brewingStand.update();
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent e){
        if(!isEnabled(e.getPlayer().getWorld()))
            return;

        Inventory inv = e.getInventory();

        if(inv == null) return;

        Location loc = null;

        // TODO: Why is this needed? It should just return null
        try{
            loc = inv.getLocation();
        } catch(Exception ignored){
        }

        if(loc == null) return;

        Block block = loc.getBlock();

        refuel(block.getState());
    }
}