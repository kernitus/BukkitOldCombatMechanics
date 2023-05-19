/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module;

import com.google.common.base.Functions;
import com.google.common.collect.ImmutableMap;
import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.SpigotFunctionChooser;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.util.Vector;

import java.util.EnumMap;

/**
 * Brings back the old fishing-rod knockback.
 */
public class ModuleFishingKnockback extends OCMModule {

    private final SpigotFunctionChooser<PlayerFishEvent, Object, Entity> getHookFunction;
    private final SpigotFunctionChooser<ProjectileHitEvent, Object, Entity> getHitEntityFunction;
    private boolean knockbackNonPlayerEntities;

    public ModuleFishingKnockback(OCMMain plugin) {
        super(plugin, "old-fishing-knockback");

        reload();

        getHookFunction = SpigotFunctionChooser.apiCompatReflectionCall((e, params) -> e.getHook(),
                PlayerFishEvent.class, "getHook");
        getHitEntityFunction = SpigotFunctionChooser.apiCompatCall((e, params) -> e.getHitEntity(), (e, params) -> {
            final Entity hookEntity = e.getEntity();
            final World world = hookEntity.getWorld();
            return world.getNearbyEntities(hookEntity.getLocation(), 0.25, 0.25, 0.25).stream()
                    .filter(entity -> !knockbackNonPlayerEntities && entity instanceof Player)
                    .findFirst()
                    .orElse(null);
        });
    }

    @Override
    public void reload() {
        knockbackNonPlayerEntities = isSettingEnabled("knockbackNonPlayerEntities");
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onRodLand(ProjectileHitEvent e) {
        final Entity hookEntity = e.getEntity();
        final World world = hookEntity.getWorld();

        if (!isEnabled(world)) return;

        if (e.getEntityType() != EntityType.FISHING_HOOK) return;

        final Entity hitEntity = getHitEntityFunction.apply(e);

        if (hitEntity == null) return;  // If no entity was hit
        if (!(hitEntity instanceof LivingEntity)) return;
        if (!knockbackNonPlayerEntities && !(hitEntity instanceof Player)) return;

        // Do not move Citizens NPCs
        // See https://wiki.citizensnpcs.co/API#Checking_if_an_entity_is_a_Citizens_NPC
        if (hitEntity.hasMetadata("NPC")) return;

        final FishHook hook = (FishHook) hookEntity;
        final Player rodder = (Player) hook.getShooter();

        if (!knockbackNonPlayerEntities) {
            final Player player = (Player) hitEntity;

            debug("You were hit by a fishing rod!", player);

            if (player.equals(rodder)) return;

            if (player.getGameMode() == GameMode.CREATIVE) return;
        }

        final LivingEntity livingEntity = (LivingEntity) hitEntity;

        // Check if cooldown time has elapsed
        if (livingEntity.getNoDamageTicks() > livingEntity.getMaximumNoDamageTicks() / 2f) return;

        double damage = module().getDouble("damage");
        if (damage < 0) damage = 0.2;

        final EntityDamageEvent event = makeEvent(rodder, hitEntity, damage);
        Bukkit.getPluginManager().callEvent(event);

        if (module().getBoolean("checkCancelled") && event.isCancelled()) {
            debug("You can't do that here!", rodder);
            return;
        }

        livingEntity.damage(damage);
        livingEntity.setVelocity(calculateKnockbackVelocity(livingEntity.getVelocity(), livingEntity.getLocation(), hook.getLocation()));
    }

    private Vector calculateKnockbackVelocity(Vector currentVelocity, Location player, Location hook) {
        double xDistance = hook.getX() - player.getX();
        double zDistance = hook.getZ() - player.getZ();

        // ensure distance is not zero and randomise in that case (I guess?)
        while (xDistance * xDistance + zDistance * zDistance < 0.0001) {
            xDistance = (Math.random() - Math.random()) * 0.01D;
            zDistance = (Math.random() - Math.random()) * 0.01D;
        }

        final double distance = Math.sqrt(xDistance * xDistance + zDistance * zDistance);

        double y = currentVelocity.getY() / 2;
        double x = currentVelocity.getX() / 2;
        double z = currentVelocity.getZ() / 2;

        // Normalise distance to have similar knockback, no matter the distance
        x -= xDistance / distance * 0.4;

        // slow the fall or throw upwards
        y += 0.4;

        // Normalise distance to have similar knockback, no matter the distance
        z -= zDistance / distance * 0.4;

        // do not shoot too high up
        if (y >= 0.4)
            y = 0.4;

        return new Vector(x, y, z);
    }

    /**
     * This is to cancel dragging the entity closer when you reel in
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    private void onReelIn(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.CAUGHT_ENTITY) return;

        final String cancelDraggingIn = module().getString("cancelDraggingIn", "players");
        final boolean isPlayer = e.getCaught() instanceof HumanEntity;
        if ((cancelDraggingIn.equals("players") && isPlayer) ||
                cancelDraggingIn.equals("mobs") && !isPlayer ||
                cancelDraggingIn.equals("all")) {
            getHookFunction.apply(e).remove(); // Remove the bobber and don't do anything else
            e.setCancelled(true);
        }
    }

    @SuppressWarnings({"deprecation"})
    private EntityDamageEvent makeEvent(Player rodder, Entity entity, double damage) {
        if (module().getBoolean("useEntityDamageEvent"))
            return new EntityDamageEvent(entity,
                    EntityDamageEvent.DamageCause.PROJECTILE,
                    new EnumMap<>(ImmutableMap.of(EntityDamageEvent.DamageModifier.BASE, damage)),
                    new EnumMap<>(ImmutableMap.of(EntityDamageEvent.DamageModifier.BASE, Functions.constant(damage))));
        else
            return new EntityDamageByEntityEvent(rodder, entity,
                    EntityDamageEvent.DamageCause.PROJECTILE,
                    new EnumMap<>(ImmutableMap.of(EntityDamageEvent.DamageModifier.BASE, damage)),
                    new EnumMap<>(ImmutableMap.of(EntityDamageEvent.DamageModifier.BASE, Functions.constant(damage))));
    }
}