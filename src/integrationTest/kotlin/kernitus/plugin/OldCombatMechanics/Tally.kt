/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics

class Tally {
    var passed: Int = 0
        private set
    var failed: Int = 0
        private set

    fun passed() {
        passed++
    }

    fun failed() {
        failed++
    }

    val total: Int
        get() = passed + failed
}
