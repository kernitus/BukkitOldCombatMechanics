package gvlfm78.plugin.OldCombatMechanics.module;

import com.google.common.base.Functions;
import com.google.common.collect.ImmutableMap;
import gvlfm78.plugin.OldCombatMechanics.OCMMain;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.plugin.RegisteredListener;

import java.util.Collection;
import java.util.EnumMap;

/**
 * Created by Rayzr522 on 6/27/16.
 */
public class ModuleFishingKnockback extends Module {

	public ModuleFishingKnockback(OCMMain plugin) {
		super(plugin, "old-fishing-knockback");
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onRodLand(ProjectileHitEvent e) {

		Entity hookEntity = e.getEntity();
		World world = hookEntity.getWorld();

		if (!isEnabled(world)) return;

		if (e.getEntityType() != EntityType.FISHING_HOOK)
			return;


		Entity hitent = null;

		try{
			hitent = e.getHitEntity();
		}
		catch(NoSuchMethodError e1){ //For older version that don't have such method
			Collection<Entity> entities = world.getNearbyEntities(hookEntity.getLocation(), 0.25, 0.25, 0.25);

			for (Entity entity : entities) {
				if (entity instanceof Player)
					hitent = entity;
					break;

			}
		}

		if(hitent == null) return;
		if(!(hitent instanceof Player)) return;

		FishHook hook = (FishHook) hookEntity;
		Player rodder = (Player) hook.getShooter();
		Player player = (Player) hitent;

		debug("You were hit by a fishing rod!", player);

		if (player.getUniqueId().equals(rodder.getUniqueId()))
			return;

		if(player.getGameMode() == GameMode.CREATIVE) return;

		double damage = module().getDouble("damage");
		if(damage < 0) damage = 0.2;

		EntityDamageEvent event = makeEvent(rodder, player, damage);
		Bukkit.getPluginManager().callEvent(event);

		if(module().getBoolean("checkCancelled") && event.isCancelled()){

			//This is to check what plugins are listening to the event
			if(plugin.getConfig().getBoolean("debug.enabled")){
				debug("You can't do that here!", rodder);
				HandlerList hl = event.getHandlers();

				for(RegisteredListener rl : hl.getRegisteredListeners())
					debug("Plugin Listening: " + rl.getPlugin().getName(), rodder);
			}

			return; 
		}

		player.damage(damage);

		Location loc = player.getLocation().add(0, 0.5, 0);
		player.teleport(loc);
		player.setVelocity(loc.subtract(rodder.getLocation()).toVector().normalize().multiply(0.4));
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private EntityDamageEvent makeEvent(Player rodder, Player player, double damage) {

		if (module().getBoolean("useEntityDamageEvent"))
			return new EntityDamageEvent(player,
					EntityDamageEvent.DamageCause.PROJECTILE,
					new EnumMap(ImmutableMap.of(EntityDamageEvent.DamageModifier.BASE, damage)),
					new EnumMap(ImmutableMap.of(EntityDamageEvent.DamageModifier.BASE, Functions.constant(damage))));
		else
			return new EntityDamageByEntityEvent(rodder, player,
					EntityDamageEvent.DamageCause.PROJECTILE,
					new EnumMap(ImmutableMap.of(EntityDamageEvent.DamageModifier.BASE, damage)),
					new EnumMap(ImmutableMap.of(EntityDamageEvent.DamageModifier.BASE, Functions.constant(damage))));
	}
}