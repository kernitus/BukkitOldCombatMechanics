/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics

import com.github.retrooper.packetevents.event.PacketListenerAbstract
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kernitus.plugin.OldCombatMechanics.utilities.Config
import kernitus.plugin.OldCombatMechanics.utilities.PacketLifecycle
import kernitus.plugin.OldCombatMechanics.utilities.PacketSupport
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector
import kotlinx.coroutines.delay
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Cow
import org.bukkit.plugin.java.JavaPlugin

/** Backend failures are injected; damage comes from one genuine native player attack. */
@OptIn(ExperimentalKotest::class)
class PacketLifecycleIntegrationTest :
    FunSpec({
        val plugin = JavaPlugin.getPlugin(OCMTestMain::class.java)
        val ocm = JavaPlugin.getPlugin(OCMMain::class.java)
        extensions(MainThreadDispatcherExtension(plugin))
        listOf("acquire", "load", "initialise", "register", "unregister", "terminate").forEach { failingStage ->
            test("native combat survives packet $failingStage failure and reload") {
                val calls = mutableListOf<String>()
                val warnings = mutableListOf<String>()

                fun call(stage: String) {
                    calls += stage
                    if (stage == failingStage) throw NoClassDefFoundError("injected $stage")
                }
                val lifecycle =
                    PacketLifecycle(
                        object : PacketLifecycle.Backend<PacketListenerAbstract> {
                            override fun acquire() = call("acquire")

                            override fun load() = call("load")

                            override fun initialise() = call("initialise")

                            override fun register(listener: PacketListenerAbstract) = call("register")

                            override fun unregister(listener: PacketListenerAbstract) = call("unregister")

                            override fun terminate() = call("terminate")
                        },
                        { warnings.add(it) },
                    )
                val support = PacketSupport(ocm)
                val lifecycleField =
                    PacketSupport::class.java
                        .getDeclaredField("lifecycle")
                        .apply { isAccessible = true }
                lifecycleField.set(support, lifecycle)
                val supportField =
                    OCMMain::class.java
                        .getDeclaredField("packetSupport")
                        .apply { isAccessible = true }
                val original = ocm.packetSupport
                val fake = FakePlayer(plugin)
                val world = Bukkit.getWorlds().first()
                val location = Location(world, 8.5, 100.0, 8.5)
                val ground = (-2..2).flatMap { x -> (-2..2).map { z -> world.getBlockAt(8 + x, 99, 8 + z).state } }
                var victim: Cow? = null
                try {
                    supportField.set(ocm, support)
                    support.load()
                    support.initialise()
                    val listener = object : PacketListenerAbstract() {}
                    support.update("injected consumer", listener, true)
                    support.update("injected consumer", listener, false)
                    if (failingStage == "terminate") support.close()
                    repeat(3) { Config.reload() }
                    ground.forEach { it.block.type = Material.STONE }
                    fake.spawn(location)
                    val player = checkNotNull(Bukkit.getPlayer(fake.uuid))
                    player.gameMode = GameMode.SURVIVAL
                    player.inventory.clear()
                    delay(25 * 50L)
                    val target = world.spawn(location.clone().add(1.0, 0.0, 0.0), Cow::class.java)
                    victim = target
                    val health = target.health
                    try {
                        player.attack(target)
                    } catch (_: NoSuchMethodError) {
                        val handle = player.javaClass.getMethod("getHandle").invoke(player)
                        val targetHandle = target.javaClass.getMethod("getHandle").invoke(target)
                        checkNotNull(Reflector.getMethodAssignable(handle.javaClass, "attack", targetHandle.javaClass))
                            .invoke(handle, targetHandle)
                    }
                    (target.health < health) shouldBe true
                    ocm.isEnabled shouldBe true
                    calls.contains(failingStage) shouldBe true
                    warnings.isNotEmpty() shouldBe true
                } finally {
                    victim?.remove()
                    fake.removePlayer()
                    ground.forEach { it.update(true, false) }
                    support.close()
                    supportField.set(ocm, original)
                    Config.reload()
                }
                calls.count { it == "terminate" } shouldBe if (failingStage == "acquire") 0 else 1
            }
        }
    })
