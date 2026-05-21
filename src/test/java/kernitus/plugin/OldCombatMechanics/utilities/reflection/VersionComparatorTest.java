/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities.reflection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionComparatorTest {
    @Test
    void treatsSameVersionAsNewerOrEqual() {
        assertCurrentVersionIsNewerOrEqualTo(1, 21, 11);
    }

    @Test
    void treatsPatchIncrementAsNewerThanCurrentVersion() {
        assertTrue(VersionComparator.isNewerOrEqualTo(1, 21, 12, 1, 21, 11));
    }

    @Test
    void treatsMinorIncrementAsNewerThanCurrentVersion() {
        assertTrue(VersionComparator.isNewerOrEqualTo(1, 22, 0, 1, 21, 11));
    }

    @Test
    void treatsNewestLineStyleVersionAsNewerThanCurrentVersion() {
        assertTrue(VersionComparator.isNewerOrEqualTo(26, 1, 2, 1, 21, 11));
    }

    @Test
    void treatsOlderVersionAsOlderThanCurrentVersion() {
        assertFalse(VersionComparator.isNewerOrEqualTo(1, 20, 6, 1, 21, 11));
    }

    private void assertCurrentVersionIsNewerOrEqualTo(int targetMajor, int targetMinor, int targetPatch) {
        assertTrue(VersionComparator.isNewerOrEqualTo(1, 21, 11, targetMajor, targetMinor, targetPatch));
    }
}
