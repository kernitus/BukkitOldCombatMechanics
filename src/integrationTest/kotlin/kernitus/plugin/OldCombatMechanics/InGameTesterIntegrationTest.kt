/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics

import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeExactly
import io.kotest.matchers.shouldBe
import kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage.getPlayerData
import kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage.setPlayerData
import kotlinx.coroutines.delay
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.Callable

@OptIn(ExperimentalKotest::class)
class InGameTesterIntegrationTest :
    StringSpec({
        val plugin = JavaPlugin.getPlugin(OCMTestMain::class.java)
        extension(MainThreadDispatcherExtension(plugin))
        lateinit var attacker: Player
        lateinit var defender: Player
        lateinit var fakeAttacker: FakePlayer
        lateinit var fakeDefender: FakePlayer

        fun <T> runSync(action: () -> T): T =
            if (Bukkit.isPrimaryThread()) {
                action()
            } else {
                Bukkit.getScheduler().callSyncMethod(plugin, Callable { action() }).get()
            }

        fun preparePlayers() {
            println("Preparing players")
            val world = Bukkit.getServer().getWorld("world")
            // TODO might need to specify server superflat?
            val location = Location(world, 0.0, 100.0, 0.0)

            fakeAttacker = FakePlayer(plugin)
            fakeAttacker.spawn(location.add(2.0, 0.0, 0.0))
            fakeDefender = FakePlayer(plugin)
            val defenderLocation = location.add(0.0, 0.0, 2.0)
            fakeDefender.spawn(defenderLocation)

            attacker = checkNotNull(Bukkit.getPlayer(fakeAttacker.uuid))
            defender = checkNotNull(Bukkit.getPlayer(fakeDefender.uuid))

            // Turn defender to face attacker
            defenderLocation.yaw = 180f
            defenderLocation.pitch = 0f
            defender.teleport(defenderLocation)

            // modeset of attacker takes precedence
            var playerData = getPlayerData(attacker.uniqueId)
            playerData.setModesetForWorld(attacker.world.uid, "old")
            setPlayerData(attacker.uniqueId, playerData)

            playerData = getPlayerData(defender.uniqueId)
            playerData.setModesetForWorld(defender.world.uid, "new")
            setPlayerData(defender.uniqueId, playerData)
        }

        beforeSpec {
            plugin.logger.info("Running before all")
            runSync { preparePlayers() }
        }

        beforeTest {
            runSync {
                for (player in listOfNotNull(attacker, defender)) {
                    player.gameMode = GameMode.SURVIVAL
                    player.maximumNoDamageTicks = 20
                    player.noDamageTicks = 0 // remove spawn invulnerability
                    player.isInvulnerable = false
                }
            }
        }

        afterSpec {
            plugin.logger.info("Running after all")
            runSync {
                fakeAttacker.removePlayer()
                fakeDefender.removePlayer()
            }
        }

        "test melee attacks" {
            println("Testing melee attack")
            val netheriteSword = runCatching { Material.valueOf("NETHERITE_SWORD") }.getOrNull()
            val weapon = ItemStack(netheriteSword ?: Material.STONE_SWORD)
            val victim =
                runSync {
                    attacker.world.spawnEntity(attacker.location.clone().add(1.5, 0.0, 0.0), EntityType.ZOMBIE) as LivingEntity
                }
            try {
                runSync {
                    attacker.inventory.setItemInMainHand(weapon)
                    attacker.updateInventory()
                    victim.maximumNoDamageTicks = 0
                    victim.noDamageTicks = 0
                }

                var damageEvents = 0
                var sawExpectedWeaponAtDamage = false
                var sawMeleeCause = false
                var sawPositiveUncancelledDamage = false
                val listener =
                    object : Listener {
                        @EventHandler(priority = EventPriority.MONITOR)
                        fun onEntityDamageByEntity(event: EntityDamageByEntityEvent) {
                            if (event.damager.uniqueId != attacker.uniqueId || event.entity.uniqueId != victim.uniqueId) {
                                return
                            }
                            damageEvents += 1
                            sawExpectedWeaponAtDamage = (attacker.inventory.itemInMainHand.type == weapon.type)
                            sawMeleeCause =
                                event.cause == EntityDamageEvent.DamageCause.ENTITY_ATTACK ||
                                event.cause == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK
                            if (!event.isCancelled && event.finalDamage > 0.0) {
                                sawPositiveUncancelledDamage = true
                            }
                        }
                    }
                runSync { Bukkit.getPluginManager().registerEvents(listener, plugin) }

                val victimStartHealth = runSync { victim.health }
                var minimumVictimHealth = victimStartHealth
                try {
                    repeat(12) {
                        val damaged =
                            runSync {
                                attackCompat(attacker, victim)
                                minimumVictimHealth = minOf(minimumVictimHealth, victim.health)
                                minimumVictimHealth < victimStartHealth && sawPositiveUncancelledDamage
                            }
                        if (damaged) {
                            return@repeat
                        }
                        delay(2 * 50L)
                    }
                } finally {
                    runSync { EntityDamageByEntityEvent.getHandlerList().unregister(listener) }
                }

                repeat(4) {
                    runSync {
                        minimumVictimHealth = minOf(minimumVictimHealth, victim.health)
                    }
                    if (minimumVictimHealth < victimStartHealth) {
                        return@repeat
                    }
                    delay(50L)
                }

                @Suppress("DEPRECATION") // Deprecated API kept for older server compatibility in tests.
                runSync { attacker.health } shouldBeExactly runSync { attacker.maxHealth }
                sawPositiveUncancelledDamage shouldBe true
                (minimumVictimHealth < victimStartHealth) shouldBe true
                (damageEvents > 0) shouldBe true
                sawExpectedWeaponAtDamage shouldBe true
                sawMeleeCause shouldBe true
            } finally {
                runSync { victim.remove() }
            }
        }
    })
