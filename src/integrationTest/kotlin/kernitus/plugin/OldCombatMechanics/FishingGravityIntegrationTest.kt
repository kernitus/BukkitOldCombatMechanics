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
import java.io.File
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
            val apiSamples = mutableListOf<String>()
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
                                if (FishHook::class.java.methods.any { method -> method.name == "getDefaultGravity" }) {
                                    val baseline = it.defaultGravity
                                    val enabled = it.gravity
                                    it.setGravity(false)
                                    val disabled = it.gravity
                                    val disabledBaseline = it.defaultGravity
                                    it.setGravity(true)
                                    apiSamples.add(
                                        "configured=$gravity default=$baseline enabled=$enabled disabled=$disabled disabledDefault=$disabledBaseline",
                                    )
                                    abs(baseline - 0.03) shouldBeLessThan 1e-8
                                    enabled shouldBe baseline
                                    disabled shouldBe 0.0
                                    disabledBaseline shouldBe baseline
                                }
                            }
                        }
                    if (FishHook::class.java.methods.any { it.name == "getState" } &&
                        FishHook::class.java.methods.any { it.name == "getHookedEntity" }
                    ) {
                        module.javaClass
                            .getDeclaredField(
                                "getHandle",
                            ).apply { isAccessible = true }
                            .get(module) shouldBe
                            null
                        module.javaClass
                            .getDeclaredField(
                                "legacyTargetResolved",
                            ).apply { isAccessible = true }
                            .getBoolean(module) shouldBe
                            false
                    }
                    val displacement = samples.zipWithNext { a, b -> b.position.clone().subtract(a.position) }
                    // First native tick precedes the task. Legacy hooks move before gravity; rewritten hooks subtract 0.03 first.
                    minOf(abs(displacement[0].y - 0.17), abs(displacement[0].y - 0.2)) shouldBeLessThan 1e-6
                    val gravityFactor = if (abs(displacement[0].y - 0.2) < 1e-6) 0.92 else 1.0
                    val rewritten =
                        module.javaClass
                            .getDeclaredMethod("isRewrittenHook", FishHook::class.java)
                            .apply { isAccessible = true }
                            .invoke(module, hook) as Boolean
                    val baseline =
                        module.javaClass
                            .getDeclaredMethod("nativeGravity", FishHook::class.java)
                            .apply { isAccessible = true }
                            .invoke(module, hook) as Double
                    rewritten shouldBe (gravityFactor == 1.0)
                    abs(baseline - if (rewritten) 0.03 else 0.04) shouldBeLessThan 1e-8
                    apiSamples.add(
                        "configured=$gravity selectedBaseline=$baseline rewritten=$rewritten firstMovement=${displacement[0].y}",
                    )
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
            File(testPlugin.dataFolder, "fishing-gravity-api-diagnostics.txt")
                .writeText(apiSamples.joinToString("\n", postfix = "\n"))
        }
        test("public gravity reads each hook without discovering a native fallback").config(
            enabledOrReasonIf = {
                io.kotest.core.test.Enabled(
                    FishHook::class.java.methods.any { it.name == "getDefaultGravity" },
                    "Default gravity getter is absent from this Paper API",
                )
            },
        ) {
            val getter =
                module.javaClass.getDeclaredMethod("nativeGravity", FishHook::class.java).apply {
                    isAccessible =
                        true
                }
            val target =
                module.javaClass.getDeclaredMethod("isHooked", FishHook::class.java).apply {
                    isAccessible =
                        true
                }
            var liveGravity = 0.02
            var hasTarget = false
            val hook =
                java.lang.reflect.Proxy.newProxyInstance(
                    FishHook::class.java.classLoader,
                    arrayOf(FishHook::class.java),
                ) { proxy, method, _ ->
                    when (method.name) {
                        "getDefaultGravity" -> liveGravity
                        "getHookedEntity" -> if (hasTarget) proxy else null
                        else -> error("Unexpected call ${method.name}; this hook has no native handle")
                    }
                } as FishHook
            for (value in listOf(0.02, 0.06)) {
                liveGravity = value
                getter.invoke(module, hook) shouldBe value
                target.invoke(module, hook) shouldBe false
            }
            hasTarget = true
            target.invoke(module, hook) shouldBe true
            module.javaClass
                .getDeclaredField("getHandle")
                .apply { isAccessible = true }
                .get(module) shouldBe null
            module.javaClass
                .getDeclaredField(
                    "legacyTargetResolved",
                ).apply { isAccessible = true }
                .getBoolean(module) shouldBe
                false
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
        for (noGravityOnly in listOf(false, true)) {
            test("fishing lifecycle external-no-gravity=$noGravityOnly").config(
                enabledOrReasonIf = {
                    io.kotest.core.test.Enabled(
                        !noGravityOnly || org.bukkit.entity.Entity::class.java.methods.any { it.name == "setGravity" },
                        "External gravity control is absent from this Bukkit API",
                    )
                },
            ) {
                val previous = ocm.config.get("fishing-rod-velocity.gravity")
                for (action in if (noGravityOnly) listOf("no-gravity") else listOf("reload", "remove", "disable")) {
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
        }
        test("real cast in water receives no additional gravity during scheduler correction") {
            val previous = ocm.config.get("fishing-rod-velocity.gravity")
            val fake = FakePlayer(testPlugin)
            var hook: FishHook? = null
            var restoreTask: (() -> Unit)? = null
            val samples = mutableListOf<String>()
            var changedAge = false
            val block = Bukkit.getWorld("world")!!.getBlockAt(0, 181, 1)
            val saved = block.state
            var largestChange = 0.0
            var checked = 0
            try {
                block.setType(Material.WATER, false)
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
                hook.teleport(Location(player.world, 0.5, 181.5, 1.5))
                hook.velocity = Vector()
                val gravityTask =
                    module.javaClass
                        .getDeclaredField("gravityTask")
                        .apply { isAccessible = true }
                        .get(module) as org.bukkit.scheduler.BukkitRunnable
                val scheduled = Bukkit.getScheduler().pendingTasks.single { it.taskId == gravityTask.taskId }
                // Capture the scheduled runnable once. Equal-tick ordering differs across CraftScheduler versions.
                val runnableField =
                    generateSequence<Class<*>>(scheduled.javaClass) { it.superclass }
                        .flatMap { it.declaredFields.asSequence() }
                        .single { it.type == Runnable::class.java }
                        .apply { isAccessible = true }
                val original = runnableField.get(scheduled) as Runnable
                val server =
                    Bukkit
                        .getServer()
                        .javaClass
                        .getMethod("getServer")
                        .invoke(Bukkit.getServer())
                val clock = server.javaClass.getField("currentTick").apply { isAccessible = true }
                val activeHooks =
                    module.javaClass
                        .getDeclaredField("activeHooks")
                        .apply { isAccessible = true }
                        .get(module) as Map<*, *>
                val observedHook = hook
                val wrapper =
                    Runnable {
                        val age = observedHook.ticksLived
                        val previousAge = activeHooks[observedHook]
                        val inWater = observedHook.location.block.type.name in listOf("WATER", "STATIONARY_WATER")
                        val velocityBefore = observedHook.velocity
                        val tick = clock.getInt(null)
                        original.run()
                        if (inWater && previousAge != null && previousAge != age && activeHooks[observedHook] == age) {
                            val change = observedHook.velocity.subtract(velocityBefore).length()
                            largestChange = maxOf(largestChange, change)
                            changedAge = changedAge || observedHook.ticksLived != age || clock.getInt(null) != tick
                            checked++
                            samples.add(
                                "tick=$tick age=$age previousAge=$previousAge phase=scheduled-runnable " +
                                    "before=$velocityBefore after=${observedHook.velocity} change=$change",
                            )
                        }
                    }
                runnableField.set(scheduled, wrapper)
                restoreTask = { if (runnableField.get(scheduled) === wrapper) runnableField.set(scheduled, original) }
                delay(200)
                File(testPlugin.dataFolder, "fishing-water-phase-diagnostics.txt")
                    .writeText(samples.joinToString("\n", postfix = "\n"))
                (checked > 0) shouldBe true
                changedAge shouldBe false
                largestChange shouldBeLessThan 1e-8
            } finally {
                restoreTask?.invoke()
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
