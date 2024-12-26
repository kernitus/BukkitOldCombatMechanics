/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics

import kernitus.plugin.OldCombatMechanics.module.OCMModule
import kernitus.plugin.OldCombatMechanics.utilities.EventRegistry
import kernitus.plugin.OldCombatMechanics.utilities.Messenger

object ModuleLoader {
    private lateinit var eventRegistry: EventRegistry
    val modules: MutableList<OCMModule> = ArrayList()

    fun addModule(module: OCMModule) = modules.add(module)

    fun initialise(plugin: OCMMain) {
        eventRegistry = EventRegistry(plugin)
    }

    fun toggleModules() = modules.forEach { setState(it, it.isEnabled()) }

    private fun setState(module: OCMModule, state: Boolean) {
        if (state) {
            if (eventRegistry.registerListener(module)) {
                Messenger.debug("Enabled " + module.moduleName)
            }
        } else {
            if (eventRegistry.unregisterListener(module)) {
                Messenger.debug("Disabled " + module.moduleName)
            }
        }
    }

}
