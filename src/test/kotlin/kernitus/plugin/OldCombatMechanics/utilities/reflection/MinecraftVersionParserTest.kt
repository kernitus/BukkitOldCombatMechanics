/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities.reflection

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class MinecraftVersionParserTest :
    StringSpec({
        "parses direct Minecraft version API output" {
            assertParsedVersion("1.21.11", 1, 21, 11)
        }

        "defaults missing patch versions to zero" {
            assertParsedVersion("1.21", 1, 21, 0)
        }

        "parses Bukkit snapshot version output" {
            assertParsedVersion("1.20.6-R0.1-SNAPSHOT", 1, 20, 6)
        }

        "prefers the Minecraft marker from server version output" {
            assertParsedVersion("git-Paper-422 (MC: 1.21.11)", 1, 21, 11)
        }

        "parses Minecraft marker output with trailing build text" {
            assertParsedVersion("git-Spigot-123 (MC: 1.20.4 build 53)", 1, 20, 4)
        }

        "finds an embedded numeric Minecraft version after non-numeric tokens" {
            assertParsedVersion("Paper build 123 for Minecraft 1.19.4", 1, 19, 4)
        }

        "skips oversized dotted tokens before a valid Minecraft version" {
            assertParsedVersion("build 999999999999.1 for Minecraft 1.18.2", 1, 18, 2)
        }

        "ignores dotted build tokens without a sane Minecraft version" {
            MinecraftVersionParser.parse("build.R0.1-SNAPSHOT").shouldBeNull()
        }
    })

private fun assertParsedVersion(
    rawVersion: String,
    major: Int,
    minor: Int,
    patch: Int,
) {
    val parsed = MinecraftVersionParser.parse(rawVersion)
    parsed.major shouldBe major
    parsed.minor shouldBe minor
    parsed.patch shouldBe patch
    parsed.toString() shouldBe "$major.$minor.$patch"
}
