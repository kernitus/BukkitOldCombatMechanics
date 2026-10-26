/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class PaperPlatformTest :
    StringSpec({
        "discovers the public marker without running its initialiser" {
            PaperPlatform.isAvailable(PaperPlatformTest::class.java.classLoader) shouldBe true
        }

        "rejects a classloader without the Paper marker" {
            PaperPlatform.isAvailable(object : ClassLoader(null) {}) shouldBe false
        }

        "rejects an unusable marker without leaking linkage errors" {
            val loader =
                object : ClassLoader(null) {
                    override fun loadClass(name: String): Class<*> = throw NoClassDefFoundError(name)
                }
            PaperPlatform.isAvailable(loader) shouldBe false
        }
    })
