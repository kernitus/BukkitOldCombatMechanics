package kernitus.plugin.OldCombatMechanics.module;

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
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.ItemUtils;

/**
 * Created by Rayzr522 on 7/4/16.
 */
public class ModuleSwordBlocking extends Module {

	private static ModuleSwordBlocking INSTANCE;

	private static final ItemStack SHIELD = ItemUtils.makeItem("shield");

	private HashMap<UUID, ItemStack> storedOffhandItems = new HashMap<UUID, ItemStack>();

	public ModuleSwordBlocking(OCMMain plugin) {
		super(plugin, "sword-blocking");
		INSTANCE = this;
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onRightClick(PlayerInteractEvent e) {

		if (!e.getAction().toString().startsWith("RIGHT_CLICK")) {
			return;
		}

		if (e.getItem() == null) {
			return;
		}

		Player p = e.getPlayer();
		World world = p.getWorld();

		if (!isEnabled(world)) {
			return;
		}

		UUID id = p.getUniqueId();

		if (storedOffhandItems.containsKey(id)) {
			return;
		}

		ItemStack item = e.getItem();

		if (!isHolding(item.getType(), "sword") || hasShield(p)) {
			return;
		}

		PlayerInventory inv = p.getInventory();

		storedOffhandItems.put(id, inv.getItemInOffHand());

		inv.setItemInOffHand(SHIELD);

		scheduleRestore(p);

	}
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onEntityDamaged(EntityDamageEvent e){
		Entity entity = e.getEntity();
		if(storedOffhandItems.containsKey(entity.getUniqueId())){
			Player p = (Player) e.getEntity();
			//Check if they are holding a shield
			if(p.getInventory().getItemInOffHand().getType().equals(Material.SHIELD)){
				double damage = (e.getDamage()/0.33D)-1;
				if(damage < 0) damage = 0;
				e.setDamage(damage);
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

		if (!storedOffhandItems.containsKey(e.getEntity().getUniqueId())) return;

		Player p = e.getEntity();
		UUID id = p.getUniqueId();

		e.getDrops().remove(SHIELD);
		e.getDrops().add(storedOffhandItems.get(id));

		storedOffhandItems.remove(id);

	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent e){
		Player p = e.getPlayer();
		if (storedOffhandItems.containsKey(p.getUniqueId()))
			e.setCancelled(true);

	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onInventoryClick(InventoryClickEvent e){

		if(e.getWhoClicked() instanceof Player){
			Player p = (Player) e.getWhoClicked();

			if (storedOffhandItems.containsKey(p.getUniqueId())){
				if(e.getCursor().getType().equals(Material.SHIELD) || e.getCurrentItem().getType().equals(Material.SHIELD)){
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

			if (storedOffhandItems.containsKey(p.getUniqueId())){
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

		if (!storedOffhandItems.containsKey(id)) {
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

	private boolean hasShield(Player p) {
		return p.getInventory().getItemInOffHand().getType() == Material.SHIELD;
	}

	private boolean isHolding(Material mat, String type) {
		return mat.toString().endsWith("_" + type.toUpperCase());
	}

}
