/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities.reflection;

final class VersionComparator {
    private VersionComparator() {
    }

    static boolean isNewerOrEqualTo(
            int currentMajor,
            int currentMinor,
            int currentPatch,
            int targetMajor,
            int targetMinor,
            int targetPatch
    ) {
        if (currentMajor != targetMajor) {
            return currentMajor > targetMajor;
        }
        if (currentMinor != targetMinor) {
            return currentMinor > targetMinor;
        }
        return currentPatch >= targetPatch;
    }
}
