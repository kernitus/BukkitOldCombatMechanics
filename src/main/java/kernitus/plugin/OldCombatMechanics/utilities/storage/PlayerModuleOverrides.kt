/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities.storage

import kernitus.plugin.OldCombatMechanics.ModuleLoader
import kernitus.plugin.OldCombatMechanics.api.PlayerModuleOverride
import org.bukkit.OfflinePlayer
import org.bukkit.entity.HumanEntity
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory per-player module override store.
 */
object PlayerModuleOverrides {

    private val overridesByPlayer = ConcurrentHashMap<UUID, ConcurrentHashMap<String, PlayerModuleOverride>>()

    @JvmStatic
    fun setOverride(human: HumanEntity, moduleName: String, state: PlayerModuleOverride) {
        requireOnline(human)
        val normalised = ModuleLoader.normaliseModuleName(moduleName)
        if (state == PlayerModuleOverride.DEFAULT) {
            clearOverride(human, normalised)
            return
        }
        overridesByPlayer.computeIfAbsent(human.uniqueId) { ConcurrentHashMap() }[normalised] = state
    }

    @JvmStatic
    fun getOverride(human: HumanEntity, moduleName: String): PlayerModuleOverride {
        requireOnline(human)
        val perPlayer = overridesByPlayer[human.uniqueId] ?: return PlayerModuleOverride.DEFAULT
        return perPlayer.getOrDefault(ModuleLoader.normaliseModuleName(moduleName), PlayerModuleOverride.DEFAULT)
    }

    @JvmStatic
    fun clearOverride(human: HumanEntity, moduleName: String): Boolean {
        requireOnline(human)
        val perPlayer = overridesByPlayer[human.uniqueId] ?: return false
        val removed = perPlayer.remove(ModuleLoader.normaliseModuleName(moduleName))
        if (perPlayer.isEmpty()) overridesByPlayer.remove(human.uniqueId)
        return removed != null
    }

    @JvmStatic
    fun clearAll(human: HumanEntity): Boolean {
        requireOnline(human)
        return overridesByPlayer.remove(human.uniqueId) != null
    }

    @JvmStatic
    fun clearAll() {
        overridesByPlayer.clear()
    }

    @JvmStatic
    private fun requireOnline(human: HumanEntity) {
        val isOnline = when (human) {
            is OfflinePlayer -> human.isOnline
            else -> true // HumanEntity (e.g. a living NPC) — assume present in world
        }
        require(isOnline) { "HumanEntity ${human.name} is not online" }
    }

}