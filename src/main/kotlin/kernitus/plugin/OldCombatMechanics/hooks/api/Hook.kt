/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.hooks.api

import kernitus.plugin.OldCombatMechanics.OCMMain

interface Hook {
    fun init(plugin: OCMMain)

    fun deinit(plugin: OCMMain)
}
