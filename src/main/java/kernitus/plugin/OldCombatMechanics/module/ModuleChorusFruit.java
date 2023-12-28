/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.MathsHelper;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.concurrent.ThreadLocalRandom;

/**
 * A module to control chorus fruits.
 */
public class ModuleChorusFruit extends OCMModule {

    public ModuleChorusFruit(OCMMain plugin) {
        super(plugin, "chorus-fruit");
    }

    @EventHandler
    public void onEat(PlayerItemConsumeEvent e) {
        if (e.getItem().getType() != Material.CHORUS_FRUIT) return;
        final Player player = e.getPlayer();

        if (!isEnabled(player)) return;

        if (module().getBoolean("prevent-eating")) {
            e.setCancelled(true);
            return;
        }

        final int hungerValue = module().getInt("hunger-value");
        final double saturationValue = module().getDouble("saturation-value");
        final int previousFoodLevel = player.getFoodLevel();
        final float previousSaturation = player.getSaturation();

        // Run it on the next tick to reset things while not cancelling the chorus fruit eat event
        // This ensures the teleport event is fired and counts towards statistics
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            final int newFoodLevel = Math.min(hungerValue + previousFoodLevel, 20);
            final float newSaturation = Math.min((float) (saturationValue + previousSaturation), newFoodLevel);

            player.setFoodLevel(newFoodLevel);
            player.setSaturation(newSaturation);

            debug("Food level changed from: " + previousFoodLevel + " to " + player.getFoodLevel(), player);
        }, 2L);
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent e) {
        if (e.getCause() != PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) return;

        final Player player = e.getPlayer();
        if (!isEnabled(player)) return;

        final double distance = getMaxTeleportationDistance();

        if (distance == 8) {
            debug("Using vanilla teleport implementation!", player);
            return;
        }

        if (distance <= 0) {
            debug("Chorus teleportation is not allowed", player);
            e.setCancelled(true);
            return;
        }

        // Not sure when this can occur, but it is marked as @Nullable
        final Location toLocation = e.getTo();

        if (toLocation == null) {
            debug("Teleport target is null", player);
            return;
        }

        final int maxheight = toLocation.getWorld().getMaxHeight();

        e.setTo(player.getLocation().add(
                ThreadLocalRandom.current().nextDouble(-distance, distance),
                MathsHelper.clamp(ThreadLocalRandom.current().nextDouble(-distance, distance), 0, maxheight - 1),
                ThreadLocalRandom.current().nextDouble(-distance, distance)
        ));
    }


    private double getMaxTeleportationDistance() {
        return module().getDouble("max-teleportation-distance");
    }
}
