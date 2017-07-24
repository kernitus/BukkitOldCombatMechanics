package gvlfm78.plugin.OldCombatMechanics.module;

import gvlfm78.plugin.OldCombatMechanics.OCMMain;
import gvlfm78.plugin.OldCombatMechanics.OCMSweepTask;
import gvlfm78.plugin.OldCombatMechanics.utilities.ToolDamage;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Created by Rayzr522 on 25/06/16.
 */
public class ModuleSwordSweep extends Module {

	public ModuleSwordSweep(OCMMain plugin) {
		super(plugin, "disable-sword-sweep");
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onEntityDamaged(EntityDamageByEntityEvent e) {
		World world = e.getDamager().getWorld();

		if (!isEnabled(world)) return;

		if (!(e.getDamager() instanceof Player)) return;

		Player p = (Player) e.getDamager();
		ItemStack weapon = p.getInventory().getItemInMainHand();

		if (isHolding(weapon.getType(), "sword"))
			onSwordAttack(e, p, weapon);
	}

	private void onSwordAttack(EntityDamageByEntityEvent e, Player p, ItemStack weapon) {
		//Disable sword sweep
		int locHashCode = p.getLocation().hashCode(); // ATTACKER
		
		int level = 0;
		
		try{ //In a try catch for servers that haven't updated
		level = weapon.getEnchantmentLevel(Enchantment.SWEEPING_EDGE);
		}
		catch(NoSuchFieldError e1){ }

		float damage = ToolDamage.getDamage(weapon.getType()) * level / (level + 1) + 1;

		if (e.getDamage() == damage) {
			// Possibly a sword-sweep attack
			if (sweepTask().swordLocations.contains(locHashCode)){
				debug("Cancelling sweep...", p);
				e.setCancelled(true);
			}
		} else
			sweepTask().swordLocations.add(locHashCode);

		ModuleOldToolDamage.onAttack(e);
	}

	private OCMSweepTask sweepTask() {
		return plugin.sweepTask();
	}

	private boolean isHolding(Material mat, String type) {
		return mat.toString().endsWith("_" + type.toUpperCase());
	}

}