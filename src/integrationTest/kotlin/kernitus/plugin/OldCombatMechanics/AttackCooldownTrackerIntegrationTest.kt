/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics

import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.doubles.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.netty.channel.embedded.EmbeddedChannel
import kernitus.plugin.OldCombatMechanics.utilities.damage.AttackCooldownTracker
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector
import kotlinx.coroutines.delay
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.Callable

@OptIn(ExperimentalKotest::class)
class AttackCooldownTrackerIntegrationTest :
    FunSpec({
        val testPlugin = JavaPlugin.getPlugin(OCMTestMain::class.java)

        fun <T> runSync(action: () -> T): T =
            if (Bukkit.isPrimaryThread()) {
                action()
            } else {
                Bukkit
                    .getScheduler()
                    .callSyncMethod(
                        testPlugin,
                        Callable {
                            action()
                        },
                    ).get()
            }

        extensions(MainThreadDispatcherExtension(testPlugin))

        test("attack cooldown tracking is only active where required") {
            val isModern = Reflector.versionIsNewerOrEqualTo(1, 16, 0)
            lateinit var fake: FakePlayer

            val uuid =
                runSync {
                    val world = Bukkit.getWorld("world") ?: error("world not loaded")
                    val location = Location(world, 0.0, 120.0, 0.0, 0f, 0f)
                    fake = FakePlayer(testPlugin)
                    fake.spawn(location)
                    fake.uuid
                }

            try {
                // Let at least one tick elapse so any scheduled tracker has a chance to populate.
                delay(2 * 50L)

                val last = runSync { AttackCooldownTracker.getLastCooldown(uuid) }
                if (isModern) {
                    last shouldBe null
                } else {
                    val value = (last ?: error("Expected a cached cooldown value on legacy servers")).toDouble()
                    value.shouldBeGreaterThanOrEqual(0.0)
                    value.shouldBeLessThanOrEqual(1.0)
                }
            } finally {
                // Ensure we remove the fake player regardless of assertions.
                runSync {
                    val legacy = Reflector.getField(fake.javaClass, "legacyImpl9").get(fake) as? LegacyFakePlayer9
                    val network = legacy?.let { Reflector.getField(it.javaClass, "networkManager").get(it) }
                    val connections =
                        legacy?.let { Reflector.getField(it.javaClass, "connectedChannels").get(it) as Collection<*> }
                    val channel =
                        legacy?.let { Reflector.getField(it.javaClass, "channel").get(it) as EmbeddedChannel }
                    fake.removePlayer()
                    Bukkit.getPlayer(uuid) shouldBe null
                    legacy?.let {
                        connections!!.contains(network) shouldBe false
                        channel!!.isOpen shouldBe false
                        it.entityPlayer shouldBe null
                        it.bukkitPlayer shouldBe null
                        Reflector.getField(it.javaClass, "networkManager").get(it) shouldBe null
                        Reflector.getField(it.javaClass, "connectedChannels").get(it) shouldBe null
                        Reflector.getField(it.javaClass, "channel").get(it) shouldBe null
                    }
                }
                delay(2 * 50L)

                // Legacy servers: tracker should remove on quit.
                // Modern servers: tracker should remain inactive and return null.
                val afterQuit = runSync { AttackCooldownTracker.getLastCooldown(uuid) }
                afterQuit shouldBe null
            }
        }
    })
