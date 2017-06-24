package gvlfm78.plugin.OldCombatMechanics.module;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
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

import gvlfm78.plugin.OldCombatMechanics.OCMMain;
import gvlfm78.plugin.OldCombatMechanics.utilities.Config;

public class ModuleDisableElytra extends Module {

	public static ModuleSwordBlocking INSTANCE;

	public ModuleDisableElytra(OCMMain plugin){
		super(plugin, "disable-elytra");
	}

	@EventHandler (priority = EventPriority.HIGHEST)
	public void onInventoryClick(InventoryClickEvent e){
		if(!isEnabled(e.getWhoClicked().getWorld())) return;

		InventoryType type = e.getInventory().getType(); //Only if they're in their inventory, not chests etc.
		if(!type.equals(InventoryType.CRAFTING) && !type.equals(InventoryType.PLAYER)) return;

		if(e.getSlot() == 38){
			e.setCancelled(true);
			return;
		}

		if(!e.getCursor().getType().equals(Material.ELYTRA) && !e.getCurrentItem().getType().equals(Material.ELYTRA)) return;

		//Stop shift clicking elytra in
		ClickType ct = e.getClick();
		if( !ct.equals(ClickType.SHIFT_LEFT) && !ct.equals(ClickType.SHIFT_RIGHT)) return;

		e.setCancelled(true);
	}

	@EventHandler (priority = EventPriority.HIGHEST)
	public void onRightClick(PlayerInteractEvent e){

		if(!isEnabled(e.getPlayer().getWorld())) return;

		//Must not be able to right click while holding it to wear it
		Action a = e.getAction();
		if(a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;

		if(!e.getMaterial().equals(Material.ELYTRA)) return;

		Block block = e.getClickedBlock();
		if(block != null && Config.getInteractiveBlocks().contains(block.getType())) return;

		e.setCancelled(true);
	}

	//Make sure they can't click in, shift click, number key, drag in

	@EventHandler (priority = EventPriority.HIGHEST)
	public void onDrag(InventoryDragEvent e){
		if(!isEnabled(e.getWhoClicked().getWorld())) return;

		if(!e.getOldCursor().getType().equals(Material.ELYTRA)) return;

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

		if(!chestplate.getType().equals(Material.ELYTRA)) return;

		inv.setChestplate(new ItemStack(Material.AIR));

		if(inv.firstEmpty() != -1)
			inv.addItem(chestplate);
		else 
			w.dropItem(p.getLocation(), chestplate);
	}
}