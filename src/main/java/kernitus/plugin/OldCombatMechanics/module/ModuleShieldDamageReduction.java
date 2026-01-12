/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.module.ModuleSwordBlocking;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDamageEvent.DamageModifier;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Allows customising the shield damage reduction percentages.
 */
public class ModuleShieldDamageReduction extends OCMModule {

    private int genericDamageReductionAmount, genericDamageReductionPercentage, projectileDamageReductionAmount, projectileDamageReductionPercentage;

    // When a hit is fully blocked (0 final damage), vanilla can still damage armour durability.
    // We cancel that armour durability for one tick only (just long enough for PlayerItemDamageEvent to fire).
    //
    // Performance/correctness:
    // - Use a normal HashMap (WeakHashMap<UUID, ...> can drop entries unpredictably).
    // - Avoid scheduling one task per fully-blocked hit: keep a shared cleanup task that runs only while entries exist.
    private final Map<UUID, FullyBlockedArmour> fullyBlocked = new HashMap<>();
    private BukkitTask fullyBlockedCleanupTask;
    private long fullyBlockedTickCounter;

    public ModuleShieldDamageReduction(OCMMain plugin) {
        super(plugin, "shield-damage-reduction");
        reload();
    }

    @Override
    public void reload() {
        genericDamageReductionAmount = module().getInt("generalDamageReductionAmount", 1);
        genericDamageReductionPercentage = module().getInt("generalDamageReductionPercentage", 50);
        projectileDamageReductionAmount = module().getInt("projectileDamageReductionAmount", 1);
        projectileDamageReductionPercentage = module().getInt("projectileDamageReductionPercentage", 50);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onItemDamage(PlayerItemDamageEvent e) {
        final Player player = e.getPlayer();
        if (!isEnabled(player)) return;
        final UUID uuid = player.getUniqueId();
        final ItemStack item = e.getItem();

        if (fullyBlocked.containsKey(uuid)) {
            final FullyBlockedArmour data = fullyBlocked.get(uuid);
            if (data == null) return;
            final List<ItemStack> armour = data.armour;
            // ItemStack.equals() checks material, durability and quantity to make sure nothing changed in the meantime
            // We're checking all the pieces this way just in case they're wearing two helmets or something strange
            final List<ItemStack> matchedPieces = armour.stream().filter(piece -> piece.equals(item)).collect(Collectors.toList());
            armour.removeAll(matchedPieces);
            debug("Ignoring armour durability damage due to full block", player);
            if (!matchedPieces.isEmpty()) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onHit(EntityDamageByEntityEvent e) {
        final Entity entity = e.getEntity();

        if (!(entity instanceof Player)) return;

        final Player player = (Player) entity;

        if (!isEnabled(e.getDamager(), player)) return;

        // Paper sword blocking sets the BLOCKING modifier to emulate 1.8 sword blocking. This module is for
        // shield blocking only; do not double-apply a second reduction when the player is blocking with a sword.
        final ModuleSwordBlocking swordBlocking = ModuleSwordBlocking.getInstance();
        if (swordBlocking != null && swordBlocking.isPaperSwordBlocking(player)) return;

        // Blocking is calculated after base and hard hat, and before armour etc.
        final double baseDamage = e.getDamage(DamageModifier.BASE) + e.getDamage(DamageModifier.HARD_HAT);
        if (!shieldBlockedDamage(baseDamage, e.getDamage(DamageModifier.BLOCKING))) return;

        final double damageReduction = getDamageReduction(baseDamage, e.getCause());
        e.setDamage(DamageModifier.BLOCKING, -damageReduction);
        final double currentDamage = baseDamage - damageReduction;

        debug("Blocking: " + baseDamage + " - " + damageReduction + " = " + currentDamage, player);
        debug("Blocking: " + baseDamage + " - " + damageReduction + " = " + currentDamage);

        final UUID uuid = player.getUniqueId();

        if (currentDamage <= 0) { // Make sure armour is not damaged if fully blocked
            final List<ItemStack> armour = Arrays.stream(player.getInventory().getArmorContents()).filter(Objects::nonNull).collect(Collectors.toList());
            fullyBlocked.put(uuid, new FullyBlockedArmour(armour, fullyBlockedTickCounter + 1L));
            ensureFullyBlockedCleanupTaskRunning();
        }
    }

    private void ensureFullyBlockedCleanupTaskRunning() {
        if (fullyBlockedCleanupTask != null) return;
        fullyBlockedTickCounter = 0;

        fullyBlockedCleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            fullyBlockedTickCounter++;
            if (fullyBlocked.isEmpty()) {
                stopFullyBlockedCleanupTaskIfIdle();
                return;
            }

            final Iterator<Map.Entry<UUID, FullyBlockedArmour>> it = fullyBlocked.entrySet().iterator();
            while (it.hasNext()) {
                final FullyBlockedArmour data = it.next().getValue();
                if (data == null || data.expiresAtTick <= fullyBlockedTickCounter) {
                    it.remove();
                }
            }

            stopFullyBlockedCleanupTaskIfIdle();
        }, 1L, 1L);
    }

    private void stopFullyBlockedCleanupTaskIfIdle() {
        if (fullyBlockedCleanupTask == null) return;
        if (!fullyBlocked.isEmpty()) return;
        fullyBlockedCleanupTask.cancel();
        fullyBlockedCleanupTask = null;
    }

    private static final class FullyBlockedArmour {
        private final List<ItemStack> armour;
        private final long expiresAtTick;

        private FullyBlockedArmour(List<ItemStack> armour, long expiresAtTick) {
            this.armour = armour;
            this.expiresAtTick = expiresAtTick;
        }
    }

    private double getDamageReduction(double damage, DamageCause damageCause) {
        // 1.8 NMS code, where f is damage done, to calculate new damage.
        // f = (1.0F + f) * 0.5F;

        // We subtract, to calculate damage reduction instead of new damage
        double reduction = damage - (damageCause == DamageCause.PROJECTILE ? projectileDamageReductionAmount : genericDamageReductionAmount);

        // Reduce to percentage
        reduction *= (damageCause == DamageCause.PROJECTILE ? projectileDamageReductionPercentage : genericDamageReductionPercentage) / 100.0;

        // Don't reduce by more than the actual damage done
        // As far as I can tell this is not checked in 1.8NMS, and if the damage was low enough
        // blocking would lead to higher damage. However, this is hardly the desired result.
        if (reduction < 0) reduction = 0;

        return reduction;
    }

    private boolean shieldBlockedDamage(double attackDamage, double blockingReduction) {
        // Only reduce damage if they were hit head on, i.e. the shield blocked some of the damage
        // This also takes into account damages that are not blocked by shields
        return attackDamage > 0 && blockingReduction < 0;
    }
}
