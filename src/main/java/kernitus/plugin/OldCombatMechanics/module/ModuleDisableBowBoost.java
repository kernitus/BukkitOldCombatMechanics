/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.projectiles.ProjectileSource;

/**
 * Prevents players from propelling themselves forward by shooting themselves.
 */
public class ModuleDisableBowBoost extends OCMModule {

    public ModuleDisableBowBoost(OCMMain plugin) {
        super(plugin, "disable-bow-boost");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onProjectileHit(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player)) return;

        final Player player = (Player) e.getEntity();

        if (!(e.getDamager() instanceof Arrow)) return;

        final Arrow arrow = (Arrow) e.getDamager();

        final ProjectileSource shooter = arrow.getShooter();

        if (shooter instanceof Player) {
            final Player shootingPlayer = (Player) shooter;
            if (player.getUniqueId().equals(shootingPlayer.getUniqueId())) {
                if (!isEnabled(player)) return;

                e.setCancelled(true);
                debug("We cancelled your bow boost", player);
            }
        }
    }
}