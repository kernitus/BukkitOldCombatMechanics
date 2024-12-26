/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module

import kernitus.plugin.OldCombatMechanics.OCMMain
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.event.entity.EntityTeleportEvent
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerRespawnEvent

class ModuleAttackFrequency(plugin: OCMMain) : OCMModule(plugin, "attack-frequency") {
    companion object {
        private const val DEFAULT_DELAY = 20
        private var playerDelay = 0
        private var mobDelay = 0
    }

    init {
        reload()
    }

    override fun reload() {
        playerDelay = module().getInt("playerDelay")
        mobDelay = module().getInt("mobDelay")

        Bukkit.getWorlds().forEach { world: World ->
            world.livingEntities.forEach { livingEntity: LivingEntity ->
                if (livingEntity is Player) livingEntity.setMaximumNoDamageTicks(
                    if (isEnabled(livingEntity)) playerDelay else DEFAULT_DELAY
                )
                else livingEntity.maximumNoDamageTicks = if (isEnabled(world)) mobDelay else DEFAULT_DELAY
            }
        }
    }

    @EventHandler
    fun onPlayerJoin(e: PlayerJoinEvent) {
        val player = e.player
        if (isEnabled(player)) setDelay(player, playerDelay)
    }

    @EventHandler
    fun onPlayerLogout(e: PlayerQuitEvent) = setDelay(e.player, DEFAULT_DELAY)

    @EventHandler
    fun onPlayerChangeWorld(e: PlayerChangedWorldEvent) {
        val player = e.player
        setDelay(player, if (isEnabled(player)) playerDelay else DEFAULT_DELAY)
    }

    @EventHandler
    fun onPlayerRespawn(e: PlayerRespawnEvent) {
        val player = e.player
        setDelay(player, if (isEnabled(player)) playerDelay else DEFAULT_DELAY)
    }

    private fun setDelay(player: Player, delay: Int) {
        player.maximumNoDamageTicks = delay
        debug("Set hit delay to $delay", player)
    }

    @EventHandler
    fun onCreatureSpawn(e: CreatureSpawnEvent) {
        val livingEntity = e.entity
        val world = livingEntity.world
        if (isEnabled(world)) livingEntity.maximumNoDamageTicks = mobDelay
    }

    @EventHandler
    fun onEntityTeleportEvent(e: EntityTeleportEvent) {
        // This event is only fired for non-player entities
        val entity = e.entity as? LivingEntity ?: return

        val fromWorld = e.from.world ?: return
        val toLocation = e.to ?: return
        val toWorld = toLocation.world ?: return
        if (fromWorld.uid !== toWorld.uid) entity.maximumNoDamageTicks =
            if (isEnabled(toWorld)) mobDelay else DEFAULT_DELAY
    }

}
