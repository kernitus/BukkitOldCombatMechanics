/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module

import kernitus.plugin.OldCombatMechanics.OCMMain
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector.versionIsNewerOrEqualTo
import kernitus.plugin.OldCombatMechanics.utilities.reflection.SpigotFunctionChooser
import org.bukkit.Material
import org.bukkit.entity.FishHook
import org.bukkit.event.EventHandler
import org.bukkit.event.player.PlayerFishEvent
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Vector
import java.util.*
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * This module reverts fishing rod gravity and velocity back to 1.8 behaviour
 *
 *
 * Fishing rod gravity in 1.14+ is 0.03 while in 1.8 it is 0.04
 * Launch velocity in 1.9+ is also different from the 1.8 formula
 */
class ModuleFishingRodVelocity(plugin: OCMMain) : OCMModule(plugin, "fishing-rod-velocity") {
    private var random: Random? = null
    private var hasDifferentGravity = false

    // In 1.12- getHook() returns a Fish which extends FishHook

    private val getHook: SpigotFunctionChooser<PlayerFishEvent, Any?, FishHook?> =
        SpigotFunctionChooser.apiCompatReflectionCall(
            { e, _ -> e.hook },
            PlayerFishEvent::class.java, "getHook"
        )

    init {
        reload()
    }

    override fun reload() {
        random = Random()

        // Versions 1.14+ have different gravity than previous versions
        hasDifferentGravity = versionIsNewerOrEqualTo(1, 14, 0)
    }

    @EventHandler(ignoreCancelled = true)
    fun onFishEvent(event: PlayerFishEvent) {
        val fishHook = getHook.apply(event)
        val player = event.player

        if (!isEnabled(player) || event.state != PlayerFishEvent.State.FISHING) return

        val location = event.player.location
        val playerYaw = location.yaw.toDouble()
        val playerPitch = location.pitch.toDouble()

        val oldMaxVelocity = 0.4f
        var velocityX =
            -sin(playerYaw / 180.0f * Math.PI.toFloat()) * cos(playerPitch / 180.0f * Math.PI.toFloat()) * oldMaxVelocity
        var velocityZ =
            cos(playerYaw / 180.0f * Math.PI.toFloat()) * cos(playerPitch / 180.0f * Math.PI.toFloat()) * oldMaxVelocity
        var velocityY = -sin(playerPitch / 180.0f * Math.PI.toFloat()) * oldMaxVelocity

        val oldVelocityMultiplier = 1.5

        val vectorLength =
            sqrt(velocityX * velocityX + velocityY * velocityY + velocityZ * velocityZ).toFloat().toDouble()
        velocityX /= vectorLength
        velocityY /= vectorLength
        velocityZ /= vectorLength

        velocityX += random!!.nextGaussian() * 0.007499999832361937
        velocityY += random!!.nextGaussian() * 0.007499999832361937
        velocityZ += random!!.nextGaussian() * 0.007499999832361937

        velocityX *= oldVelocityMultiplier
        velocityY *= oldVelocityMultiplier
        velocityZ *= oldVelocityMultiplier

        fishHook!!.velocity = Vector(velocityX, velocityY, velocityZ)

        if (!hasDifferentGravity) return

        // Adjust gravity on every tick unless it's in water
        object : BukkitRunnable() {
            override fun run() {
                if (!fishHook.isValid || fishHook.isOnGround) cancel()

                // We check both conditions as sometimes it's underwater but in seagrass, or when bobbing not underwater but the material is water
                if (!fishHook.isInWater && fishHook.world.getBlockAt(fishHook.location).type != Material.WATER) {
                    val fVelocity = fishHook.velocity
                    fVelocity.setY(fVelocity.y - 0.01)
                    fishHook.velocity = fVelocity
                }
            }
        }.runTaskTimer(plugin, 1, 1)
    }
}
