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

import kernitus.plugin.OldCombatMechanics.utilities.Messenger.send
import org.bukkit.command.CommandSender
import org.bukkit.entity.LivingEntity
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

object TesterUtils {
    /**
     * Checks whether the two values are equal, prints the result and updates the tally
     *
     * @param a        The expected value
     * @param b        The actual value
     * @param tally    The tally to update the result of the test with
     * @param testName The name of the test being run
     * @param senders  The command senders to message with the result of the test
     */
    fun assertEquals(a: Float, b: Float, tally: Tally, testName: String, vararg senders: CommandSender) {
        // Due to cooldown effects, numbers can be very close (e.g. 1.0000000149011612 == 1.0)
        // These are equivalent when using floats, which is what the server is using anyway
        if (a == b) {
            tally.passed()
            for (sender in senders) send(
                sender,
                "&aPASSED &f$testName [E: $a / A: $b]"
            )
        } else {
            tally.failed()
            for (sender in senders) send(
                sender,
                "&cFAILED &f$testName [E: $a / A: $b]"
            )
        }
    }

    /**
     * Cross-version accessor for a specific potion effect. Pre-1.12 servers lack
     * LivingEntity#getPotionEffect, so we fall back to scanning active effects.
     */
    fun LivingEntity.getPotionEffectCompat(type: PotionEffectType): PotionEffect? {
        // Prefer reflection to avoid linkage errors on legacy servers.
        val method = javaClass.methods.firstOrNull { m ->
            m.name == "getPotionEffect" &&
                m.parameterTypes.size == 1 &&
                m.parameterTypes[0] == PotionEffectType::class.java
        }
        return runCatching { method?.invoke(this, type) as PotionEffect? }.getOrNull()
            ?: activePotionEffects.firstOrNull { it.type == type }
    }
}
