package gvlfm78.plugin.OldCombatMechanics.module;

import gvlfm78.plugin.OldCombatMechanics.OCMMain;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public class ModuleDisableEnderpearlCooldown extends Module {

	public ModuleDisableEnderpearlCooldown(OCMMain plugin) {
		super(plugin, "disable-enderpearl-cooldown");
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerShoot(PlayerInteractEvent e) {

		Action action = e.getAction();

		if(action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

		Player player = e.getPlayer();

		if(!isEnabled(player.getWorld())) return;

		if(e.getMaterial() != Material.ENDER_PEARL) return;

		if (e.isCancelled()) return;

		e.setCancelled(true);

		EnderPearl pearl = player.launchProjectile(EnderPearl.class);

		pearl.setVelocity(player.getEyeLocation().getDirection().multiply(2));

		GameMode mode = player.getGameMode();

		if(mode == GameMode.ADVENTURE || mode == GameMode.SURVIVAL) {
			PlayerInventory inv = player.getInventory();

			boolean isInOffhand = true;
			ItemStack hand = inv.getItemInOffHand();

			if(hand.getType() != Material.ENDER_PEARL) {
				hand = inv.getItemInMainHand();
				isInOffhand = false;
			}

			hand.setAmount(hand.getAmount() - 1);

			if(isInOffhand)
				inv.setItemInOffHand(hand);
			else
				inv.setItemInMainHand(hand);
		}
	}
}
