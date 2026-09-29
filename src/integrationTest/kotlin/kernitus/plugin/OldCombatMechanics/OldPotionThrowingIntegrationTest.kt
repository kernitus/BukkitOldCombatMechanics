/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics

import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kernitus.plugin.OldCombatMechanics.api.PlayerModuleOverride
import kernitus.plugin.OldCombatMechanics.module.ModuleOldPotionThrowing
import kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerModuleOverrides
import kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage
import kotlinx.coroutines.delay
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.entity.ThrownPotion
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.entity.ProjectileLaunchEvent
import org.bukkit.inventory.meta.PotionMeta
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Vector
import java.util.Random
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalKotest::class)
class OldPotionThrowingIntegrationTest :
    FunSpec({
        val testPlugin = JavaPlugin.getPlugin(OCMTestMain::class.java)
        val ocm = JavaPlugin.getPlugin(OCMMain::class.java)
        val module = ModuleLoader.getModules().filterIsInstance<ModuleOldPotionThrowing>().single()
        val defaults =
            mapOf<String, Any>(
                "launch-speed" to 0.5,
                "pitch-offset" to -20.0,
                "yaw-offset" to 0.0,
                "sideways-offset" to 0.16,
                "vertical-offset" to -0.1,
                "inherit-player-velocity" to false,
                "gravity" to 0.05,
            )
        var saved = mapOf<String, Any>()

        fun configure(vararg values: Pair<String, Any>) {
            values.forEach { (key, value) -> ocm.config.set("old-potion-throwing.$key", value) }
            module.reload()
        }

        fun setMode(
            player: Player,
            mode: String,
        ) {
            val data = PlayerStorage.getPlayerData(player.uniqueId)
            data.setModesetForWorld(player.world.uid, mode)
            PlayerStorage.setPlayerData(player.uniqueId, data)
        }

        fun seed(value: Long) {
            module.javaClass
                .getDeclaredField("random")
                .apply { isAccessible = true }
                .set(module, Random(value))
        }

        fun tracked(): Int {
            val field = module.javaClass.getDeclaredField("activePotions").apply { isAccessible = true }
            return (field.get(module) as Map<*, *>).size
        }

        fun assertVector(
            actual: Vector,
            expected: Vector,
            tolerance: Double = 1e-6,
        ) {
            withClue("actual=$actual expected=$expected") {
                actual.clone().subtract(expected).length() shouldBeLessThan tolerance
            }
        }
        lateinit var fake: FakePlayer
        lateinit var player: Player
        extensions(MainThreadDispatcherExtension(testPlugin))
        beforeTest {
            saved =
                ocm.config
                    .getConfigurationSection("old-potion-throwing")!!
                    .getValues(false)
                    .toMap()
            configure(*defaults.toList().toTypedArray())
            fake = FakePlayer(testPlugin)
            fake.spawn(Location(Bukkit.getWorld("world"), 8.0, 180.0, 8.0, 0f, 0f))
            player = fake.requireBukkitPlayer()
            delay(100)
            setMode(player, "old")
            module.isEnabled(player) shouldBe true
            ProjectileLaunchEvent.getHandlerList().registeredListeners.any { it.listener === module } shouldBe true
        }
        afterTest {
            nativeTestProjectiles(player)
                .filterIsInstance<ThrownPotion>()
                .forEach { it.remove() }
            fake.removePlayer()
            configure(*saved.toList().toTypedArray())
        }
        test("real splash item use starts 0.16 blocks right and 0.1 blocks below the eyes") {
            val eye = player.eyeLocation
            useProjectileItem(player, Material.SPLASH_POTION)
            val potion =
                nativeTestProjectiles(player)
                    .filterIsInstance<ThrownPotion>()
                    .single()
            potion.shooter shouldBe player
            abs(potion.location.x - (eye.x - cos(Math.toRadians(eye.yaw.toDouble())) * 0.16)) shouldBeLessThan 1e-6
            abs(potion.location.y - (eye.y - 0.1)) shouldBeLessThan 1e-6
            abs(potion.location.z - (eye.z - sin(Math.toRadians(eye.yaw.toDouble())) * 0.16)) shouldBeLessThan 1e-6
        }
        test("real splash crossing a loaded chunk boundary survives insertion at its configured origin") {
            val world = player.world
            val chunks = listOf(world.getChunkAt(-1, 0), world.getChunkAt(0, 0))
            val forced =
                chunks.map { chunk ->
                    chunk.load()
                    try {
                        chunk.isForceLoaded.also { chunk.isForceLoaded = true }
                    } catch (_: NoSuchMethodError) {
                        null
                    }
                }
            try {
                player.teleport(Location(world, 0.0, 180.0, 8.0, 0f, 0f))
                delay(150) // Allow neighbouring chunks to enter the native ticking set after teleport.
                val eye = player.eyeLocation
                useProjectileItem(player, Material.SPLASH_POTION)
                val potion = nativeTestProjectiles(player).filterIsInstance<ThrownPotion>().single()
                potion.isValid shouldBe true
                potion.isDead shouldBe false
                potion.shooter shouldBe player
                val expected =
                    eye.toVector().add(
                        Vector(
                            -cos(Math.toRadians(eye.yaw.toDouble())) * 0.16,
                            -0.1,
                            -sin(Math.toRadians(eye.yaw.toDouble())) * 0.16,
                        ),
                    )
                assertVector(potion.location.toVector(), expected)
                val origin = potion.location.toVector()
                repeat(20) {
                    if (potion.ticksLived < 2 && potion.isValid) delay(50)
                }
                potion.isValid shouldBe true
                (potion.ticksLived >= 2) shouldBe true
                (potion.location.toVector().distance(origin) > 0.1) shouldBe true
            } finally {
                chunks.zip(forced).forEach { (chunk, wasForced) ->
                    if (wasForced != null) chunk.isForceLoaded = wasForced
                }
            }
        }

        test("real splash use preserves potion effects and shooter without the effects module") {
            PlayerModuleOverrides.setOverride(
                player,
                "old-potion-effects",
                PlayerModuleOverride.FORCE_DISABLED,
            )
            useProjectileItem(player, Material.SPLASH_POTION) { item ->
                val meta = item.itemMeta as PotionMeta
                meta.addCustomEffect(PotionEffect(PotionEffectType.SPEED, 100, 1), true)
                item.itemMeta = meta
            }
            val potion =
                nativeTestProjectiles(player)
                    .filterIsInstance<ThrownPotion>()
                    .single()
            potion.shooter shouldBe player
            potion.effects.single().duration shouldBe 100
            potion.effects.single().amplifier shouldBe 1
            abs(
                potion.location.x -
                    (player.eyeLocation.x - cos(Math.toRadians(player.eyeLocation.yaw.toDouble())) * 0.16),
            ) shouldBeLessThan
                1e-6
        }
        test("real splash launch applies pitch, yaw and seeded spread before speed") {
            player.teleport(
                player.location.apply {
                    yaw = 43f
                    pitch = 31f
                },
            )
            configure(
                "launch-speed" to 0.8,
                "pitch-offset" to -35.0,
                "yaw-offset" to 22.0,
                "sideways-offset" to -0.3,
                "vertical-offset" to 0.2,
            )
            seed(73)
            val eye = player.eyeLocation
            useProjectileItem(player, Material.SPLASH_POTION)
            val potion =
                nativeTestProjectiles(player)
                    .filterIsInstance<ThrownPotion>()
                    .single()
            val yaw = Math.toRadians(eye.yaw + 22.0)
            val pitch = Math.toRadians(eye.pitch.toDouble())
            val expected =
                Vector(
                    -sin(yaw) * cos(pitch),
                    -sin(Math.toRadians(eye.pitch - 35.0)),
                    cos(yaw) * cos(pitch),
                ).normalize()
            val random = Random(73)
            val spread = Vector(random.nextGaussian(), random.nextGaussian(), random.nextGaussian())
            expected.add(spread.multiply(0.007499999832361937)).multiply(0.8)
            assertVector(potion.velocity, expected)
            val displacement =
                Vector(
                    cos(Math.toRadians(eye.yaw.toDouble())) * 0.3,
                    0.2,
                    sin(Math.toRadians(eye.yaw.toDouble())) * 0.3,
                )
            assertVector(potion.location.toVector(), eye.toVector().add(displacement))
            tracked() shouldBe 0
        }
        test("real throws inherit airborne player velocity only when configured") {
            configure("launch-speed" to 0.0, "inherit-player-velocity" to true)
            player.isOnGround shouldBe false
            val inherited = Vector(0.15, 0.12, -0.1)
            player.velocity = inherited
            useProjectileItem(player, Material.SPLASH_POTION)
            val potion =
                nativeTestProjectiles(player)
                    .filterIsInstance<ThrownPotion>()
                    .single()
            assertVector(potion.velocity, inherited)
            potion.remove()
            configure("inherit-player-velocity" to false)
            useProjectileItem(player, Material.SPLASH_POTION)
            assertVector(
                nativeTestProjectiles(player)
                    .filterIsInstance<ThrownPotion>()
                    .single()
                    .velocity,
                Vector(),
            )
        }
        test("grounded throws inherit horizontal velocity only") {
            val block = player.world.getBlockAt(0, 179, 0)
            val previous = block.state
            try {
                block.type = Material.STONE
                player.teleport(Location(player.world, 0.5, 180.0, 0.5))
                delay(250)
                player.isOnGround shouldBe true
                configure("launch-speed" to 0.0, "inherit-player-velocity" to true)
                player.velocity = Vector(0.1, 0.4, -0.1)
                useProjectileItem(player, Material.SPLASH_POTION)
                val potion =
                    nativeTestProjectiles(player)
                        .filterIsInstance<ThrownPotion>()
                        .single()
                assertVector(potion.velocity, Vector(0.1, 0.0, -0.1))
            } finally {
                previous.update(true, false)
            }
        }
        test("new modeset splash and lingering item use keep native origin") {
            setMode(player, "new")
            var eye = player.eyeLocation
            useProjectileItem(player, Material.SPLASH_POTION)
            var potion =
                nativeTestProjectiles(player)
                    .filterIsInstance<ThrownPotion>()
                    .single()
            abs(potion.location.x - eye.x) shouldBeLessThan 1e-6
            potion.remove()
            setMode(player, "old")
            eye = player.eyeLocation
            useProjectileItem(player, Material.LINGERING_POTION)
            potion =
                nativeTestProjectiles(player)
                    .filterIsInstance<ThrownPotion>()
                    .single()
            abs(potion.location.x - eye.x) shouldBeLessThan 1e-6
            tracked() shouldBe 0
        }
        test("API launch scope excludes witches and dispensers") {
            configure("launch-speed" to 0.0, "gravity" to 0.1)
            val witch =
                player.world.spawn(
                    player.location.clone().add(4.0, 0.0, 0.0),
                    org.bukkit.entity.Witch::class.java,
                )
            val block = player.world.getBlockAt(4, 180, 4)
            val savedBlock = block.state
            var witchPotion: ThrownPotion? = null
            var dispenserPotion: ThrownPotion? = null
            try {
                witchPotion = witch.launchProjectile(ThrownPotion::class.java)
                witchPotion.shooter shouldBe witch
                (witchPotion.velocity.length() > 0.1) shouldBe true
                block.type = Material.DISPENSER
                val source = (block.state as org.bukkit.block.Dispenser).blockProjectileSource!!
                dispenserPotion = source.launchProjectile(ThrownPotion::class.java)
                dispenserPotion.shooter shouldBe source
                (dispenserPotion.velocity.length() > 0.1) shouldBe true
                tracked() shouldBe 0
            } finally {
                witchPotion?.remove()
                dispenserPotion?.remove()
                witch.remove()
                savedBlock.update(true, false)
            }
        }

        test("cancelled real item launch leaves no potion or gravity tracker") {
            configure("gravity" to 0.1)
            val listener = object : Listener {}
            Bukkit.getPluginManager().registerEvent(
                ProjectileLaunchEvent::class.java,
                listener,
                EventPriority.LOWEST,
                { _, event ->
                    if ((event as ProjectileLaunchEvent).entity.shooter == player) event.isCancelled = true
                },
                testPlugin,
            )
            try {
                useProjectileItem(player, Material.SPLASH_POTION)
                nativeTestProjectiles(player)
                    .filterIsInstance<ThrownPotion>()
                    .isEmpty() shouldBe true
                tracked() shouldBe 0
            } finally {
                HandlerList.unregisterAll(listener)
            }
        }
        test("native splash trajectories honour default, zero and custom gravity with native drag") {
            var gravityFactor = 0.0
            for (gravity in listOf(0.05, 0.0, 0.1)) {
                configure("gravity" to gravity)
                val samples =
                    recordNativeFlight(testPlugin) {
                        useProjectileItem(player, Material.SPLASH_POTION)
                        nativeTestProjectiles(player).filterIsInstance<ThrownPotion>().single().also {
                            it.velocity = Vector(0.0, 0.3, 0.5)
                        }
                    }
                val displacement = samples.zipWithNext { a, b -> b.position.clone().subtract(a.position) }
                if (gravity == 0.05) {
                    // Derive the server's native gravity ordering from its unmodified default trajectory.
                    gravityFactor = (0.99 * displacement[0].y - displacement[1].y) / 0.05
                    minOf(abs(gravityFactor - 1.0), abs(gravityFactor - 0.99)) shouldBeLessThan 1e-5
                }
                // First displacement is native even with custom gravity, as item use can follow the scheduler.
                val firstY = if (abs(gravityFactor - 1.0) < 1e-5) 0.3 else (0.3 - 0.05) * 0.99
                abs(displacement[0].y - firstY) shouldBeLessThan 1e-6
                displacement.zipWithNext().forEachIndexed { index, (previous, next) ->
                    withClue("gravity=$gravity tick=$index samples=$samples") {
                        abs(next.y - (previous.y * 0.99 - gravity * gravityFactor)) shouldBeLessThan 2e-6
                        abs(next.z - previous.z * 0.99) shouldBeLessThan 2e-6
                    }
                }
                nativeTestProjectiles(player)
                    .filterIsInstance<ThrownPotion>()
                    .forEach { it.remove() }
            }
        }
        test("synthetic missing post-add capability preserves native travel while applying the offset") {
            setMode(player, "new")
            val baseline =
                recordNativeFlight(testPlugin) {
                    useProjectileItem(player, Material.SPLASH_POTION)
                    nativeTestProjectiles(player).filterIsInstance<ThrownPotion>().single().also {
                        it.velocity = Vector(0.0, 0.3, 0.5)
                    }
                }
            nativeTestProjectiles(player).forEach { it.remove() }
            setMode(player, "old")
            val listener =
                module.javaClass
                    .getDeclaredField("additionListener")
                    .apply { isAccessible = true }
                    .get(module) as Listener
            HandlerList.unregisterAll(listener)
            val insertionCapability = module.javaClass.getDeclaredField("modernInsertion").apply { isAccessible = true }
            val detectedInsertion = insertionCapability.get(module)
            insertionCapability.set(module, false)
            try {
                val eye = player.eyeLocation
                val origin =
                    eye
                        .clone()
                        .add(
                            -cos(Math.toRadians(eye.yaw.toDouble())) * 0.16,
                            -0.1,
                            -sin(Math.toRadians(eye.yaw.toDouble())) * 0.16,
                        ).toVector()
                val fallback =
                    recordNativeFlight(testPlugin) {
                        useProjectileItem(player, Material.SPLASH_POTION)
                        nativeTestProjectiles(player).filterIsInstance<ThrownPotion>().single().also {
                            it.velocity = Vector(0.0, 0.3, 0.5)
                        }
                    }
                val nativeTravel =
                    baseline
                        .last()
                        .position
                        .clone()
                        .subtract(baseline.first().position)
                assertVector(fallback.last().position, origin.add(nativeTravel), 2e-6)
                module.javaClass
                    .getDeclaredField("gravityTask")
                    .apply { isAccessible = true }
                    .get(module) shouldBe null
            } finally {
                insertionCapability.set(module, detectedInsertion)
                module.javaClass
                    .getDeclaredMethod(
                        "registerAdditionListener",
                    ).apply { isAccessible = true }
                    .invoke(module)
            }
        }

        test("gravity trackers stop after reload, modeset change, removal and external no-gravity") {
            for (action in listOf("reload", "modeset", "remove", "no-gravity")) {
                setMode(player, "old")
                configure("gravity" to 0.1)
                useProjectileItem(player, Material.SPLASH_POTION)
                val potion =
                    nativeTestProjectiles(player)
                        .filterIsInstance<ThrownPotion>()
                        .single()
                tracked() shouldBe 1
                when (action) {
                    "reload" -> module.reload()
                    "modeset" -> setMode(player, "new")
                    "remove" -> potion.remove()
                    else -> potion.setGravity(false)
                }
                delay(150)
                tracked() shouldBe 0
                val taskField = module.javaClass.getDeclaredField("gravityTask").apply { isAccessible = true }
                taskField.get(module) shouldBe null
                potion.remove()
            }
        }
        test("invalid numeric values fall back to finite vanilla launch defaults") {
            configure(
                "launch-speed" to Double.NaN,
                "gravity" to -0.1,
                "pitch-offset" to Double.POSITIVE_INFINITY,
                "yaw-offset" to "bad",
                "sideways-offset" to 50.0,
                "vertical-offset" to Double.NEGATIVE_INFINITY,
            )
            seed(0)
            useProjectileItem(player, Material.SPLASH_POTION)
            val potion =
                nativeTestProjectiles(player)
                    .filterIsInstance<ThrownPotion>()
                    .single()
            potion.velocity.checkFinite()
            abs(
                potion.location.x -
                    (player.eyeLocation.x - cos(Math.toRadians(player.eyeLocation.yaw.toDouble())) * 0.16),
            ) shouldBeLessThan
                1e-6
            abs(potion.velocity.length() - 0.5) shouldBeLessThan 0.02
            tracked() shouldBe 0
        }
    })
