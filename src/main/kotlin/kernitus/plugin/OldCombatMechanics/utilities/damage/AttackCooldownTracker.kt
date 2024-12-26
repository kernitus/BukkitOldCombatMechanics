package kernitus.plugin.OldCombatMechanics.utilities.damage

import kernitus.plugin.OldCombatMechanics.OCMMain
import kernitus.plugin.OldCombatMechanics.module.OCMModule
import kernitus.plugin.OldCombatMechanics.utilities.reflection.VersionCompatUtils
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.player.PlayerQuitEvent
import java.util.*

/**
 * Spigot versions below 1.16 did not have way of getting attack cooldown.
 * Obtaining through NMS works, but value is reset before EntityDamageEvent is called.
 * This means we must keep track of the cooldown to get the correct values.
 */
class AttackCooldownTracker(plugin: OCMMain) : OCMModule(plugin, "attack-cooldown-tracker") {
    private val lastCooldown: MutableMap<UUID, Float>

    init {
        INSTANCE = this
        lastCooldown = WeakHashMap()

        val cooldownTask = Runnable {
            Bukkit.getOnlinePlayers().forEach { player: Player? ->
                lastCooldown[player!!.uniqueId] = VersionCompatUtils.getAttackCooldown(player)
            }
        }
        Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, cooldownTask, 0, 1L)
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        lastCooldown.remove(event.player.uniqueId)
    }

    // Module is always enabled, because it will only be in list of modules if server
    // itself requires it (i.e. is below 1.16 / does not have getAttackCooldown method)
    override fun isEnabled(world: World): Boolean {
        return true
    }

    companion object {
        private lateinit var INSTANCE: AttackCooldownTracker

        @JvmStatic
        fun getLastCooldown(uuid: UUID): Float? {
            return INSTANCE.lastCooldown[uuid]
        }
    }
}
