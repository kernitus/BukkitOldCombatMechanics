package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.ConfigUtils;
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
import java.util.Collection;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * Disables usage of the off hand.
 */
public class ModuleDisableOffHand extends Module {

    private static final int OFFHAND_SLOT = 40;
    private List<Material> materials = new ArrayList<>();

    public ModuleDisableOffHand(OCMMain plugin){
        super(plugin, "disable-offhand");
    }

    @Override
    public void reload(){
        materials = ConfigUtils.loadMaterialList(module(), "items");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSwapHandItems(PlayerSwapHandItemsEvent e){
        if(isEnabled(e.getPlayer().getWorld()) && shouldWeCancel(e.getOffHandItem())){
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent e){
        if(!isEnabled(e.getWhoClicked().getWorld())) return;
        final ClickType clickType = e.getClick();

        try {
            if (clickType == ClickType.SWAP_OFFHAND){
                e.setResult(Event.Result.DENY);
                e.setCancelled(true);
                return;
            }
        } catch (NoSuchFieldError ignored){} // For versions below 1.16

        if(e.getInventory().getType() != InventoryType.CRAFTING || e.getSlot() != OFFHAND_SLOT) return;

        if(clickType == ClickType.NUMBER_KEY || shouldWeCancel(e.getCursor())){
            e.setResult(Event.Result.DENY);
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent e){
        if(!isEnabled(e.getWhoClicked().getWorld())
                || e.getInventory().getType() != InventoryType.CRAFTING
                || !e.getInventorySlots().contains(OFFHAND_SLOT)) return;

        if(shouldWeCancel(e.getOldCursor())){
            e.setResult(Event.Result.DENY);
            e.setCancelled(true);
        }
    }

    private boolean shouldWeCancel(ItemStack item){
        if(item == null || item.getType() == Material.AIR){
            return false;
        }

        return !getBlockType().isAllowed(materials, item.getType());
    }

    private BlockType getBlockType(){
        return module().getBoolean("whitelist") ? BlockType.WHITELIST : BlockType.BLACKLIST;
    }

    private enum BlockType {
        WHITELIST(Collection::contains),
        BLACKLIST(not(Collection::contains));

        private BiPredicate<Collection<Material>, Material> filter;

        BlockType(BiPredicate<Collection<Material>, Material> filter){
            this.filter = filter;
        }

        /**
         * Checks whether the given material is allowed.
         *
         * @param list    the list to use for checking
         * @param toCheck the material to check
         * @return true if the item is allowed, based on the list and the current mode
         */
        boolean isAllowed(Collection<Material> list, Material toCheck){
            return filter.test(list, toCheck);
        }
    }

    private static <T, U> BiPredicate<T, U> not(BiPredicate<T, U> predicate){
        return predicate.negate();
    }
}