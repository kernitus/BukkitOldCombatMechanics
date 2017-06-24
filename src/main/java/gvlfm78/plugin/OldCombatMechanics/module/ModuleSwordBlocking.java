package gvlfm78.plugin.OldCombatMechanics.module;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageModifier;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import gvlfm78.plugin.OldCombatMechanics.OCMMain;
import gvlfm78.plugin.OldCombatMechanics.utilities.Config;
import gvlfm78.plugin.OldCombatMechanics.utilities.ItemUtils;

/**
 * Created by Rayzr522 on 7/4/16.
 */
public class ModuleSwordBlocking extends Module {

	public static ModuleSwordBlocking INSTANCE;

	private static final ItemStack SHIELD = ItemUtils.makeItem("shield");

	private HashMap<UUID, ItemStack> storedOffhandItems = new HashMap<UUID, ItemStack>();

	public ModuleSwordBlocking(OCMMain plugin) {
		super(plugin, "sword-blocking");
		INSTANCE = this;
	}

	@EventHandler(priority = EventPriority.HIGH)
	public void onRightClick(PlayerInteractEvent e) {

		if (e.getItem() == null) return;

		Action action = e.getAction();

		if(!action.equals(Action.RIGHT_CLICK_AIR) && !action.equals(Action.RIGHT_CLICK_BLOCK)) return;

		if (action.equals(Action.RIGHT_CLICK_BLOCK) && Config.getInteractiveBlocks().contains(e.getClickedBlock().getType())) return;

		Player p = e.getPlayer();
		World world = p.getWorld();

		if (!isEnabled(world)) return;

		UUID id = p.getUniqueId();

		if (isBlocking(id)) return;

		ItemStack item = e.getItem();

		if (!isHolding(item.getType(), "sword") || hasShield(p)) return;

		PlayerInventory inv = p.getInventory();

		storedOffhandItems.put(id, inv.getItemInOffHand());

		inv.setItemInOffHand(SHIELD);

		scheduleRestore(p);

	}
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onHit(EntityDamageByEntityEvent e){
		Entity ent = e.getEntity();

		if(ent != null && ent instanceof Player){
			Player p = (Player) ent;
			if(isBlocking(p.getUniqueId())){
				//If it's a player blocking with their sword
				//Instead of reducing damage to 33% just remove half a heart

				double damageReduction = e.getDamage(); //Reducing by this would mean blocking all damage

				//Reduce the damage by 1/2 a heart if it doesn't result in the damage being negative
				//Otherwise remove damage entirely
				if((damageReduction - 1) >= 0)
					damageReduction = -1; //-1 because it is a reduction value

				//Only reduce damage if they were hit head on, i.e. the shield blocked some of the damage
				if(e.getDamage(DamageModifier.BLOCKING) > 0)			
					e.setDamage(DamageModifier.BLOCKING, damageReduction);
			}
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onWorldChange(PlayerChangedWorldEvent e) {
		restore(e.getPlayer());
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerLogout(PlayerQuitEvent e) {
		restore(e.getPlayer());
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerDeath(PlayerDeathEvent e) {
		if (!isBlocking(e.getEntity().getUniqueId())) return;

		Player p = e.getEntity();
		UUID id = p.getUniqueId();

		e.getDrops().replaceAll(item -> {
			if(item.getType().equals(Material.SHIELD)){
				item = storedOffhandItems.get(id);
			}

			return item;
		}
				);

		storedOffhandItems.remove(id);
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent e){
		Player p = e.getPlayer();
		if (isBlocking(p.getUniqueId()))
			e.setCancelled(true);

	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onInventoryClick(InventoryClickEvent e){

		if(e.getWhoClicked() instanceof Player){
			Player p = (Player) e.getWhoClicked();

			if (isBlocking(p.getUniqueId())){
				ItemStack cursor = e.getCursor();
				ItemStack current = e.getCurrentItem();
				if(cursor!=null && cursor.getType().equals(Material.SHIELD) || current!=null && current.getType().equals(Material.SHIELD)){
					e.setCancelled(true);
					restore(p);
				}
			}
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onItemDrop(PlayerDropItemEvent e){
		Item is = e.getItemDrop();

		Player p = e.getPlayer();

		if (isBlocking(p.getUniqueId())){
			if(is.getType().equals(Material.SHIELD)){
				e.setCancelled(true);
				restore(p);
			}
		}
	}

	private void scheduleRestore(final Player p) {

		Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
			public void run() {
				restore(p);
			}
		}, 60);

	}

	private void restore(Player p) {

		UUID id = p.getUniqueId();

		if (!isBlocking(id)) {
			return;
		}

		p.getInventory().setItemInOffHand(storedOffhandItems.get(id));
		storedOffhandItems.remove(id);

	}

	public static void RestoreAll() {
		INSTANCE.restoreAll();
	}

	public void restoreAll() {
		for (Map.Entry<UUID, ItemStack> entry : storedOffhandItems.entrySet()) {

			UUID id = entry.getKey();
			Player p = Bukkit.getPlayer(id);

			p.getInventory().setItemInOffHand(storedOffhandItems.get(id));

			storedOffhandItems.remove(id);
		}
	}

	private boolean isBlocking(UUID uuid){
		return storedOffhandItems.containsKey(uuid);
	}

	private boolean hasShield(Player p) {
		return p.getInventory().getItemInOffHand().getType() == Material.SHIELD;
	}

	private boolean isHolding(Material mat, String type) {
		return mat.toString().endsWith("_" + type.toUpperCase());
	}
}
