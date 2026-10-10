/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics

import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import kernitus.plugin.OldCombatMechanics.utilities.Config
import kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage
import kotlinx.coroutines.delay
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Arrow
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.entity.Snowball
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityShootBowEvent
import org.bukkit.event.entity.ProjectileLaunchEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.util.Vector
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

/** Real native held-stack use; legacy capability cases assert preservation explicitly. */
@OptIn(ExperimentalKotest::class)
class ProjectileLaunchFidelityIntegrationTest :
    FunSpec({
        val plugin = JavaPlugin.getPlugin(OCMTestMain::class.java)
        val ocm = JavaPlugin.getPlugin(OCMMain::class.java)
        extensions(MainThreadDispatcherExtension(plugin))
        val launchType =
            runCatching {
                Class
                    .forName(
                        "com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent",
                    ).asSubclass(Event::class.java)
            }.getOrNull()
        val modern = launchType != null
        val trajectory = "old-projectile-trajectory"
        val offset = "projectile-shoot-offset"
        val relative = "relative-projectile-velocity"
        val all = listOf(trajectory, offset, relative)
        val snowball = Material.matchMaterial("SNOWBALL") ?: Material.valueOf("SNOW_BALL")
        val materials =
            listOf(
                Material.EGG,
                snowball,
                Material.ENDER_PEARL,
                Material.SPLASH_POTION,
                Material.matchMaterial("EXPERIENCE_BOTTLE") ?: Material.valueOf("EXP_BOTTLE"),
            )
        lateinit var saved: String
        lateinit var fake: FakePlayer
        lateinit var player: Player
        val listeners = mutableListOf<Listener>()
        val entities = mutableListOf<Projectile>()
        var incoming: Vector? = null
        var incomingPosition: Vector? = null
        var force = 0.0

        fun configure(names: List<String>) {
            ocm.config.set("always_enabled_modules", names)
            ocm.config.set("disabled_modules", ModuleLoader.getConfigurableModuleNames().filterNot { it in names })
            ocm.config.set("modesets", mapOf("old" to emptyList<String>(), "new" to emptyList<String>()))
            ocm.saveConfig()
            Config.reload()
        }

        fun listen(
            type: Class<out Event>,
            priority: EventPriority,
            action: (Event) -> Unit,
        ) {
            val listener = object : Listener {}
            listeners.add(listener)
            Bukkit.getPluginManager().registerEvent(type, listener, priority, { _, event -> action(event) }, plugin)
        }

        fun assertVector(
            actual: Vector,
            expected: Vector,
            tolerance: Double = 1e-8,
        ) {
            withClue("actual=$actual expected=$expected") {
                actual.x shouldBe (expected.x plusOrMinus tolerance)
                actual.y shouldBe (expected.y plusOrMinus tolerance)
                actual.z shouldBe (expected.z plusOrMinus tolerance)
            }
        }

        fun launch(
            material: Material,
            offHand: Boolean = false,
        ): Projectile {
            incoming = null
            incomingPosition = null
            useProjectileItem(player, material, offHand)
            return nativeTestProjectiles(player).single().also { entities.add(it) }
        }

        fun remove(projectile: Projectile) {
            projectile.remove()
            entities.remove(projectile)
        }

        fun expectedDirection(
            material: Material,
            speed: Double,
        ): Vector {
            val yaw = Math.toRadians(player.location.yaw.toDouble())
            val pitch = Math.toRadians(player.location.pitch.toDouble())
            val adjusted =
                material == Material.SPLASH_POTION || material.name in setOf("EXPERIENCE_BOTTLE", "EXP_BOTTLE")
            val yPitch = Math.toRadians(player.location.pitch + if (adjusted) -20.0 else 0.0)
            return Vector(-sin(yaw) * cos(pitch), -sin(yPitch), cos(yaw) * cos(pitch)).normalize().multiply(speed)
        }

        fun nativeMotion(): Vector {
            val handle = player.javaClass.getMethod("getHandle").invoke(player)
            val getter =
                handle.javaClass.methods.first {
                    it.parameterCount == 0 &&
                        it.name in setOf("getKnownMovement", "dd")
                }
            val value = getter.invoke(handle)
            val names =
                if (value.javaClass.fields.any { it.name == "x" }) {
                    listOf(
                        "x",
                        "y",
                        "z",
                    )
                } else {
                    listOf("c", "d", "e")
                }
            val axes = names.map { value.javaClass.getField(it).getDouble(value) }
            return Vector(axes[0], axes[1], axes[2])
        }

        fun paperFlag(): Pair<Any, java.lang.reflect.Field> {
            val world =
                player.world.javaClass
                    .getMethod("getHandle")
                    .invoke(player.world)
            val config = world.javaClass.getMethod("paperConfig").invoke(world)
            val misc = config.javaClass.getField("misc").get(config)
            return misc to misc.javaClass.getField("disableRelativeProjectileVelocity")
        }

        suspend fun moveThroughConnection(
            grounded: Boolean,
            settle: Boolean = true,
        ) {
            val handle = player.javaClass.getMethod("getHandle").invoke(player)
            val connection = fake.getConnection(handle)
            val mapped = connection.javaClass.methods.any { it.name == "handleMovePlayer" }
            val loadedHandler = connection.javaClass.methods.firstOrNull { it.name == "handleAcceptPlayerLoad" }
            if (loadedHandler != null) {
                loadedHandler.invoke(
                    connection,
                    loadedHandler.parameterTypes
                        .single()
                        .getConstructor()
                        .newInstance(),
                )
            }
            val teleport =
                connection.javaClass
                    .getDeclaredField(if (mapped) "awaitingTeleport" else "D")
                    .apply {
                        isAccessible =
                            true
                    }.getInt(connection)
            val ackType =
                Class.forName(
                    "net.minecraft.network.protocol.game." +
                        if (mapped) "ServerboundAcceptTeleportationPacket" else "PacketPlayInTeleportAccept",
                )
            val ack = ackType.getConstructor(Int::class.javaPrimitiveType).newInstance(teleport)
            connection.javaClass.methods
                .single {
                    it.parameterTypes.contentEquals(
                        arrayOf(ackType),
                    )
                }.invoke(connection, ack)
            if (settle) delay(100)
            val packetType =
                Class.forName(
                    "net.minecraft.network.protocol.game." +
                        if (mapped) {
                            "ServerboundMovePlayerPacket\$PosRot"
                        } else {
                            "PacketPlayInFlying\$PacketPlayInPositionLook"
                        },
                )
            val constructor =
                packetType.constructors.single {
                    it.parameterTypes.first() == Double::class.javaPrimitiveType
                }
            val before = player.location
            val destination = before.clone().add(0.3, if (grounded) 0.0 else 0.25, 0.1)
            val arguments =
                mutableListOf<Any>(
                    destination.x,
                    destination.y,
                    destination.z,
                    destination.yaw,
                    destination.pitch,
                    grounded,
                )
            if (constructor.parameterCount == 7) arguments.add(false)
            val packet = constructor.newInstance(*arguments.toTypedArray())
            player.velocity = Vector(0.7, 0.3, -0.2)
            connection.javaClass.methods
                .single {
                    it.parameterTypes.contentEquals(arrayOf(packetType.superclass))
                }.invoke(connection, packet)
            assertVector(player.location.toVector(), destination.toVector(), 1e-6)
            if (mapped) {
                // This checks real packet bookkeeping; setVelocity alone cannot populate known movement.
                (nativeMotion().distance(player.velocity) > 0.1) shouldBe true
                (nativeMotion().lengthSquared() > 0.001) shouldBe true
            }
            player.isOnGround shouldBe grounded
        }

        beforeTest {
            saved = ocm.config.saveToString()
            configure(all)
            fake = FakePlayer(plugin)
            fake.spawn(Location(Bukkit.getWorld("world"), 8.0, 180.0, 8.0, 37f, 24f))
            player = fake.requireBukkitPlayer()
            player.gameMode = GameMode.CREATIVE
            player.allowFlight = true
            player.isFlying = true
            delay(100)
            player.teleport(Location(player.world, 8.0, 180.0, 8.0, 37f, 24f))
            player.velocity = Vector()
            if (launchType != null) {
                val getter = launchType.getMethod("getProjectile")
                listen(launchType, EventPriority.LOWEST) { event ->
                    val projectile = getter.invoke(event) as Projectile
                    if (projectile.shooter == player) {
                        incoming = projectile.velocity.clone()
                        incomingPosition = projectile.location.toVector()
                    }
                }
            } else {
                listen(ProjectileLaunchEvent::class.java, EventPriority.LOWEST) { event ->
                    val projectile = (event as ProjectileLaunchEvent).entity
                    if (projectile.shooter == player) {
                        incoming = projectile.velocity.clone()
                        incomingPosition = projectile.location.toVector()
                    }
                }
            }
            listen(EntityShootBowEvent::class.java, EventPriority.LOWEST) { raw ->
                val event = raw as EntityShootBowEvent
                if (event.entity == player) {
                    incoming = event.projectile.velocity.clone()
                    incomingPosition = event.projectile.location.toVector()
                    force = event.force.toDouble()
                }
            }
        }
        afterTest {
            listeners.forEach { HandlerList.unregisterAll(it) }
            listeners.clear()
            entities.forEach { it.remove() }
            entities.clear()
            nativeTestProjectiles(player).forEach { it.remove() }
            fake.removePlayer()
            ocm.config.loadFromString(saved)
            ocm.saveConfig()
            Config.reload()
        }

        for (material in materials) {
            for (selected in listOf(listOf(trajectory), listOf(offset), all)) {
                for (sneaking in listOf(false, true)) {
                    test("native ${material.name} ${selected.joinToString("+")} sneaking=$sneaking") {
                        configure(selected)
                        player.isSneaking = sneaking
                        val speed =
                            when (material.name) {
                                "SPLASH_POTION" -> 0.5
                                "EXPERIENCE_BOTTLE", "EXP_BOTTLE" -> 0.7
                                else -> 1.5
                            }
                        val velocities = mutableListOf<Vector>()
                        repeat(if (trajectory in selected && modern) 256 else 1) {
                            val projectile = launch(material)
                            if (!modern) {
                                assertVector(projectile.velocity, checkNotNull(incoming))
                                assertVector(projectile.location.toVector(), checkNotNull(incomingPosition))
                            } else {
                                if (offset in selected) {
                                    val yaw = Math.toRadians(player.location.yaw.toDouble())
                                    val expected =
                                        player.location.toVector().add(
                                            Vector(
                                                -cos(yaw) * 0.16,
                                                (if (sneaking) 1.54 else 1.62) - 0.1,
                                                -sin(yaw) * 0.16,
                                            ),
                                        )
                                    assertVector(projectile.location.toVector(), expected, 1e-6)
                                    projectile.isValid shouldBe true
                                } else {
                                    assertVector(projectile.location.toVector(), checkNotNull(incomingPosition))
                                }
                                if (trajectory !in selected) assertVector(projectile.velocity, checkNotNull(incoming))
                                velocities.add(projectile.velocity.clone().multiply(1.0 / speed))
                            }
                            remove(projectile)
                        }
                        if (trajectory in selected && modern) {
                            // 256 independent Gaussian draws per axis. Mean bound is 6.4 standard errors;
                            // variance bounds exceed five standard errors, avoiding random-seed assumptions.
                            val mean =
                                velocities
                                    .fold(
                                        Vector(),
                                    ) { sum, value -> sum.add(value) }
                                    .multiply(1.0 / velocities.size)
                            assertVector(mean, expectedDirection(material, 1.0), 0.003)
                            for (axis in 0..2) {
                                fun component(v: Vector) = listOf(v.x, v.y, v.z)[axis]
                                val variance =
                                    velocities.sumOf {
                                        val d = component(it) - component(mean)
                                        d * d
                                    } / 255.0
                                (variance in 0.000025..0.000095) shouldBe true
                            }
                        }
                    }
                }
            }
            test("native offhand ${material.name} remains native with identical main-hand item") {
                player.inventory.setItemInMainHand(ItemStack(material, 16))
                val projectile = launch(material, true)
                assertVector(projectile.velocity, checkNotNull(incoming))
                assertVector(projectile.location.toVector(), checkNotNull(incomingPosition))
            }
        }

        test("plugin-created snowball keeps its explicit position and velocity") {
            val location = player.eyeLocation.clone().add(0.4, 0.2, 0.3)
            val projectile = player.world.spawn(location, Snowball::class.java)
            entities.add(projectile)
            projectile.shooter = player
            projectile.velocity = Vector(0.6, 0.7, 0.8)
            assertVector(projectile.location.toVector(), location.toVector())
            assertVector(projectile.velocity, Vector(0.6, 0.7, 0.8))
        }

        test("native lingering potion retains native launch geometry and velocity") {
            val projectile = launch(Material.LINGERING_POTION)
            assertVector(projectile.velocity, checkNotNull(incoming))
            assertVector(projectile.location.toVector(), checkNotNull(incomingPosition))
        }

        test("old potion throwing owns splash even with all generic modules enabled") {
            configure(all + "old-potion-throwing")
            val module = ModuleLoader.getModules().first { it.configName == "old-potion-throwing" }
            ocm.config.set("old-potion-throwing.launch-speed", 0.9)
            ocm.config.set("old-potion-throwing.sideways-offset", 0.35)
            module.reload()
            val projectile = launch(Material.SPLASH_POTION)
            projectile.velocity.length() shouldBe (0.9 plusOrMinus 0.035)
            val yaw = Math.toRadians(player.location.yaw.toDouble())
            val expected = player.eyeLocation.toVector().add(Vector(-cos(yaw) * 0.35, -0.1, -sin(yaw) * 0.35))
            assertVector(projectile.location.toVector(), expected, 1e-6)
        }

        for (priority in listOf(EventPriority.LOW, EventPriority.HIGHEST)) {
            test("native throwable cancelled at $priority never survives insertion") {
                val type = launchType ?: ProjectileLaunchEvent::class.java
                var cancelled: Projectile? = null
                listen(type, priority) { event ->
                    val entity =
                        if (event is ProjectileLaunchEvent) {
                            event.entity
                        } else {
                            type
                                .getMethod(
                                    "getProjectile",
                                ).invoke(event) as Projectile
                        }
                    if (entity.shooter == player) {
                        (event as Cancellable).isCancelled = true
                        cancelled = entity
                    }
                }
                useProjectileItem(player, snowball)
                val projectile = checkNotNull(cancelled)
                delay(100)
                projectile.isValid shouldBe false
                nativeTestProjectiles(player).isEmpty() shouldBe true
            }
        }

        // Use the server owner's configured registry entries, never a server-specific material list.
        val customBows =
            ocm.config.getStringList("projectile-launch.custom-bows").map { name ->
                checkNotNull(Material.matchMaterial(name.trim())) { "Unregistered custom bow fixture: $name" }
            }.distinct().filterNot { it == Material.BOW }
        for (bow in listOf(Material.BOW) + customBows) {
            for (charge in listOf(5, 22)) {
                test("native ${bow.name} force after $charge ticks controls legacy arrow speed") {
                    if (charge == 5) configure(listOf(trajectory, offset))
                    player.inventory.addItem(ItemStack(Material.ARROW, 64))
                    useProjectileItem(player, bow)
                    delay(charge * 50L)
                    if (modern) moveThroughConnection(false, false)
                    val inherited = if (modern && charge == 5) nativeMotion() else Vector()
                    releaseProjectileItem(player)
                    val arrow = nativeTestProjectiles(player).filterIsInstance<Arrow>().single()
                    entities.add(arrow)
                    if (modern) {
                        (force > 0.0) shouldBe true
                        assertVector(
                            arrow.velocity,
                            expectedDirection(Material.BOW, 3.0 * force).add(inherited),
                            0.15,
                        )
                        arrow.velocity
                            .clone()
                            .subtract(inherited)
                            .length() shouldBe (3.0 * force plusOrMinus 0.08)
                        val yaw = Math.toRadians(player.location.yaw.toDouble())
                        assertVector(
                            arrow.location.toVector(),
                            player.location.toVector().add(
                                Vector(
                                    -cos(yaw) * 0.16,
                                    1.52,
                                    -sin(yaw) * 0.16,
                                ),
                            ),
                            1e-6,
                        )
                    } else {
                        assertVector(arrow.velocity, checkNotNull(incoming))
                    }
                }
            }
        }

        for (bow in customBows) {
            test("custom ${bow.name} eligibility changes on reload") {
                ocm.config.set("projectile-launch.custom-bows", emptyList<String>())
                ocm.saveConfig()
                Config.reload()
                player.inventory.addItem(ItemStack(Material.ARROW, 64))
                useProjectileItem(player, bow)
                delay(1100)
                releaseProjectileItem(player)
                val native = nativeTestProjectiles(player).filterIsInstance<Arrow>().single()
                entities.add(native)
                assertVector(native.velocity, checkNotNull(incoming))
                assertVector(native.location.toVector(), checkNotNull(incomingPosition))
                remove(native)

                ocm.config.set("projectile-launch.custom-bows", listOf(bow.name))
                ocm.saveConfig()
                Config.reload()
                useProjectileItem(player, bow)
                delay(1100)
                if (modern) moveThroughConnection(false, false)
                releaseProjectileItem(player)
                val corrected = nativeTestProjectiles(player).filterIsInstance<Arrow>().single()
                entities.add(corrected)
                if (modern) {
                    assertVector(corrected.velocity, expectedDirection(Material.BOW, 3.0 * force), 0.15)
                    val yaw = Math.toRadians(player.location.yaw.toDouble())
                    assertVector(
                        corrected.location.toVector(),
                        player.location.toVector().add(Vector(-cos(yaw) * 0.16, 1.52, -sin(yaw) * 0.16)),
                        1e-6,
                    )
                } else {
                    assertVector(corrected.velocity, checkNotNull(incoming))
                }
            }
        }

        for (priority in listOf(EventPriority.LOWEST, EventPriority.HIGHEST)) {
            test("foreign bow replacement at $priority retains its own launch") {
                val replacement = player.world.spawn(player.eyeLocation, Arrow::class.java)
                entities.add(replacement)
                replacement.shooter = player
                val velocity = Vector(0.35, 0.22, 0.45)
                replacement.velocity = velocity
                listen(EntityShootBowEvent::class.java, priority) { raw ->
                    val event = raw as EntityShootBowEvent
                    if (event.entity == player) event.projectile = replacement
                }
                player.inventory.addItem(ItemStack(Material.ARROW, 64))
                useProjectileItem(player, Material.BOW)
                delay(1100)
                // Keep the replacement stationary during charging, then restore the foreign motion.
                replacement.teleport(player.eyeLocation)
                replacement.velocity = velocity
                releaseProjectileItem(player)
                assertVector(replacement.velocity, velocity)
            }
        }

        test("modeset change enables offset for the selected player and reload clears pending state") {
            configure(emptyList())
            ocm.config.set("disabled_modules", ModuleLoader.getConfigurableModuleNames().filterNot { it == offset })
            ocm.config.set("modesets", mapOf("old" to listOf(offset), "new" to emptyList<String>()))
            ocm.saveConfig()
            Config.reload()
            val data = PlayerStorage.getPlayerData(player.uniqueId)
            for (mode in listOf("new", "old", "new")) {
                data.setModesetForWorld(player.world.uid, mode)
                PlayerStorage.setPlayerData(player.uniqueId, data)
                val projectile = launch(snowball)
                if (mode == "old" && modern) {
                    val yaw = Math.toRadians(player.location.yaw.toDouble())
                    assertVector(
                        projectile.location.toVector(),
                        player.location.toVector().add(
                            Vector(
                                -cos(yaw) * 0.16,
                                1.52,
                                -sin(yaw) * 0.16,
                            ),
                        ),
                        1e-6,
                    )
                } else {
                    assertVector(projectile.location.toVector(), checkNotNull(incomingPosition))
                }
                remove(projectile)
                ocm.saveConfig()
                Config.reload()
            }
        }

        for (grounded in listOf(false, true)) {
            for (selected in listOf(listOf(relative), listOf(trajectory), listOf(trajectory, relative))) {
                for (flag in listOf(false, true)) {
                    test(
                        "native connection movement grounded=$grounded " +
                            "modules=${selected.joinToString("+")} paperDisabled=$flag",
                    ) {
                        configure(selected)
                        if (modern) {
                            val floor =
                                (-1..1).flatMap { x ->
                                    (-1..1).map { z -> player.world.getBlockAt(8 + x, 179, 8 + z) }
                                }
                            val states = floor.map { it.state }
                            val (misc, field) = paperFlag()
                            val previous = field.getBoolean(misc)
                            try {
                                floor.forEach { it.type = Material.STONE }
                                field.setBoolean(misc, flag)
                                moveThroughConnection(grounded)
                                val inherited = if (flag) Vector() else nativeMotion().also { if (grounded) it.y = 0.0 }
                                val velocities = mutableListOf<Vector>()
                                repeat(if (trajectory in selected) 256 else 1) {
                                    val projectile = launch(snowball)
                                    if (trajectory !in
                                        selected
                                    ) {
                                        assertVector(
                                            projectile.velocity,
                                            checkNotNull(incoming).clone().subtract(inherited),
                                        )
                                    }
                                    velocities.add(projectile.velocity.clone())
                                    field.getBoolean(misc) shouldBe flag
                                    remove(projectile)
                                }
                                if (trajectory in selected) {
                                    val mean =
                                        velocities
                                            .fold(
                                                Vector(),
                                            ) { sum, v -> sum.add(v) }
                                            .multiply(1.0 / velocities.size)
                                    val expected = expectedDirection(snowball, 1.5)
                                    if (relative !in selected) expected.add(inherited)
                                    assertVector(mean, expected, 0.005)
                                }
                            } finally {
                                field.setBoolean(misc, previous)
                                states.forEach { it.update(true, false) }
                            }
                        } else {
                            player.velocity = Vector(0.3, 0.2, 0.1)
                            val projectile = launch(snowball)
                            assertVector(projectile.velocity, checkNotNull(incoming))
                        }
                    }
                }
            }
        }

        test("8192 native launches restore Gaussian fourth moment against a disabled control") {
            if (modern) {
                val results = mutableListOf<String>()
                for (enabled in listOf(false, true)) {
                    configure(if (enabled) listOf(trajectory, relative) else emptyList())
                    val centre = expectedDirection(snowball, 1.0)
                    val values = Array(3) { mutableListOf<Double>() }
                    repeat(8192) {
                        val projectile = launch(snowball)
                        val residual =
                            projectile.velocity
                                .clone()
                                .multiply(1.0 / 1.5)
                                .subtract(centre)
                        values[0].add(residual.x)
                        values[1].add(residual.y)
                        values[2].add(residual.z)
                        remove(projectile)
                    }
                    values.forEachIndexed { axis, samples ->
                        val mean = samples.average()
                        val variance = samples.sumOf { (it - mean) * (it - mean) } / samples.size
                        val fourth =
                            samples.sumOf {
                                val d = it - mean
                                d * d * d * d
                            } / samples.size
                        val kurtosis = fourth / (variance * variance)
                        results.add(
                            "enabled=$enabled axis=$axis n=${samples.size} mean=$mean " +
                                "variance=$variance kurtosis=$kurtosis",
                        )
                        if (enabled) {
                            // Gaussian kurtosis SE sqrt(24/8192)=0.0541. These bounds exceed 5.5 SE;
                            // the modern triangle's population kurtosis 2.4 lies outside them.
                            mean shouldBe (0.0 plusOrMinus 0.0006)
                            variance shouldBe (0.00005625 plusOrMinus 0.0000045)
                            kurtosis shouldBe (3.0 plusOrMinus 0.3)
                        }
                    }
                }
                File(
                    plugin.dataFolder,
                    "projectile-launch-distribution.txt",
                ).writeText(results.joinToString("\n") + "\n")
            } else {
                val projectile = launch(snowball)
                assertVector(projectile.velocity, checkNotNull(incoming))
            }
        }

        test("native offset crosses chunk boundary and preserves first collision") {
            configure(listOf(offset))
            val world = player.world
            val chunks = listOf(world.getChunkAt(-1, 0), world.getChunkAt(0, 0))
            val forced =
                chunks.map { chunk ->
                    chunk.load()
                    runCatching {
                        chunk.isForceLoaded.also {
                            chunk.isForceLoaded =
                                true
                        }
                    }.getOrNull()
                }
            val block = world.getBlockAt(-1, 181, 9)
            val state = block.state
            var collision: org.bukkit.event.entity.ProjectileHitEvent? = null
            try {
                block.type = Material.STONE
                player.teleport(Location(world, 0.1, 180.0, 8.0, 0f, 0f))
                delay(150)
                listen(launchType ?: ProjectileLaunchEvent::class.java, EventPriority.LOW) { event ->
                    val projectile =
                        if (event is ProjectileLaunchEvent) {
                            event.entity
                        } else {
                            launchType!!
                                .getMethod(
                                    "getProjectile",
                                ).invoke(event) as Projectile
                        }
                    projectile.velocity = Vector(0.0, 0.0, 1.5)
                }
                listen(org.bukkit.event.entity.ProjectileHitEvent::class.java, EventPriority.MONITOR) { event ->
                    val hit = event as org.bukkit.event.entity.ProjectileHitEvent
                    if (hit.entity.shooter == player) collision = hit
                }
                val projectile = launch(snowball)
                projectile.isValid shouldBe true
                if (modern) {
                    assertVector(projectile.location.toVector(), Vector(-0.06, 181.52, 8.0), 1e-6)
                    delay(100)
                    checkNotNull(collision).hitBlock shouldBe block
                    (checkNotNull(collision).entity.ticksLived <= 2) shouldBe true
                } else {
                    assertVector(projectile.location.toVector(), checkNotNull(incomingPosition))
                    assertVector(projectile.velocity, Vector(0.0, 0.0, 1.5))
                }
            } finally {
                state.update(true, false)
                chunks.zip(forced).forEach { (chunk, old) -> if (old != null) chunk.isForceLoaded = old }
            }
        }

        test("bundled launch modules are disabled and assigned exactly once") {
            val bundled =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                    checkNotNull(ocm.getResource("config.yml")).reader(),
                )
            val lists =
                listOf("always_enabled_modules", "disabled_modules") +
                    bundled.getConfigurationSection("modesets")!!.getKeys(false).map { "modesets.$it" }
            for (name in all) {
                bundled.getStringList("disabled_modules").contains(name) shouldBe true
                lists.sumOf { key -> bundled.getStringList(key).count { it == name } } shouldBe 1
            }
        }

        test("re-entrant synthetic Paper launch cannot double-correct the native projectile") {
            configure(listOf(relative))
            if (launchType != null) {
                moveThroughConnection(false)
                val inherited = nativeMotion()
                (inherited.lengthSquared() > 0.001) shouldBe true
                var nested = false
                var captured: Event? = null
                var before: Vector? = null
                val getter = launchType.getMethod("getProjectile")
                listen(launchType, EventPriority.LOW) { event ->
                    if (!nested) {
                        captured = event
                        val projectile = getter.invoke(event) as Projectile
                        val prior = projectile.velocity.clone()
                        nested = true
                        Bukkit.getPluginManager().callEvent(event)
                        nested = false
                        assertVector(projectile.velocity, prior)
                        before = prior
                    }
                }
                val projectile = launch(snowball)
                assertVector(projectile.velocity, checkNotNull(before).clone().subtract(inherited))
                val corrected = projectile.velocity.clone()
                // A duplicate dispatch after insertion has no native stack attribution.
                nested = true
                Bukkit.getPluginManager().callEvent(checkNotNull(captured))
                assertVector(projectile.velocity, corrected)
            } else {
                val projectile = launch(snowball)
                assertVector(projectile.velocity, checkNotNull(incoming))
            }
        }

        suspend fun prepareCapturedLaunch(bow: Boolean) {
            if (bow) {
                player.inventory.addItem(ItemStack(Material.ARROW, 64))
                useProjectileItem(player, Material.BOW)
                delay(1100)
            }
            if (modern) moveThroughConnection(false, false)
        }

        fun finishCapturedLaunch(bow: Boolean): Projectile {
            if (!bow) return launch(snowball)
            releaseProjectileItem(player)
            return nativeTestProjectiles(player).single().also { entities.add(it) }
        }

        fun capturedEventType(bow: Boolean): Class<out Event> =
            if (bow) EntityShootBowEvent::class.java else launchType ?: ProjectileLaunchEvent::class.java

        for (bow in listOf(false, true)) {
            for (module in listOf(relative, trajectory)) {
                for (change in listOf("motion", "ground", "flag-on", "flag-off")) {
                    test("captured native $bow $module survives later $change").config(
                        enabledOrReasonIf = {
                            io.kotest.core.test
                                .Enabled(modern, "Native item attribution is absent")
                        },
                    ) {
                        configure(listOf(module))
                        val (misc, field) = paperFlag()
                        val original = field.getBoolean(misc)
                        val initiallyDisabled = change == "flag-off"
                        try {
                            field.setBoolean(misc, initiallyDisabled)
                            prepareCapturedLaunch(bow)
                            val motion = nativeMotion()
                            val inherited = if (initiallyDisabled) Vector() else motion.clone()
                            val direction = expectedDirection(if (bow) Material.BOW else snowball, 1.0)
                            var changed = false
                            listen(capturedEventType(bow), EventPriority.LOW) {
                                changed = true
                                when (change) {
                                    "motion" -> {
                                        player.velocity = Vector(-0.8, 0.6, 0.4)
                                        val handle = player.javaClass.getMethod("getHandle").invoke(player)
                                        val setter =
                                            handle.javaClass.methods.firstOrNull {
                                                it.name == "setKnownMovement" && it.parameterCount == 1
                                            }
                                        if (setter != null) {
                                            val vector =
                                                setter.parameterTypes
                                                    .single()
                                                    .getConstructor(
                                                        Double::class.javaPrimitiveType,
                                                        Double::class.javaPrimitiveType,
                                                        Double::class.javaPrimitiveType,
                                                    ).newInstance(-0.9, 0.8, -0.7)
                                            setter.invoke(handle, vector)
                                        }
                                        (nativeMotion().distance(motion) > 0.5) shouldBe true
                                    }

                                    "ground" -> {
                                        val handle = player.javaClass.getMethod("getHandle").invoke(player)
                                        val setter =
                                            handle.javaClass.methods.first {
                                                it.name in setOf("setOnGround", "c") &&
                                                    it.parameterTypes.contentEquals(
                                                        arrayOf(Boolean::class.javaPrimitiveType),
                                                    )
                                            }
                                        setter.invoke(handle, true)
                                        player.isOnGround shouldBe true
                                    }

                                    else -> {
                                        field.setBoolean(misc, !initiallyDisabled)
                                    }
                                }
                            }
                            val projectile = finishCapturedLaunch(bow)
                            changed shouldBe true
                            if (module == relative) {
                                assertVector(projectile.velocity, checkNotNull(incoming).clone().subtract(inherited))
                            } else {
                                val speed = if (bow) 3.0 * force else 1.5
                                // At maximum bow speed, 0.15 exceeds six Gaussian standard deviations per axis.
                                assertVector(projectile.velocity, direction.multiply(speed).add(inherited), 0.15)
                            }
                        } finally {
                            field.setBoolean(misc, original)
                        }
                    }
                }
            }

            test("captured native $bow geometry survives later teleport and stance change").config(
                enabledOrReasonIf = {
                    io.kotest.core.test
                        .Enabled(modern, "Native item attribution is absent")
                },
            ) {
                configure(all)
                prepareCapturedLaunch(bow)
                val start = player.location
                val yaw = Math.toRadians(start.yaw.toDouble())
                val origin = start.toVector().add(Vector(-cos(yaw) * 0.16, 1.52, -sin(yaw) * 0.16))
                val direction = expectedDirection(if (bow) Material.BOW else snowball, 1.0)
                listen(capturedEventType(bow), EventPriority.LOW) {
                    player.teleport(
                        start.clone().add(3.0, 2.0, -4.0).also {
                            it.yaw = -90f
                            it.pitch = -30f
                        },
                    )
                    player.isSneaking = true
                }
                val projectile = finishCapturedLaunch(bow)
                assertVector(projectile.location.toVector(), origin, 1e-6)
                assertVector(projectile.velocity, direction.multiply(if (bow) 3.0 * force else 1.5), 0.15)
            }

            test("captured native $bow reload clears pending context and permits the next launch") {
                configure(listOf(relative))
                var reload = true
                listen(capturedEventType(bow), EventPriority.LOW) {
                    if (reload) {
                        reload = false
                        Config.reload()
                    }
                }
                prepareCapturedLaunch(bow)
                val first = finishCapturedLaunch(bow)
                assertVector(first.velocity, checkNotNull(incoming))
                remove(first)
                // Give the second native movement packet a fresh teleport acknowledgement.
                player.teleport(Location(player.world, 8.0, 180.0, 8.0, 37f, 24f))
                player.velocity = Vector()
                prepareCapturedLaunch(bow)
                val inherited = if (modern) nativeMotion() else Vector()
                val second = finishCapturedLaunch(bow)
                assertVector(second.velocity, checkNotNull(incoming).clone().subtract(inherited))
            }
        }

        test("captured native bow redispatch leaves nested motion intact and corrects the outer launch once") {
            configure(listOf(relative))
            prepareCapturedLaunch(true)
            val inherited = if (modern) nativeMotion() else Vector()
            var nested = false
            var redispatched = false
            var laterListenerRan = false
            var originalBowMotion: Vector? = null
            val foreign = Vector(0.2, 0.1, -0.2)
            listen(EntityShootBowEvent::class.java, EventPriority.LOW) { event ->
                if (!nested) {
                    val projectile = (event as EntityShootBowEvent).projectile
                    val before = projectile.velocity.clone()
                    originalBowMotion = before.clone()
                    nested = true
                    Bukkit.getPluginManager().callEvent(event)
                    nested = false
                    redispatched = true
                    assertVector(projectile.velocity, before)
                }
            }
            listen(EntityShootBowEvent::class.java, EventPriority.NORMAL) { event ->
                if (!nested) {
                    val projectile = (event as EntityShootBowEvent).projectile
                    projectile.velocity = projectile.velocity.add(foreign)
                    laterListenerRan = true
                }
            }
            val projectile = finishCapturedLaunch(true)
            redispatched shouldBe true
            laterListenerRan shouldBe true
            assertVector(projectile.velocity, checkNotNull(originalBowMotion).clone().add(foreign).subtract(inherited))
        }

        test("native crossbow arrow remains outside the bow-only correction").config(
            enabledOrReasonIf = {
                io.kotest.core.test.Enabled(
                    Material.matchMaterial("CROSSBOW") != null,
                    "Crossbows are absent from this server",
                )
            },
        ) {
            val material = Material.matchMaterial("CROSSBOW")
            if (material != null) {
                useProjectileItem(player, material) { item ->
                    val meta = item.itemMeta!!
                    meta.javaClass
                        .getMethod(
                            "addChargedProjectile",
                            ItemStack::class.java,
                        ).invoke(meta, ItemStack(Material.ARROW))
                    item.itemMeta = meta
                }
                val projectile = nativeTestProjectiles(player).single()
                entities.add(projectile)
                assertVector(projectile.velocity, checkNotNull(incoming))
                assertVector(projectile.location.toVector(), checkNotNull(incomingPosition))
            } else {
                Material.matchMaterial("CROSSBOW") shouldBe null
            }
        }

        test("native charged trident remains outside generic launch corrections").config(
            enabledOrReasonIf = {
                io.kotest.core.test.Enabled(
                    Material.matchMaterial("TRIDENT") != null,
                    "Tridents are absent from this server",
                )
            },
        ) {
            val material = Material.matchMaterial("TRIDENT")
            if (material != null) {
                listen(ProjectileLaunchEvent::class.java, EventPriority.LOWEST) { event ->
                    val projectile = (event as ProjectileLaunchEvent).entity
                    if (projectile.shooter == player && projectile.type.name == "TRIDENT") {
                        incoming = projectile.velocity.clone()
                        incomingPosition = projectile.location.toVector()
                    }
                }
                useProjectileItem(player, material)
                delay(1100)
                releaseProjectileItem(player)
                val projectile = nativeTestProjectiles(player).single()
                entities.add(projectile)
                assertVector(projectile.velocity, checkNotNull(incoming))
                assertVector(projectile.location.toVector(), checkNotNull(incomingPosition))
            } else {
                Material.matchMaterial("TRIDENT") shouldBe null
            }
        }

        test("API mob launch and native dispenser retain their own origins and velocities") {
            val witch =
                player.world.spawn(
                    player.location.clone().add(4.0, 0.0, 0.0),
                    org.bukkit.entity.Witch::class.java,
                )
            var captured: Projectile? = null
            var velocity: Vector? = null
            var origin: Vector? = null
            listen(ProjectileLaunchEvent::class.java, EventPriority.LOWEST) { event ->
                val entity = (event as ProjectileLaunchEvent).entity
                captured = entity
                velocity = entity.velocity.clone()
                origin = entity.location.toVector()
            }
            val block = player.world.getBlockAt(12, 180, 12)
            val previous = block.state
            try {
                val fromMob = witch.launchProjectile(Snowball::class.java, Vector(0.2, 0.3, 0.4))
                entities.add(fromMob)
                assertVector(fromMob.velocity, checkNotNull(velocity))
                assertVector(fromMob.location.toVector(), checkNotNull(origin))
                block.type = Material.DISPENSER
                val dispenser = block.state as org.bukkit.block.Dispenser
                dispenser.inventory.addItem(ItemStack(snowball, 1))
                captured = null
                dispenser.dispense() shouldBe true
                val fromBlock = checkNotNull(captured)
                entities.add(fromBlock)
                assertVector(fromBlock.velocity, checkNotNull(velocity))
                assertVector(fromBlock.location.toVector(), checkNotNull(origin))
            } finally {
                witch.remove()
                previous.update(true, false)
            }
        }

        test("native launch capability is recorded with precise legacy limits") {
            File(plugin.dataFolder, "projectile-launch-capabilities.txt").writeText(
                "${Bukkit.getBukkitVersion()}: native throwable attribution=$modern; " +
                    if (modern) {
                        "Paper native main-hand launch and read-only movement bridges required.\n"
                    } else {
                        "No native throwable identity hook; generic corrections preserve native launches. " +
                            "Bow attribution also requires a supported native stack.\n"
                    },
            )
            ModuleLoader.getConfigurableModuleNames().containsAll(all) shouldBe true
        }
    })
