/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities.damage

import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.configuration.file.YamlConfiguration
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles

/** Reads Purpur's optional critical multiplier without linking against its API. */
object ServerCriticalMultiplier {
    private const val VANILLA_MULTIPLIER = 1.5
    private const val OPTION = "gameplay-mechanics.player.critical-damage-multiplier"
    private val configGetter: MethodHandle? =
        try {
            val spigot = Bukkit.spigot()
            Reflector.getMethod(spigot.javaClass, "getPurpurConfig", 0)?.let {
                MethodHandles.lookup().unreflect(it).bindTo(spigot)
            }
        } catch (_: IllegalAccessException) {
            null
        } catch (_: SecurityException) {
            null
        } catch (_: LinkageError) {
            null
        }

    @JvmStatic
    fun get(world: World): Double {
        val getter = configGetter ?: return VANILLA_MULTIPLIER
        // Purpur replaces its configuration object on reload. Keep the accessor,
        // obtaining the current object without reflective discovery on each hit.
        val config =
            try {
                getter.invoke() as? YamlConfiguration
            } catch (_: RuntimeException) {
                null
            } catch (_: LinkageError) {
                null
            } ?: return VANILLA_MULTIPLIER
        val default = config.getDouble("world-settings.default.$OPTION", VANILLA_MULTIPLIER)
        // Match the float conversion in Purpur's native attack calculation.
        return config.getDouble("world-settings.${world.name}.$OPTION", default).toFloat().toDouble()
    }
}
