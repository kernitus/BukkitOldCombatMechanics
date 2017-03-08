package kernitus.plugin.OldCombatMechanics.module;

import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.projectiles.ProjectileSource;

import kernitus.plugin.OldCombatMechanics.OCMMain;

public class ModuleDisableBowBoost extends Module {


	public ModuleDisableBowBoost(OCMMain plugin) {
		super(plugin, "disable-bow-boost");
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onProjectileHit(EntityDamageByEntityEvent e){
		Entity entity = e.getEntity();
		Entity damager = e.getDamager();
		if(entity != null && entity instanceof Player && damager.getType().equals(EntityType.ARROW)){
			Player player = (Player) entity;
			if(isEnabled(player.getWorld())){
				ProjectileSource shooter = ((Arrow) damager).getShooter();
				if(shooter instanceof Player){
					Player shootingPlayer = (Player) shooter;
					if(player.getUniqueId().equals(shootingPlayer.getUniqueId())){
						e.setCancelled(true);
						debug("We cancelled your bow boost", player);
					}
				}
			}
		}
	}
}