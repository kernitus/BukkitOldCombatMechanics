/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module

import kernitus.plugin.OldCombatMechanics.OCMMain
import kernitus.plugin.OldCombatMechanics.utilities.MathsHelper.clamp
import org.bukkit.Bukkit
import org.bukkit.attribute.Attribute
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.entity.EntityRegainHealthEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.*

/**
 * Establishes custom health regeneration rules.
 * Default values based on 1.8 from [wiki](https://minecraft.gamepedia.com/Hunger?oldid=948685)
 */
class ModulePlayerRegen(plugin: OCMMain) : OCMModule(plugin, "old-player-regen") {
    private val healTimes: MutableMap<UUID, Long> = WeakHashMap()

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onRegen(e: EntityRegainHealthEvent) {
        if (e.entityType != EntityType.PLAYER
            || e.regainReason != EntityRegainHealthEvent.RegainReason.SATIATED
        ) return

        val p = e.entity as Player
        if (!isEnabled(p)) return

        val playerId = p.uniqueId

        // We cancel the regen, but saturation and exhaustion need to be adjusted separately
        // Exhaustion is modified in the next tick, and saturation in the tick following that (if exhaustion > 4)
        e.isCancelled = true

        // Get exhaustion & saturation values before healing modifies them
        val previousExhaustion = p.exhaustion
        val previousSaturation = p.saturation

        // Check that it has been at least x seconds since last heal
        val currentTime = System.currentTimeMillis()
        val hasLastHealTime = healTimes.containsKey(playerId)
        val lastHealTime = healTimes.computeIfAbsent(playerId) { id: UUID? -> currentTime }

        debug(
            "Exh: " + previousExhaustion + " Sat: " + previousSaturation + " Time: " + (currentTime - lastHealTime),
            p
        )

        // If we're skipping this heal, we must fix the exhaustion in the following tick
        if (hasLastHealTime && currentTime - lastHealTime <= module().getLong("interval")) {
            Bukkit.getScheduler().runTaskLater(plugin, Runnable { p.exhaustion = previousExhaustion }, 1L)
            return
        }

        val maxHealth = p.getAttribute(Attribute.GENERIC_MAX_HEALTH)!!.value
        val playerHealth = p.health

        if (playerHealth < maxHealth) {
            p.health = clamp(playerHealth + module().getInt("amount"), 0.0, maxHealth)
            healTimes[playerId] = currentTime
        }

        // Calculate new exhaustion value, must be between 0 and 4. If above, it will reduce the saturation in the following tick.
        val exhaustionToApply = module().getDouble("exhaustion").toFloat()

        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            // We do this in the next tick because bukkit doesn't stop the exhaustion change when cancelling the event
            p.exhaustion = previousExhaustion + exhaustionToApply
            debug(
                "Exh before: " + previousExhaustion + " Now: " + p.exhaustion +
                        " Sat now: " + previousSaturation, p
            )
        }, 1L)
    }

    @EventHandler
    fun onPlayerQuit(e: PlayerQuitEvent) = healTimes.remove(e.player.uniqueId)
}
