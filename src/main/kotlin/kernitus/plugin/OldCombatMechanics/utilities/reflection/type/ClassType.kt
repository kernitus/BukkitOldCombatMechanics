/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities.reflection.type

import kernitus.plugin.OldCombatMechanics.utilities.reflection.VersionCompatUtils

enum class ClassType {
    NMS {
        override fun qualifyClassName(partialName: String): String {
            if (VersionCompatUtils.versionIsNewerOrEqualTo(1, 17, 0)) {
                return "net.minecraft.$partialName"
            }
            // FIXME: Assumes class names are upper case and trims off the preceding package name
            // entity.foo.Bar.Test
            // ^^^^^^^^^^ ^^^^^^^^^
            //   Group 1   Group 2
            val className = partialName.replace("([a-z.]+)\\.([a-zA-Z.]+)".toRegex(), "$2")

            return "net.minecraft.server." + VersionCompatUtils.version + "." + className
        }
    },
    CRAFTBUKKIT {
        override fun qualifyClassName(partialName: String): String {
            return String.format("%s.%s.%s", "org.bukkit.craftbukkit", VersionCompatUtils.version, partialName)
        }
    };

    abstract fun qualifyClassName(partialName: String): String
}
