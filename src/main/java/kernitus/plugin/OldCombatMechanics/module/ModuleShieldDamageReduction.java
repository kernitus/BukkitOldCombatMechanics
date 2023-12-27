/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
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

import java.util.*;
import java.util.stream.Collectors;

/**
 * Allows customising the shield damage reduction percentages.
 */
public class ModuleShieldDamageReduction extends OCMModule {

    private int genericDamageReductionAmount, genericDamageReductionPercentage, projectileDamageReductionAmount, projectileDamageReductionPercentage;
    private final Map<UUID, List<ItemStack>> fullyBlocked = new WeakHashMap<>();

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
            final List<ItemStack> armour = fullyBlocked.get(uuid);
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
            fullyBlocked.put(uuid, armour);

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                fullyBlocked.remove(uuid);
                debug("Removed from fully blocked set!", player);
            }, 1L);
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
