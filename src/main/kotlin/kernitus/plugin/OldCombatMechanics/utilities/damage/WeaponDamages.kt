/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities.damage

import kernitus.plugin.OldCombatMechanics.OCMMain
import kernitus.plugin.OldCombatMechanics.utilities.ConfigUtils
import org.bukkit.Material
import java.util.*

object WeaponDamages {
    private var damages: Map<String, Double> = emptyMap()

    private lateinit var plugin: OCMMain

    fun initialise(plugin: OCMMain) {
        WeaponDamages.plugin = plugin
        reload()
    }

    private fun reload() {
        val section = plugin.config.getConfigurationSection("old-tool-damage.damages")
        damages = ConfigUtils.loadDoubleMap(section!!) // Should error out if damages not found
    }

    fun getDamage(mat: Material): Double {
        val name = mat.name.replace("GOLDEN", "GOLD").replace("WOODEN", "WOOD").replace("SHOVEL", "SPADE")
        return damages.getOrDefault(name, -1.0)
    }

    val materialDamages: Map<Material, Double>
        get() {
            val materialMap: MutableMap<Material, Double> =
                EnumMap(org.bukkit.Material::class.java)
            damages.forEach { (name: String, damage: Double) ->
                val newName =
                    name.replace("GOLD", "GOLDEN").replace("WOOD", "WOODEN").replace("SPADE", "SHOVEL")
                materialMap[Material.valueOf(newName)] = damage
            }
            return materialMap
        }
}
