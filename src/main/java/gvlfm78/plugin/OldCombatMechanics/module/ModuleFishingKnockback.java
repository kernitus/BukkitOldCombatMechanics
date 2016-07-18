package gvlfm78.plugin.OldCombatMechanics.module;

import com.google.common.base.Functions;
import com.google.common.collect.ImmutableMap;
import gvlfm78.plugin.OldCombatMechanics.OCMMain;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;

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

        if (!isEnabled(e.getEntity().getWorld())) {
            return;
        }

        if (!(e.getEntityType() == EntityType.FISHING_HOOK)) {
            return;
        }

        Collection<Entity> entities = Bukkit.getWorld(e.getEntity().getLocation().getWorld().getName()).getNearbyEntities(e.getEntity().getLocation(), 0.25, 0.25, 0.25);

        for (Entity entity : entities) {

            if (!(entity instanceof Player)) {
                continue;
            }

            FishHook hook = (FishHook) e.getEntity();
            Player rodder = (Player) hook.getShooter();
            Player player = (Player) entity;

            if (player.getUniqueId() == rodder.getUniqueId()) {
                continue;
            }

//            player.damage(0.2);
            Bukkit.getPluginManager().callEvent(makeEvent(rodder, player));
            Location loc = player.getLocation().add(0, 0.5, 0);
            player.teleport(loc);
            player.setVelocity(loc.subtract(rodder.getLocation()).toVector().normalize().multiply(0.4));

            return;

        }

    }

    private EntityDamageByEntityEvent makeEvent(Player rodder, Player player) {

        return new EntityDamageByEntityEvent(rodder, player, EntityDamageEvent.DamageCause.ENTITY_ATTACK, new EnumMap(ImmutableMap.of(EntityDamageEvent.DamageModifier.BASE, Double.valueOf(0.2))), new EnumMap(ImmutableMap.of(EntityDamageEvent.DamageModifier.BASE, Functions.constant(Double.valueOf(0.2)))));

    }

}
