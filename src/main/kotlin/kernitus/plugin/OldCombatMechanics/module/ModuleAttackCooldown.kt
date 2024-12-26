/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module

import kernitus.plugin.OldCombatMechanics.OCMMain
import kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage.getPlayerData
import org.bukkit.Bukkit
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

/**
 * Disables the attack cooldown.
 */
class ModuleAttackCooldown(plugin: OCMMain) : OCMModule(plugin, "disable-attack-cooldown") {
    private val NEW_ATTACK_SPEED = 4.0

    override fun reload() {
        Bukkit.getOnlinePlayers().forEach { player: Player -> this.adjustAttackSpeed(player) }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerLogin(e: PlayerJoinEvent) {
        adjustAttackSpeed(e.player)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onWorldChange(e: PlayerChangedWorldEvent) {
        adjustAttackSpeed(e.player)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerQuit(e: PlayerQuitEvent) {
        setAttackSpeed(e.player, NEW_ATTACK_SPEED)
    }

    /**
     * Adjusts the attack speed to the default or configured value, depending on whether the module is enabled.
     *
     * @param player the player to set the attack speed for
     */
    private fun adjustAttackSpeed(player: Player) {
        val attackSpeed = if (isEnabled(player))
            module()!!.getDouble("generic-attack-speed")
        else
            NEW_ATTACK_SPEED

        setAttackSpeed(player, attackSpeed)
    }

    override fun onModesetChange(player: Player) {
        adjustAttackSpeed(player)
    }

    /**
     * Sets the attack speed to the given value.
     *
     * @param player      the player to set it for
     * @param attackSpeed the attack speed to set it to
     */
    fun setAttackSpeed(player: Player, attackSpeed: Double) {
        val attribute = player.getAttribute(Attribute.GENERIC_ATTACK_SPEED) ?: return

        val baseValue = attribute.baseValue

        val modesetName = getPlayerData(player.uniqueId).getModesetForWorld(player.world.uid)
        debug(
            String.format(
                "Setting attack speed to %.2f (was: %.2f) for %s in mode %s",
                attackSpeed,
                baseValue,
                player.name,
                modesetName
            )
        )

        if (baseValue != attackSpeed) {
            debug(String.format("Setting attack speed to %.2f (was: %.2f)", attackSpeed, baseValue), player)

            attribute.baseValue = attackSpeed
            player.saveData()
        }
    }
}
