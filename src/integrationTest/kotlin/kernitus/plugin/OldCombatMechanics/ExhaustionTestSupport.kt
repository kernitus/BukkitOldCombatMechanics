/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.Event

/** Model the native exhaustion step after a constructed heal, including its optional Bukkit event. */
fun applyTestExhaustion(
    player: Player,
    amount: Double,
    reason: String = "REGEN",
) {
    val eventClass =
        try {
            Class.forName("org.bukkit.event.entity.EntityExhaustionEvent")
        } catch (_: ClassNotFoundException) {
            null
        }
    var applied = amount
    if (eventClass != null) {
        val constructor = eventClass.constructors.first { it.parameterCount == 3 }
        val arguments =
            constructor.parameterTypes.map { type ->
                when {
                    type.isInstance(player) -> player
                    type == java.lang.Double.TYPE -> amount
                    type == java.lang.Float.TYPE -> amount.toFloat()
                    type.isEnum -> type.enumConstants.first { (it as Enum<*>).name == reason }
                    else -> error("Unexpected exhaustion constructor parameter: $type")
                }
            }
        val event = constructor.newInstance(*arguments.toTypedArray()) as Event
        Bukkit.getPluginManager().callEvent(event)
        if ((event as Cancellable).isCancelled) return
        applied = (eventClass.getMethod("getExhaustion").invoke(event) as Number).toDouble()
    }
    player.exhaustion = minOf(40.0f, player.exhaustion + applied.toFloat())
}
