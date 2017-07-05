package gvlfm78.plugin.OldCombatMechanics.module;

import gvlfm78.plugin.OldCombatMechanics.OCMMain;
import gvlfm78.plugin.OldCombatMechanics.utilities.Config;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public class ModuleDisableElytra extends Module {

	public static ModuleSwordBlocking INSTANCE;

	public ModuleDisableElytra(OCMMain plugin){
		super(plugin, "disable-elytra");
	}

	@EventHandler (priority = EventPriority.HIGHEST)
	public void onInventoryClick(InventoryClickEvent e){
		HumanEntity human = e.getWhoClicked();
		if(!isEnabled(human.getWorld())) return;

		if(!(human instanceof Player)) return;

		Player p = (Player) human;

		if(p.getGameMode() == GameMode.CREATIVE) return;

		InventoryType type = e.getInventory().getType(); // Only if they're in their inventory, not chests etc.
		if(type != InventoryType.CRAFTING && type != InventoryType.PLAYER) return;

		ItemStack cursor = e.getCursor();
		ItemStack currentItem = e.getCurrentItem();

		if((cursor != null && cursor.getType() != Material.ELYTRA) && (currentItem != null && currentItem.getType() != Material.ELYTRA)) return;

		if(e.getSlot() == 38){
			e.setCancelled(true);
			//((Player) e.getWhoClicked()).updateInventory();
			return;
		}

		//Stop shift clicking elytra in
		if(e.getClick() != ClickType.SHIFT_LEFT && e.getClick() != ClickType.SHIFT_RIGHT) return;

		e.setCancelled(true);
	}

	@EventHandler (priority = EventPriority.HIGHEST)
	public void onRightClick(PlayerInteractEvent e){

		if(!isEnabled(e.getPlayer().getWorld())) return;

		//Must not be able to right click while holding it to wear it
		Action a = e.getAction();
        if(a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;

		if(e.getMaterial() != Material.ELYTRA) return;

		Block block = e.getClickedBlock();
		if(block != null && Config.getInteractiveBlocks().contains(block.getType())) return;

		e.setCancelled(true);
	}

	//Make sure they can't click in, shift click, number key, drag in

	@EventHandler (priority = EventPriority.HIGHEST)
	public void onDrag(InventoryDragEvent e){
		if(!isEnabled(e.getWhoClicked().getWorld())) return;

		if(e.getOldCursor() == null || (e.getCursor() != null && e.getCursor().getType() != Material.ELYTRA)) return;

		if(!e.getInventorySlots().contains(38)) return;

		e.setCancelled(true);
	}

	@EventHandler (priority = EventPriority.HIGHEST)
	public void onWorldChange(PlayerChangedWorldEvent e){
		Player p = e.getPlayer();
		World w = p.getWorld();
		if(!isEnabled(w)) return;

		PlayerInventory inv = p.getInventory();

		ItemStack chestplate = inv.getChestplate();

		if(chestplate == null || chestplate.getType() != Material.ELYTRA) return;

		inv.setChestplate(new ItemStack(Material.AIR));

		if(inv.firstEmpty() != -1)
			inv.addItem(chestplate);
		else
			w.dropItem(p.getLocation(), chestplate);
	}
}