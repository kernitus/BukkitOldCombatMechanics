/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module

import kernitus.plugin.OldCombatMechanics.OCMMain
import kernitus.plugin.OldCombatMechanics.utilities.ConfigUtils.loadMaterialList
import kernitus.plugin.OldCombatMechanics.utilities.Messenger.send
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.inventory.PrepareItemCraftEvent

/**
 * Makes the specified materials uncraftable.
 */
class ModuleDisableCrafting(plugin: OCMMain) : OCMModule(plugin, "disable-crafting") {
    private lateinit var denied: List<Material>
    private var message: String? = null

    init {
        reload()
    }

    override fun reload() {
        denied = loadMaterialList(module(), "denied")
        message = if (module().getBoolean("showMessage")) module().getString("message") else null
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPrepareItemCraft(e: PrepareItemCraftEvent) {
        val viewers = e.viewers
        if (viewers.size == 0) return

        if (!isEnabled(viewers[0])) return

        val inv = e.inventory
        val result = inv.result

        if (result != null && denied.contains(result.type)) {
            inv.result = null
            if (message != null) viewers.forEach {
                send(it, message!!)
            }
        }
    }
}