/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import com.cryptomorin.xseries.XEnchantment;
import kernitus.plugin.OldCombatMechanics.utilities.damage.NewWeaponDamage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A module to disable the sweep attack.
 */
public class ModuleSwordSweep extends OCMModule {

    // Legacy (pre-1.11) sweep detection: we observe a normal sword hit, then a 1.0 sweep-damage hit follows
    // immediately afterwards from the same attacker. Track per-attacker, not by Location (yaw/pitch makes Location unstable).
    private final Set<UUID> sweepPrimedAttackers = new HashSet<>();
    private EntityDamageEvent.DamageCause sweepDamageCause;
    private BukkitTask pendingClearTask;

    public ModuleSwordSweep(OCMMain plugin) {
        super(plugin, "disable-sword-sweep");

        try {
            // Available from 1.11 onwards
            sweepDamageCause = EntityDamageEvent.DamageCause.valueOf("ENTITY_SWEEP_ATTACK");
        } catch (IllegalArgumentException e) {
            sweepDamageCause = null;
        }

        reload();
    }

    @Override
    public void reload() {
        // we didn't set anything up in the first place
        if (sweepDamageCause != null) return;

        if (pendingClearTask != null) {
            pendingClearTask.cancel();
            pendingClearTask = null;
        }
        sweepPrimedAttackers.clear();
    }


    //Changed from HIGHEST to LOWEST to support DamageIndicator plugin
    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityDamaged(EntityDamageByEntityEvent e) {
        final Entity damager = e.getDamager();

        if (!(damager instanceof Player)) return;
        if (!isEnabled(damager, e.getEntity())) return;

        if (sweepDamageCause != null) {
            if (e.getCause() == sweepDamageCause) {
                e.setCancelled(true);
                debug("Sweep cancelled", damager);
            }
            // sweep attack detected or not, we do not need to fall back to the guessing implementation
            return;
        }

        final Player attacker = (Player) e.getDamager();
        final ItemStack weapon = attacker.getInventory().getItemInMainHand();

        if (isHoldingSword(weapon.getType()))
            onSwordAttack(e, attacker, weapon);
    }

    private void onSwordAttack(EntityDamageByEntityEvent e, Player attacker, ItemStack weapon) {
        //Disable sword sweep
        int level = 0;

        final Enchantment sweepingEdge = XEnchantment.SWEEPING_EDGE.getEnchant();
        if (sweepingEdge != null) {
            level = weapon.getEnchantmentLevel(sweepingEdge);
        }

        final Float baseDamage = NewWeaponDamage.getDamageOrNull(weapon.getType());
        if (baseDamage == null) {
            debug("Unknown sword in NewWeaponDamage: " + weapon.getType() + " (passing through)", attacker);
            return;
        }
        final float damage = baseDamage * level / (level + 1) + 1;

        if (e.getDamage() == damage) {
            // Possibly a sword-sweep attack
            if (sweepPrimedAttackers.contains(attacker.getUniqueId())) {
                debug("Cancelling sweep...", attacker);
                e.setCancelled(true);
            }
        } else {
            sweepPrimedAttackers.add(attacker.getUniqueId());
            scheduleClearNextTickIfNeeded();
        }
    }

    private void scheduleClearNextTickIfNeeded() {
        if (pendingClearTask != null) return;
        pendingClearTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            sweepPrimedAttackers.clear();
            pendingClearTask = null;
        }, 1L);
    }

    private boolean isHoldingSword(Material mat) {
        return mat.toString().endsWith("_SWORD");
    }
}
