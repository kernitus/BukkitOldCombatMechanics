/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module

import kernitus.plugin.OldCombatMechanics.OCMMain
import kernitus.plugin.OldCombatMechanics.utilities.Messenger.send
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.entity.EnderPearl
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.entity.ProjectileLaunchEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.ItemStack
import java.util.*
import kotlin.math.max

/**
 * Allows you to throw enderpearls as often as you like, not only after a cooldown.
 */
class ModuleDisableEnderpearlCooldown(plugin: OCMMain) : OCMModule(plugin, "disable-enderpearl-cooldown") {
    /**
     * Contains players that threw an ender pearl. As the handler calls launchProjectile,
     * which also calls ProjectileLaunchEvent, we need to ignore that event call.
     */
    private val ignoredPlayers: MutableSet<UUID> = HashSet()
    private var lastLaunched: MutableMap<UUID, Long>? = null
    private var cooldown = 0
    private var message: String? = null

    companion object {
        lateinit var instance: ModuleDisableEnderpearlCooldown
    }

    init {
        instance = this
        reload()
    }

    override fun reload() {
        cooldown = module().getInt("cooldown")
        if (cooldown > 0) {
            if (lastLaunched == null) lastLaunched = WeakHashMap()
        } else lastLaunched = null

        message = if (module().getBoolean("showMessage")) module().getString("message") else null
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerShoot(e: ProjectileLaunchEvent) {
        val projectile = e.entity as? EnderPearl ?: return
        val shooter = projectile.shooter as? Player ?: return

        if (!isEnabled(shooter)) return

        val uuid = shooter.uniqueId

        if (ignoredPlayers.contains(uuid)) return

        e.isCancelled = true

        // Check if the cooldown has expired yet
        val currentTime = System.currentTimeMillis() / 1000

        lastLaunched?.get(uuid)?.let { lastLaunchedValue ->
            val elapsedSeconds = currentTime - lastLaunchedValue
            if (elapsedSeconds < cooldown) {
                message?.let {
                    send(shooter, it, cooldown - elapsedSeconds)
                    return
                }
            }
        }

        lastLaunched?.let { it[uuid] = currentTime }

        // Make sure we ignore the event triggered by launchProjectile
        ignoredPlayers.add(uuid)
        val pearl = shooter.launchProjectile(EnderPearl::class.java)
        ignoredPlayers.remove(uuid)

        pearl.velocity = shooter.eyeLocation.direction.multiply(2)

        if (shooter.gameMode == GameMode.CREATIVE) return

        val enderpearlItemStack: ItemStack
        val playerInventory = shooter.inventory
        val mainHand = playerInventory.itemInMainHand
        val offHand = playerInventory.itemInOffHand

        enderpearlItemStack = if (isEnderPearl(mainHand)) mainHand
        else if (isEnderPearl(offHand)) offHand
        else return

        enderpearlItemStack.amount -= 1
    }

    private fun isEnderPearl(itemStack: ItemStack?) = itemStack?.type == Material.ENDER_PEARL

    @EventHandler
    fun onPlayerQuit(e: PlayerQuitEvent) {
        lastLaunched?.remove(e.player.uniqueId)
    }

    /**
     * Get the remaining cooldown time for ender pearls for a given player.
     * @param playerUUID The UUID of the player to check the cooldown for.
     * @return The remaining cooldown time in seconds, or 0 if there is no cooldown, or it has expired.
     */
    fun getEnderpearlCooldown(playerUUID: UUID): Long {
        lastLaunched?.get(playerUUID)?.let { lastLaunchedValue ->
            val currentTime = System.currentTimeMillis() / 1000 // Current time in seconds
            val lastLaunchTime = lastLaunchedValue // Last launch time in seconds
            val elapsedSeconds = currentTime - lastLaunchTime
            val cooldownRemaining = cooldown - elapsedSeconds
            return max(
                cooldownRemaining.toDouble(), 0.0
            ).toLong() // Return the remaining cooldown or 0 if it has expired
        }
        return 0
    }
}
