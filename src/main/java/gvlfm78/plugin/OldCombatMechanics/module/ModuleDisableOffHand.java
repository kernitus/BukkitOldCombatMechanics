package gvlfm78.plugin.OldCombatMechanics.module;

import gvlfm78.plugin.OldCombatMechanics.OCMMain;
import gvlfm78.plugin.OldCombatMechanics.utilities.ConfigUtils;
import org.bukkit.Material;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ModuleDisableOffHand extends Module {

    private List<Material> mats = new ArrayList<>();

    public ModuleDisableOffHand(OCMMain plugin){
        super(plugin, "disable-offhand");
    }

    @Override
    public void reload(){
        mats = ConfigUtils.loadMaterialList(module(), "items");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSwapHandItems(PlayerSwapHandItemsEvent e){
        if(isEnabled(e.getPlayer().getWorld()) && shouldWeCancel(e.getOffHandItem())){
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent e){
        if(!isEnabled(e.getWhoClicked().getWorld()) ||
                e.getInventory().getType() != InventoryType.CRAFTING || //Making sure it's a survival player's inventory
                e.getSlot() != 40) return; // If they didn't click into the offhand slot, return

        if(e.getClick().equals(ClickType.NUMBER_KEY) || shouldWeCancel(e.getCursor())){
            e.setResult(Event.Result.DENY);
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent e){
        if(!isEnabled(e.getWhoClicked().getWorld()) ||
                e.getInventory().getType() != InventoryType.CRAFTING ||
                !e.getInventorySlots().contains(40)) return;

        if(shouldWeCancel(e.getOldCursor())){
            e.setResult(Event.Result.DENY);
            e.setCancelled(true);
        }
    }

    private boolean shouldWeCancel(ItemStack item){
        if(item == null || item.getType() == Material.AIR){
            return false;
        }

        boolean isContained = mats.contains(item.getType());
        boolean isWhitelist = module().getBoolean("whitelist");

        return isWhitelist != isContained;
    }
}