/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics

import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.StringSpec
import io.kotest.core.test.TestCase
import io.kotest.matchers.doubles.shouldBeExactly
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.doubles.shouldBeLessThanOrEqual
import kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage.getPlayerData
import kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage.setPlayerData
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin


@OptIn(ExperimentalKotest::class)
class InGameTesterIntegrationTest(private val plugin: JavaPlugin) : StringSpec({
    extension(MainThreadDispatcherExtension(plugin))
    concurrency = 1  // Run tests sequentially

    lateinit var attacker: Player
    lateinit var defender: Player
    lateinit var fakeAttacker: FakePlayer
    lateinit var fakeDefender: FakePlayer

    fun preparePlayers() {
        println("Preparing players")
        val world = Bukkit.getServer().getWorld("world")
        // TODO might need to specify server superflat?
        val location = Location(world, 0.0, 100.0, 0.0)

        fakeAttacker = FakePlayer(plugin)
        plugin.logger.info("fake1")
        fakeAttacker.spawn(location.add(2.0, 0.0, 0.0))
        plugin.logger.info("fake2")
        fakeDefender = FakePlayer(plugin)
        val defenderLocation = location.add(0.0, 0.0, 2.0)
        fakeDefender.spawn(defenderLocation)

        plugin.logger.info("AAAAA")
        attacker = checkNotNull(Bukkit.getPlayer(fakeAttacker.uuid))
        defender = checkNotNull(Bukkit.getPlayer(fakeDefender.uuid))
        plugin.logger.info("BBBB")

        // Turn defender to face attacker
        defenderLocation.yaw = 180f
        defenderLocation.pitch = 0f
        defender.teleport(defenderLocation)
        plugin.logger.info("CCCC")

        // modeset of attacker takes precedence
        var playerData = getPlayerData(attacker.uniqueId)
        playerData.setModesetForWorld(attacker.world.uid, "old")
        setPlayerData(attacker.uniqueId, playerData)
        plugin.logger.info("DDD")

        playerData = getPlayerData(defender.uniqueId)
        playerData.setModesetForWorld(defender.world.uid, "new")
        setPlayerData(defender.uniqueId, playerData)
        plugin.logger.info("EEE")
    }

    beforeSpec {
        plugin.logger.info("Running before all")
        Bukkit.getScheduler().runTask(plugin, Runnable {
            preparePlayers()
        })
    }

    beforeTest {
        for (player in listOfNotNull(attacker, defender)) {
            player.gameMode = GameMode.SURVIVAL
            player.maximumNoDamageTicks = 20
            player.noDamageTicks = 0 // remove spawn invulnerability
            player.isInvulnerable = false
        }
    }

    afterSpec {
        plugin.logger.info("Running after all")
        Bukkit.getScheduler().runTask(plugin, Runnable {
            fakeAttacker.removePlayer()
            fakeDefender.removePlayer()
        })
    }

    "test melee attacks" {
        println("Testing melee attack")
        //for (weaponType in materialDamages.keys) {
        val weapon = ItemStack(kernitus.plugin.OldCombatMechanics.utilities.damage.WeaponDamages.getMaterialDamages().keys.first())
        // attack delay : 1
        defender.maximumNoDamageTicks = 0
        attacker.attack(defender)
        //}

        //TODO need to assert the damage received is what we calculated

        // Wait before the next test if necessary
        //delay(50L)
        attacker.health shouldBeExactly attacker.maxHealth
    }
})
