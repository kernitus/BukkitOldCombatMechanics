package kernitus.plugin.OldCombatMechanics.module;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import kernitus.plugin.OldCombatMechanics.OCMMain;

public class ModuleDisableEnderpearlCooldown extends Module {

	public ModuleDisableEnderpearlCooldown(OCMMain plugin) {
		super(plugin, "disable-enderpearl-cooldown");

	}

	@SuppressWarnings("deprecation")
	@EventHandler
	public void onPlayerShoot(PlayerInteractEvent e) {

		Action action = e.getAction();

		if(action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

		Player player = e.getPlayer();

		if(!isEnabled(player.getWorld())) return;

		if(e.getMaterial() != Material.ENDER_PEARL) return;

		e.setCancelled(true);

		EnderPearl pearl = player.launchProjectile(EnderPearl.class);

		pearl.setVelocity(player.getEyeLocation().getDirection().multiply(2));

		GameMode mode = player.getGameMode();

		if(mode == GameMode.ADVENTURE || mode == GameMode.SURVIVAL) {
			ItemStack pearlItem = new ItemStack(Material.ENDER_PEARL);
			player.getInventory().removeItem(pearlItem);
			player.updateInventory();
		}            
	}
}
