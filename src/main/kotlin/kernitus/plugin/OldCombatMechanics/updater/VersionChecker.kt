/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.updater

import kernitus.plugin.OldCombatMechanics.OCMMain
import java.util.regex.Pattern

object VersionChecker {
    fun shouldUpdate(remoteVersion: String): Boolean {
        return isUpdateOut(remoteVersion, OCMMain.version)
    }

    private fun isUpdateOut(remoteVersion: String, localVersion: String): Boolean {
        val testVer = getVersionNumbers(remoteVersion)
        val baseVer = getVersionNumbers(localVersion)

        for (i in testVer.indices) {
            if (testVer[i] != baseVer[i]) return testVer[i] > baseVer[i]
        }

        return false
    }

    private fun getVersionNumbers(ver: String): IntArray {
        val m = Pattern.compile("(\\d+)\\.(\\d+)\\.*(\\d*)(-beta(\\d*))?", Pattern.CASE_INSENSITIVE).matcher(ver)
        require(m.matches()) { "Plugin version formatted wrong!" }

        // Group 1 = MAJOR
        // Group 2 = MINOR
        // Group 3 = PATCH
        // Group 4 = beta
        // Group 5 = beta_version

        //This parses it to MAJOR.MINOR.PATCH-beta_version
        //MAJOR & MINOR required, anything else is set to maximum value if omitted - necessary otherwise
        // somebody with a beta version will not see update to release version
        return intArrayOf(
            m.group(1).toInt(),
            m.group(2).toInt(),
            if (m.group(3).isEmpty()) Int.MAX_VALUE else m.group(3).toInt(),
            if (m.group(4) == null) Int.MAX_VALUE else (if (m.group(5).isEmpty()) 1 else m.group(5).toInt())
        )
    }
}
