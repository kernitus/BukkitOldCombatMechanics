/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics

import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.doubles.shouldBeLessThanOrEqual
import kernitus.plugin.OldCombatMechanics.module.ModuleChorusFruit
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.Callable

@OptIn(ExperimentalKotest::class)
class ChorusFruitIntegrationTest : FunSpec({
    val testPlugin = JavaPlugin.getPlugin(OCMTestMain::class.java)
    val ocm = JavaPlugin.getPlugin(OCMMain::class.java)
    val chorusModule = ModuleLoader.getModules()
        .filterIsInstance<ModuleChorusFruit>()
        .firstOrNull() ?: error("ModuleChorusFruit not registered")

    extensions(MainThreadDispatcherExtension(testPlugin))

    fun runSync(action: () -> Unit) {
        if (Bukkit.isPrimaryThread()) {
            action()
        } else {
            Bukkit.getScheduler().callSyncMethod(testPlugin, Callable {
                action()
                null
            }).get()
        }
    }

    fun preparePlatform() {
        runSync {
            val world = checkNotNull(Bukkit.getWorld("world"))
            // Solid floor at y=100 and clear air above it in a 25x25 area around the origin
            for (x in -12..12) {
                for (z in -12..12) {
                    world.getBlockAt(x, 100, z).type = Material.STONE
                    for (y in 101..105) {
                        world.getBlockAt(x, y, z).type = Material.AIR
                    }
                }
            }
        }
    }

    fun clearPlatform() {
        runSync {
            val world = checkNotNull(Bukkit.getWorld("world"))
            for (x in -12..12) {
                for (z in -12..12) {
                    for (y in 99..105) {
                        world.getBlockAt(x, y, z).type = Material.AIR
                    }
                }
            }
        }
    }

    suspend fun TestScope.withChorusConfig(distance: Double, block: suspend TestScope.() -> Unit) {
        val enabled = ocm.config.getBoolean("chorus-fruit.enabled")
        val maxDistance = ocm.config.getDouble("chorus-fruit.max-teleportation-distance")
        val preventEating = ocm.config.getBoolean("chorus-fruit.prevent-eating")
        val hungerValue = ocm.config.getInt("chorus-fruit.hunger-value")
        val saturationValue = ocm.config.getDouble("chorus-fruit.saturation-value")

        try {
            ocm.config.set("chorus-fruit.enabled", true)
            ocm.config.set("chorus-fruit.max-teleportation-distance", distance)
            ocm.config.set("chorus-fruit.prevent-eating", false)
            ocm.config.set("chorus-fruit.hunger-value", hungerValue)
            ocm.config.set("chorus-fruit.saturation-value", saturationValue)
            chorusModule.reload()
            ModuleLoader.toggleModules()
            block()
        } finally {
            clearPlatform()
            ocm.config.set("chorus-fruit.enabled", enabled)
            ocm.config.set("chorus-fruit.max-teleportation-distance", maxDistance)
            ocm.config.set("chorus-fruit.prevent-eating", preventEating)
            ocm.config.set("chorus-fruit.hunger-value", hungerValue)
            ocm.config.set("chorus-fruit.saturation-value", saturationValue)
            chorusModule.reload()
            ModuleLoader.toggleModules()
        }
    }

    fun Location.isSafe(): Boolean {
        val feet = block
        val head = feet.getRelative(BlockFace.UP)
        val below = feet.getRelative(BlockFace.DOWN)
        val legacy = !kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector.versionIsNewerOrEqualTo(1, 13, 0)
        val feetPassable = if (legacy) !feet.type.isSolid else feet.isPassable
        val headPassable = if (legacy) !head.type.isSolid else head.isPassable
        return feetPassable && headPassable && below.type.isSolid
    }

    suspend fun withFakePlayer(origin: Location, block: suspend (Player) -> Unit) {
        lateinit var fake: FakePlayer
        lateinit var player: Player

        runSync {
            fake = FakePlayer(testPlugin)
            fake.spawn(origin)
            player = checkNotNull(Bukkit.getPlayer(fake.uuid))
            player.gameMode = GameMode.SURVIVAL
            player.isInvulnerable = false
            player.activePotionEffects.forEach { player.removePotionEffect(it.type) }
            val data = kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage.getPlayerData(player.uniqueId)
            data.setModesetForWorld(player.world.uid, "old")
            kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage.setPlayerData(player.uniqueId, data)
        }

        try {
            block(player)
        } finally {
            runSync {
                fake.removePlayer()
            }
        }
    }

    test("chorus fruit custom distance teleports to a safe spot") {
        preparePlatform()
        withChorusConfig(distance = 1.0) {
            val origin = Location(checkNotNull(Bukkit.getWorld("world")), 0.5, 101.0, 0.5)
            withFakePlayer(origin) { player ->
                var result: Location? = null
                runSync {
                    val event = PlayerTeleportEvent(
                        player,
                        player.location,
                        player.location.clone(),
                        PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT
                    )
                    Bukkit.getPluginManager().callEvent(event)
                    result = event.to
                }

                val target = result ?: error("Teleport target was null")
                // Horizontal displacement should respect the configured radius
                kotlin.math.abs(target.x - origin.x) shouldBeLessThanOrEqual 1.0
                kotlin.math.abs(target.z - origin.z) shouldBeLessThanOrEqual 1.0
                // Y stays within the search band (clamped to world height)
                kotlin.math.abs(target.y - origin.y) shouldBeLessThanOrEqual 1.0
                target.isSafe().shouldBeTrue()
            }
        }
    }
})
