/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities.reflection

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class VersionComparatorTest : StringSpec({
    "treats same version as newer or equal" {
        assertCurrentVersionIsNewerOrEqualTo(1, 21, 11)
    }

    "treats patch increment as newer than current version" {
        VersionComparator.isNewerOrEqualTo(1, 21, 12, 1, 21, 11) shouldBe true
    }

    "treats minor increment as newer than current version" {
        VersionComparator.isNewerOrEqualTo(1, 22, 0, 1, 21, 11) shouldBe true
    }

    "treats newest line style version as newer than current version" {
        VersionComparator.isNewerOrEqualTo(26, 1, 2, 1, 21, 11) shouldBe true
    }

    "treats older version as older than current version" {
        VersionComparator.isNewerOrEqualTo(1, 20, 6, 1, 21, 11) shouldBe false
    }
})

private fun assertCurrentVersionIsNewerOrEqualTo(
    targetMajor: Int,
    targetMinor: Int,
    targetPatch: Int,
) {
    VersionComparator.isNewerOrEqualTo(1, 21, 11, targetMajor, targetMinor, targetPatch) shouldBe true
}
