package gvlfm78.plugin.OldCombatMechanics.module;

import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.projectiles.ProjectileSource;

import gvlfm78.plugin.OldCombatMechanics.OCMMain;

public class ModuleDisableBowBoost extends Module {


	public ModuleDisableBowBoost(OCMMain plugin) {
		super(plugin, "disable-bow-boost");
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onProjectileHit(EntityDamageByEntityEvent e) {
		if (!(e.getEntity() instanceof Player)) {
			return;
		}

		Player player = (Player) e.getEntity();

		if (!isEnabled(player.getWorld())) {
			return;
		}

		if (!(e.getDamager() instanceof Arrow)) {
			return;
		}

		Arrow arrow = (Arrow) e.getDamager();

		ProjectileSource shooter = arrow.getShooter();
		if (shooter instanceof Player) {
			Player shootingPlayer = (Player) shooter;
			if (player.getUniqueId().equals(shootingPlayer.getUniqueId())) {
				e.setCancelled(true);
				debug("We cancelled your bow boost", player);
			}
		}
	}
}