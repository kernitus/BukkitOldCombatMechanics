/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.hooks

import kernitus.plugin.OldCombatMechanics.OCMMain
import kernitus.plugin.OldCombatMechanics.hooks.api.Hook
import kernitus.plugin.OldCombatMechanics.module.ModuleDisableEnderpearlCooldown
import kernitus.plugin.OldCombatMechanics.module.ModuleGoldenApple
import kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage
import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.entity.Player

class PlaceholderAPIHook : Hook {
    private var expansion: PlaceholderExpansion? = null

    override fun init(plugin: OCMMain) {
        expansion = object : PlaceholderExpansion() {
            override fun canRegister() = true

            override fun persist() = true

            override fun getIdentifier() = "ocm"

            override fun getAuthor() = plugin.description.authors.joinToString(", ")

            override fun getVersion() = plugin.description.version

            override fun onPlaceholderRequest(player: Player, identifier: String): String? {
                return when (identifier) {
                    "modeset" -> getModeset(player)
                    "gapple_cooldown" -> getGappleCooldown(player)
                    "napple_cooldown" -> getNappleCooldown(player)
                    "enderpearl_cooldown" -> getEnderpearlCooldown(player)
                    else -> null
                }
            }

            fun getGappleCooldown(player: Player): String {
                val seconds = ModuleGoldenApple.instance.getGappleCooldown(player.uniqueId)
                return if (seconds > 0) seconds.toString() else "None"
            }

            fun getNappleCooldown(player: Player): String {
                val seconds = ModuleGoldenApple.instance.getNappleCooldown(player.uniqueId)
                return if (seconds > 0) seconds.toString() else "None"
            }

            fun getEnderpearlCooldown(player: Player): String {
                val seconds = ModuleDisableEnderpearlCooldown.instance.getEnderpearlCooldown(player.uniqueId)
                return if (seconds > 0) seconds.toString() else "None"
            }

            fun getModeset(player: Player): String {
                val playerData = PlayerStorage.getPlayerData(player.uniqueId)
                var modeName = playerData.getModesetForWorld(player.world.uid)
                if (modeName.isNullOrEmpty()) modeName = "unknown"
                return modeName
            }
        }

        (expansion as PlaceholderExpansion).register()
    }

    override fun deinit(plugin: OCMMain) {
        expansion?.unregister()
    }
}
