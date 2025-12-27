/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics

import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeExactly
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.doubles.shouldBeLessThanOrEqual
import kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage.getPlayerData
import kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage.setPlayerData
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin


@OptIn(ExperimentalKotest::class)
class InGameTesterIntegrationTest : StringSpec({
    val plugin = JavaPlugin.getPlugin(OCMTestMain::class.java)
    extension(MainThreadDispatcherExtension(plugin))
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
        val netheriteSword = runCatching { Material.valueOf("NETHERITE_SWORD") }.getOrNull()
        val weapon = ItemStack(netheriteSword ?: Material.STONE_SWORD)
        // attack delay : 1
        defender.maximumNoDamageTicks = 0
        try {
            attacker.attack(defender)
        } catch (e: NoSuchMethodError) {
            defender.damage(1.0, attacker)
        }

        //TODO need to assert the damage received is what we calculated

        // Wait before the next test if necessary
        //delay(50L)
        @Suppress("DEPRECATION") // Deprecated API kept for older server compatibility in tests.
        attacker.health shouldBeExactly attacker.maxHealth
    }
})
