package kernitus.plugin.OldCombatMechanics.module;

import java.util.EnumMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.projectiles.ProjectileSource;

import com.google.common.base.Functions;
import com.google.common.collect.ImmutableMap;

import kernitus.plugin.OldCombatMechanics.OCMMain;

public class ModuleProjectileKnockback extends Module {

	public ModuleProjectileKnockback(OCMMain plugin) {
		super(plugin, "projectile-knockback");
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onProjectileHit(ProjectileHitEvent e) {

		if (!isEnabled(e.getEntity().getWorld()))
			return;

		EntityType type = e.getEntityType();

		if (type != EntityType.EGG && type != EntityType.SNOWBALL && type != EntityType.ENDER_PEARL) return;

		Projectile projectile = e.getEntity();
		Entity entity = e.getHitEntity();

		if(entity==null || !(entity instanceof Player)) return; //Didn't hit any player

		Player player = (Player) entity;
		ProjectileSource shooter = projectile.getShooter();
		
		if(!(shooter instanceof Entity)) return; //For vector calculations

		if(shooter instanceof Player && entity.getUniqueId().equals( ((Player) shooter) ))
			return; //Don't allow players to damage themselves

		EntityDamageByEntityEvent event = makeEvent(projectile, player);
		Bukkit.getPluginManager().callEvent(event);

		if(module().getBoolean("checkCancelled") && event.isCancelled()) return;

		String item = type.toString().toLowerCase();
		
		double damage = module().getDouble("damage." + item);
		if(damage < 0) damage = 0.2;
		
		double knockback = module().getDouble("knockback." + item);
		
		player.damage(damage);

		Location loc = player.getLocation().add(0, 0.5, 0);
		player.teleport(loc);
		
		player.setVelocity(player.getVelocity().add(player.getLocation().toVector().subtract(((Entity) shooter).getLocation().toVector()).normalize().multiply(knockback)));
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private EntityDamageByEntityEvent makeEvent(Entity projectile, Player player) {
		return new EntityDamageByEntityEvent(projectile, player, EntityDamageEvent.DamageCause.ENTITY_ATTACK, new EnumMap(ImmutableMap.of(EntityDamageEvent.DamageModifier.BASE, Double.valueOf(0.2))), new EnumMap(ImmutableMap.of(EntityDamageEvent.DamageModifier.BASE, Functions.constant(Double.valueOf(0.2)))));
	}
}