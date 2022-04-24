/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDamageEvent.DamageModifier;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Allows customising the shield damage reduction percentages.
 */
public class ModuleShieldDamageReduction extends Module {

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
        if (!isEnabled(player.getWorld())) return;
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
        Entity entity = e.getEntity();

        if (!(entity instanceof Player)) return;

        final Player player = (Player) entity;

        if (!isEnabled(player.getWorld())) return;

        if (!shieldBlockedDamage(e)) return;

        // Instead of reducing damage to 33% apply config reduction
        final double damageReduction = getDamageReduction(e.getDamage(), e.getCause());

        // Also make sure reducing the damage doesn't result in negative damage
        e.setDamage(DamageModifier.BLOCKING, 0);

        if (e.getFinalDamage() >= damageReduction)
            e.setDamage(DamageModifier.BLOCKING, -damageReduction);

        debug("Damage reduced by: " + e.getDamage(DamageModifier.BLOCKING) + " to " + e.getFinalDamage(), player);

        final UUID uuid = player.getUniqueId();

        if (e.getFinalDamage() <= 0) { // Make sure armour is not damaged if fully blocked
            final List<ItemStack> armour = Arrays.stream(player.getInventory().getArmorContents()).filter(Objects::nonNull).collect(Collectors.toList());
            fullyBlocked.put(uuid, armour);
            new BukkitRunnable() {
                @Override
                public void run() {
                    fullyBlocked.remove(uuid);
                    debug("Removed from fully blocked set!", player);
                }
            }.runTaskLater(plugin, 1);
        }
    }

    private double getDamageReduction(double fullDamage, DamageCause damageCause) {
        // 1.8 NMS code, where f is damage done: f = (1.0F + f) * 0.5F;

        // Reduce by amount
        fullDamage -= damageCause == DamageCause.PROJECTILE ? projectileDamageReductionAmount : genericDamageReductionAmount;

        // Reduce by percentage
        fullDamage *= (damageCause == DamageCause.PROJECTILE ? projectileDamageReductionPercentage : genericDamageReductionPercentage) / 100F;

        // Don't reduce by more than the actual damage done
        if (fullDamage < 0) fullDamage = 0;

        return fullDamage;
    }

    private boolean shieldBlockedDamage(EntityDamageByEntityEvent e) {
        // Only reduce damage if they were hit head on, i.e. the shield blocked some of the damage
        return e.getDamage() > 0 && e.getDamage(DamageModifier.BLOCKING) < 0;
    }
}
