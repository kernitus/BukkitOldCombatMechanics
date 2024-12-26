/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module

import kernitus.plugin.OldCombatMechanics.OCMMain
import kernitus.plugin.OldCombatMechanics.utilities.MathsHelper.clamp
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.event.player.PlayerTeleportEvent
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.min

/**
 * A module to control chorus fruits.
 */
class ModuleChorusFruit(plugin: OCMMain) : OCMModule(plugin, "chorus-fruit") {
    @EventHandler
    fun onEat(e: PlayerItemConsumeEvent) {
        if (e.item.type != Material.CHORUS_FRUIT) return
        val player = e.player

        if (!isEnabled(player)) return

        if (module().getBoolean("prevent-eating")) {
            e.isCancelled = true
            return
        }

        val hungerValue = module().getInt("hunger-value")
        val saturationValue = module().getDouble("saturation-value")
        val previousFoodLevel = player.foodLevel
        val previousSaturation = player.saturation

        // Run it on the next tick to reset things while not cancelling the chorus fruit eat event
        // This ensures the teleport event is fired and counts towards statistics
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            val newFoodLevel = min((hungerValue + previousFoodLevel).toDouble(), 20.0).toInt()
            val newSaturation =
                min((saturationValue + previousSaturation).toFloat().toDouble(), newFoodLevel.toDouble()).toFloat()

            player.foodLevel = newFoodLevel
            player.saturation = newSaturation
            debug("Food level changed from: " + previousFoodLevel + " to " + player.foodLevel, player)
        }, 2L)
    }

    @EventHandler
    fun onTeleport(e: PlayerTeleportEvent) {
        if (e.cause != PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) return

        val player = e.player
        if (!isEnabled(player)) return

        val distance = maxTeleportationDistance

        if (distance == 8.0) {
            debug("Using vanilla teleport implementation!", player)
            return
        }

        if (distance <= 0) {
            debug("Chorus teleportation is not allowed", player)
            e.isCancelled = true
            return
        }

        val toLocation = e.to
        val world = toLocation?.world
        if (world == null) {
            debug("Chorus teleportation cancelled due to null world or location", player)
            return
        }
        val maxHeight = world.maxHeight

        e.setTo(
            player.location.add(
                ThreadLocalRandom.current().nextDouble(-distance, distance),
                clamp(ThreadLocalRandom.current().nextDouble(-distance, distance), 0.0, (maxHeight - 1).toDouble()),
                ThreadLocalRandom.current().nextDouble(-distance, distance)
            )
        )
    }

    private val maxTeleportationDistance: Double
        get() = module().getDouble("max-teleportation-distance")
}
