/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import com.cryptomorin.xseries.XEntityType;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.SpigotFunctionChooser;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import java.util.ArrayDeque;
import java.util.Deque;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.util.Vector;

/**
 * Brings back the old fishing-rod knockback.
 */
public class ModuleFishingKnockback extends OCMModule {

    private final SpigotFunctionChooser<PlayerFishEvent, Object, Entity> getHookFunction;
    private final SpigotFunctionChooser<ProjectileHitEvent, Object, Entity> getHitEntityFunction;
    private boolean knockbackNonPlayerEntities;
    private final Deque<RodDamageAttempt> damageAttempts = new ArrayDeque<>();

    public ModuleFishingKnockback(OCMMain plugin) {
        super(plugin, "old-fishing-knockback");

        reload();

        getHookFunction = SpigotFunctionChooser.apiCompatReflectionCall((e, params) -> e.getHook(),
                PlayerFishEvent.class, "getHook");
        getHitEntityFunction = SpigotFunctionChooser.apiCompatCall((e, params) -> e.getHitEntity(),
                (e, params) -> findNearbyHitEntity(e.getEntity()));
    }

    @Override
    public void reload() {
        knockbackNonPlayerEntities = isSettingEnabled("knockbackNonPlayerEntities");
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onRodLand(ProjectileHitEvent event) {
        final Entity hookEntity = event.getEntity();

        final EntityType fishingBobberType = XEntityType.FISHING_BOBBER.get();
        if (fishingBobberType == null || event.getEntityType() != fishingBobberType)
            return;

        final FishHook hook = (FishHook) hookEntity;

        if (!(hook.getShooter() instanceof Player))
            return;
        final Player rodder = (Player) hook.getShooter();
        if (!isEnabled(rodder))
            return;

        Entity hitEntity = getHitEntityFunction.apply(event);
        if (hitEntity == null) {
            hitEntity = findNearbyHitEntity(hookEntity);
        }

        if (hitEntity == null)
            return; // If no entity was hit
        if (!(hitEntity instanceof LivingEntity))
            return;
        final LivingEntity livingEntity = (LivingEntity) hitEntity;
        if (!knockbackNonPlayerEntities && !(hitEntity instanceof Player))
            return;

        // Do not move Citizens NPCs
        // See https://wiki.citizensnpcs.co/API#Checking_if_an_entity_is_a_Citizens_NPC
        if (hitEntity.hasMetadata("NPC"))
            return;

        if (!knockbackNonPlayerEntities) {
            final Player player = (Player) hitEntity;

            debug("You were hit by a fishing rod!", player);

            if (player.equals(rodder))
                return;

            if (player.getGameMode() == GameMode.CREATIVE)
                return;
        }

        // Check if cooldown time has elapsed
        if (livingEntity.getNoDamageTicks() > livingEntity.getMaximumNoDamageTicks() / 2f)
            return;

        double damage = module().getDouble("damage");
        if (damage < 0)
            damage = 0.0001;

        final RodDamageAttempt attempt = new RodDamageAttempt(rodder, livingEntity);
        damageAttempts.push(attempt);
        try {
            livingEntity.damage(damage, rodder);
        } finally {
            damageAttempts.pop();
        }
        // Damage can be rejected by another plugin or by the server before an event is raised.
        // Zero final damage alone is not rejection: resistance and absorption can absorb a valid hit.
        if (attempt.event == null || attempt.event.isCancelled()) return;
        livingEntity.setVelocity(
                calculateKnockbackVelocity(livingEntity.getVelocity(), livingEntity.getLocation(), hook.getLocation()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void observeRodDamage(EntityDamageByEntityEvent event) {
        final RodDamageAttempt attempt = damageAttempts.peek();
        if (attempt == null || !event.getEntity().getUniqueId().equals(attempt.victim.getUniqueId())
                || !event.getDamager().getUniqueId().equals(attempt.rodder.getUniqueId())) return;
        // Nested damage completes first; the enclosing rod event then replaces this reference.
        // Read cancellation after damage() returns, including later listeners at this priority.
        attempt.event = event;
    }

    private static final class RodDamageAttempt {
        private final Player rodder;
        private final LivingEntity victim;
        private EntityDamageByEntityEvent event;

        private RodDamageAttempt(Player rodder, LivingEntity victim) {
            this.rodder = rodder;
            this.victim = victim;
        }
    }

    private Entity findNearbyHitEntity(Entity hookEntity) {
        return hookEntity.getWorld().getNearbyEntities(hookEntity.getLocation(), 0.25, 0.25, 0.25).stream()
                .filter(entity -> knockbackNonPlayerEntities || entity instanceof Player)
                .findFirst()
                .orElse(null);
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
        if (e.getState() != PlayerFishEvent.State.CAUGHT_ENTITY)
            return;
        if (!isEnabled(e.getPlayer()))
            return;

        final String cancelDraggingIn = module().getString("cancelDraggingIn", "players");
        final boolean isPlayer = e.getCaught() instanceof HumanEntity;
        if ((cancelDraggingIn.equals("players") && isPlayer) ||
                cancelDraggingIn.equals("mobs") && !isPlayer ||
                cancelDraggingIn.equals("all")) {
            getHookFunction.apply(e).remove(); // Remove the bobber and don't do anything else
            e.setCancelled(true);
        }
    }
}
