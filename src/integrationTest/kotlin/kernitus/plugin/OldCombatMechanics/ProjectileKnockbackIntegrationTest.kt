/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics

import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import kernitus.plugin.OldCombatMechanics.module.ModuleProjectileKnockback
import kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage.getPlayerData
import kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage.setPlayerData
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Snowball
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.Callable

@OptIn(ExperimentalKotest::class)
class ProjectileKnockbackIntegrationTest :
    FunSpec({
        val testPlugin = JavaPlugin.getPlugin(OCMTestMain::class.java)
        val module =
            ModuleLoader
                .getModules()
                .filterIsInstance<ModuleProjectileKnockback>()
                .firstOrNull() ?: error("ModuleProjectileKnockback not registered")

        extensions(MainThreadDispatcherExtension(testPlugin))

        fun runSync(action: () -> Unit) {
            if (Bukkit.isPrimaryThread()) {
                action()
            } else {
                Bukkit
                    .getScheduler()
                    .callSyncMethod(
                        testPlugin,
                        Callable {
                            action()
                            null
                        },
                    ).get()
            }
        }

        test("projectile knockback ignores retained offline player-shaped defenders") {
            runSync {
                val world = checkNotNull(Bukkit.getServer().getWorld("world"))
                val offlineFake = FakePlayer(testPlugin)
                var snowball: Snowball? = null

                try {
                    offlineFake.spawn(Location(world, 4.5, 100.0, 0.0))
                    val offlineDefender = checkNotNull(Bukkit.getPlayer(offlineFake.uuid))
                    val playerData = getPlayerData(offlineDefender.uniqueId)
                    playerData.setModesetForWorld(world.uid, "old")
                    setPlayerData(offlineDefender.uniqueId, playerData)

                    offlineFake.removePlayer()
                    offlineDefender.isOnline shouldBe false

                    snowball = world.spawn(Location(world, 0.0, 100.0, 0.0), Snowball::class.java)
                    val event =
                        EntityDamageByEntityEvent(
                            snowball,
                            offlineDefender,
                            EntityDamageEvent.DamageCause.PROJECTILE,
                            0.0,
                        )
                    val originalDamage = event.damage
                    val thrown =
                        runCatching {
                            module.onEntityHit(event)
                        }.exceptionOrNull()

                    thrown shouldBe null
                    event.damage shouldBe (originalDamage plusOrMinus 0.0001)
                } finally {
                    snowball?.remove()
                    if (Bukkit.getPlayer(offlineFake.uuid) != null) {
                        offlineFake.removePlayer()
                    }
                }
            }
        }
    })
