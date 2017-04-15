package kernitus.plugin.OldCombatMechanics.module;

import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import kernitus.plugin.OldCombatMechanics.OCMMain;

public class ModuleProjectileKnockback extends Module {

	public ModuleProjectileKnockback(OCMMain plugin) {
		super(plugin, "projectile-knockback");
	}

	@EventHandler(priority=EventPriority.NORMAL)
	public void onEntityHit(EntityDamageByEntityEvent e){
		if(!isEnabled(e.getEntity().getWorld())) return;

		EntityType type = e.getDamager().getType();

		switch(type){
		case SNOWBALL: case EGG: case ENDER_PEARL:
			e.setDamage(module().getDouble("damage." + type.toString().toLowerCase()));
		default:
			break;
		}

	}
}