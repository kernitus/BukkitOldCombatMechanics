/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics

import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kernitus.plugin.OldCombatMechanics.api.PlayerModuleOverride
import kernitus.plugin.OldCombatMechanics.module.ModuleFishingRodVelocity
import kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerModuleOverrides
import kotlinx.coroutines.delay
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.FishHook
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerFishEvent
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.util.Vector
import kotlin.math.abs

@OptIn(ExperimentalKotest::class)
class FishingGravityIntegrationTest :
    FunSpec({
        val testPlugin = JavaPlugin.getPlugin(OCMTestMain::class.java)
        val ocm = JavaPlugin.getPlugin(OCMMain::class.java)
        val module = ModuleLoader.getModules().filterIsInstance<ModuleFishingRodVelocity>().single()
        extensions(MainThreadDispatcherExtension(testPlugin))
        test("real fishing item use respects configured gravity throughout native flight") {
            val previous = ocm.config.get("fishing-rod-velocity.gravity")
            for (gravity in listOf(0.0, 0.03, 0.04, 0.1)) {
                val fake = FakePlayer(testPlugin)
                var hook: FishHook? = null
                try {
                    ocm.config.set("fishing-rod-velocity.gravity", gravity)
                    module.reload()
                    fake.spawn(Location(Bukkit.getWorld("world"), 8.0, 180.0, 8.0))
                    val player = fake.requireBukkitPlayer()
                    delay(100)
                    val samples =
                        recordNativeFlight(testPlugin) {
                            useProjectileItem(player, Material.FISHING_ROD)
                            nativeTestProjectiles(player).filterIsInstance<FishHook>().single().also {
                                hook = it
                                it.velocity = Vector(0.0, 0.2, 0.4)
                            }
                        }
                    val displacement = samples.zipWithNext { a, b -> b.position.clone().subtract(a.position) }
                    // First native tick precedes the task. Legacy hooks move before gravity; rewritten hooks subtract 0.03 first.
                    minOf(abs(displacement[0].y - 0.17), abs(displacement[0].y - 0.2)) shouldBeLessThan 1e-6
                    val gravityFactor = if (abs(displacement[0].y - 0.2) < 1e-6) 0.92 else 1.0
                    displacement.zipWithNext().forEachIndexed { index, (before, after) ->
                        io.kotest.assertions.withClue("gravity=$gravity tick=$index samples=$samples") {
                            abs(after.y - (before.y * 0.92 - gravity * gravityFactor)) shouldBeLessThan 2e-6
                            abs(after.z - before.z * 0.92) shouldBeLessThan 2e-6
                        }
                    }
                } finally {
                    hook?.remove()
                    fake.removePlayer()
                    ocm.config.set("fishing-rod-velocity.gravity", previous)
                    module.reload()
                }
            }
        }
        test("real cancelled cast and disabled shooter do not start gravity tracking") {
            val previous = ocm.config.get("fishing-rod-velocity.gravity")
            val fake = FakePlayer(testPlugin)
            val listener = object : Listener {}
            try {
                ocm.config.set("fishing-rod-velocity.gravity", 0.1)
                module.reload()
                fake.spawn(Location(Bukkit.getWorld("world"), 8.0, 180.0, 8.0))
                val player = fake.requireBukkitPlayer()
                delay(100)
                Bukkit.getPluginManager().registerEvent(
                    PlayerFishEvent::class.java,
                    listener,
                    EventPriority.LOWEST,
                    { _, event -> if ((event as PlayerFishEvent).player == player) event.isCancelled = true },
                    testPlugin,
                )
                useProjectileItem(player, Material.FISHING_ROD)
                nativeTestProjectiles(player)
                    .filterIsInstance<FishHook>()
                    .isEmpty() shouldBe true
                (
                    module.javaClass
                        .getDeclaredField(
                            "activeHooks",
                        ).apply { isAccessible = true }
                        .get(module) as Map<*, *>
                ).size shouldBe
                    0
                HandlerList.unregisterAll(listener)
                PlayerModuleOverrides.setOverride(player, "fishing-rod-velocity", PlayerModuleOverride.FORCE_DISABLED)
                useProjectileItem(player, Material.FISHING_ROD)
                nativeTestProjectiles(player)
                    .filterIsInstance<FishHook>()
                    .size shouldBe 1
                (
                    module.javaClass
                        .getDeclaredField(
                            "activeHooks",
                        ).apply { isAccessible = true }
                        .get(module) as Map<*, *>
                ).size shouldBe
                    0
            } finally {
                HandlerList.unregisterAll(listener)
                Bukkit
                    .getWorld("world")!!
                    .entities
                    .filterIsInstance<FishHook>()
                    .forEach { it.remove() }
                fake.removePlayer()
                ocm.config.set("fishing-rod-velocity.gravity", previous)
                module.reload()
            }
        }
        test("real cast trackers stop after reload, removal, disable and external no-gravity") {
            val previous = ocm.config.get("fishing-rod-velocity.gravity")
            for (action in listOf("reload", "remove", "disable", "no-gravity")) {
                val fake = FakePlayer(testPlugin)
                var hook: FishHook? = null
                try {
                    ocm.config.set("fishing-rod-velocity.gravity", 0.1)
                    module.reload()
                    fake.spawn(Location(Bukkit.getWorld("world"), 8.0, 180.0, 8.0))
                    val player = fake.requireBukkitPlayer()
                    delay(100)
                    useProjectileItem(player, Material.FISHING_ROD)
                    hook =
                        nativeTestProjectiles(player)
                            .filterIsInstance<FishHook>()
                            .single()
                    (
                        module.javaClass
                            .getDeclaredField(
                                "activeHooks",
                            ).apply { isAccessible = true }
                            .get(module) as Map<*, *>
                    ).size shouldBe
                        1
                    when (action) {
                        "reload" -> {
                            module.reload()
                        }

                        "remove" -> {
                            hook.remove()
                        }

                        "disable" -> {
                            PlayerModuleOverrides.setOverride(
                                player,
                                "fishing-rod-velocity",
                                PlayerModuleOverride.FORCE_DISABLED,
                            )
                        }

                        else -> {
                            hook.setGravity(false)
                        }
                    }
                    delay(150)
                    (
                        module.javaClass
                            .getDeclaredField(
                                "activeHooks",
                            ).apply { isAccessible = true }
                            .get(module) as Map<*, *>
                    ).size shouldBe
                        0
                    module.javaClass
                        .getDeclaredField("gravityTask")
                        .apply { isAccessible = true }
                        .get(module) shouldBe null
                } finally {
                    hook?.remove()
                    fake.removePlayer()
                    ocm.config.set("fishing-rod-velocity.gravity", previous)
                    module.reload()
                }
            }
        }
        test("real cast in water receives no additional gravity during scheduler correction") {
            val previous = ocm.config.get("fishing-rod-velocity.gravity")
            val fake = FakePlayer(testPlugin)
            var hook: FishHook? = null
            var beforeTask: org.bukkit.scheduler.BukkitTask? = null
            var afterTask: org.bukkit.scheduler.BukkitTask? = null
            val block = Bukkit.getWorld("world")!!.getBlockAt(0, 181, 1)
            val saved = block.state
            var velocityBefore = Vector()
            var largestChange = 0.0
            var checked = 0
            try {
                block.setType(Material.WATER, false)
                ocm.config.set("fishing-rod-velocity.gravity", 0.1)
                module.reload()
                fake.spawn(Location(Bukkit.getWorld("world"), 8.0, 180.0, 8.0))
                val player = fake.requireBukkitPlayer()
                delay(100)
                beforeTask =
                    Bukkit.getScheduler().runTaskTimer(
                        testPlugin,
                        Runnable {
                            hook?.let { if (it.location.block.type == Material.WATER) velocityBefore = it.velocity }
                        },
                        1L,
                        1L,
                    )
                useProjectileItem(player, Material.FISHING_ROD)
                hook =
                    nativeTestProjectiles(player)
                        .filterIsInstance<FishHook>()
                        .single()
                hook.teleport(Location(player.world, 0.5, 181.5, 1.5))
                hook.velocity = Vector()
                afterTask =
                    Bukkit.getScheduler().runTaskTimer(
                        testPlugin,
                        Runnable {
                            hook?.let {
                                if (it.location.block.type == Material.WATER) {
                                    largestChange = maxOf(largestChange, it.velocity.subtract(velocityBefore).length())
                                    checked++
                                }
                            }
                        },
                        1L,
                        1L,
                    )
                delay(200)
                (checked > 0) shouldBe true
                largestChange shouldBeLessThan 1e-8
            } finally {
                beforeTask?.cancel()
                afterTask?.cancel()
                hook?.remove()
                fake.removePlayer()
                saved.update(true, false)
                ocm.config.set("fishing-rod-velocity.gravity", previous)
                module.reload()
            }
        }
        test("invalid fishing gravity falls back to 0.04") {
            val previous = ocm.config.get("fishing-rod-velocity.gravity")
            try {
                for (invalid in listOf(Double.NaN, Double.POSITIVE_INFINITY, -0.1, 2.0, "invalid")) {
                    ocm.config.set("fishing-rod-velocity.gravity", invalid)
                    module.reload()
                    module.javaClass
                        .getDeclaredField("gravity")
                        .apply { isAccessible = true }
                        .getDouble(module) shouldBe
                        0.04
                }
            } finally {
                ocm.config.set("fishing-rod-velocity.gravity", previous)
                module.reload()
            }
        }
    })
