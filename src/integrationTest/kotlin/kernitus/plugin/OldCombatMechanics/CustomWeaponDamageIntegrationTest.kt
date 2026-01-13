/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics

import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeExactly
import io.kotest.matchers.shouldBe
import kernitus.plugin.OldCombatMechanics.module.ModuleOldToolDamage
import kernitus.plugin.OldCombatMechanics.utilities.damage.WeaponDamages
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.entity.Trident
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicReference

@OptIn(ExperimentalKotest::class)
class CustomWeaponDamageIntegrationTest : FunSpec({
    val testPlugin = JavaPlugin.getPlugin(OCMTestMain::class.java)
    val ocm = JavaPlugin.getPlugin(OCMMain::class.java)
    val toolDamageModule = ModuleLoader.getModules()
        .filterIsInstance<ModuleOldToolDamage>()
        .firstOrNull() ?: error("ModuleOldToolDamage not registered")

    extensions(MainThreadDispatcherExtension(testPlugin))

    fun runSync(action: () -> Unit) {
        if (Bukkit.isPrimaryThread()) action() else Bukkit.getScheduler().callSyncMethod(testPlugin, Callable {
            action()
            null
        }).get()
    }

    suspend fun TestScope.withWeaponConfig(
        tridentMelee: Double?,
        tridentThrown: Double?,
        mace: Double?,
        block: suspend TestScope.() -> Unit
    ) {
        val snapshot = ocm.config.getConfigurationSection("old-tool-damage.damages")?.getValues(false) ?: emptyMap<String, Any?>()

        fun set(path: String, value: Double?) {
            if (value == null) return
            ocm.config.set("old-tool-damage.damages.$path", value)
        }

        try {
            set("TRIDENT", tridentMelee)
            set("TRIDENT_THROWN", tridentThrown)
            set("MACE", mace)
            toolDamageModule.reload()
            WeaponDamages.initialise(ocm)
            ModuleLoader.toggleModules()
            block()
        } finally {
            // restore
            snapshot.forEach { (k, v) -> ocm.config.set("old-tool-damage.damages.$k", v) }
            toolDamageModule.reload()
            WeaponDamages.initialise(ocm)
            ModuleLoader.toggleModules()
        }
    }

    data class SpawnedPlayer(val fake: FakePlayer, val player: Player)

    fun spawnFake(location: Location): SpawnedPlayer {
        lateinit var fake: FakePlayer
        lateinit var player: Player
        runSync {
            fake = FakePlayer(testPlugin)
            fake.spawn(location)
            player = checkNotNull(Bukkit.getPlayer(fake.uuid))
            player.gameMode = GameMode.SURVIVAL
            player.isInvulnerable = false
            player.inventory.clear()
            player.activePotionEffects.forEach { player.removePotionEffect(it.type) }
            val data = kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage.getPlayerData(player.uniqueId)
            data.setModesetForWorld(player.world.uid, "old")
            kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage.setPlayerData(player.uniqueId, data)
        }
        return SpawnedPlayer(fake, player)
    }

    fun cleanup(vararg players: SpawnedPlayer) {
        runSync {
            players.forEach { p ->
                p.fake.removePlayer()
            }
        }
    }

    test("trident melee uses configured base damage") {
        val tridentMat = Material.matchMaterial("TRIDENT") ?: return@test
        withWeaponConfig(tridentMelee = 12.0, tridentThrown = null, mace = null) {
            val world = checkNotNull(Bukkit.getWorld("world"))
            val attacker = spawnFake(Location(world, 0.0, 100.0, 0.0))
            val victim = spawnFake(Location(world, 1.5, 100.0, 0.0))
            val damageCapture = AtomicReference<Double?>()
            val listener = object : Listener {
                @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
                fun onHit(event: EntityDamageByEntityEvent) {
                    if (event.damager == attacker.player && event.entity == victim.player) {
                        damageCapture.set(event.damage)
                    }
                }
            }

            runSync {
                Bukkit.getPluginManager().registerEvents(listener, testPlugin)
                attacker.player.inventory.setItemInMainHand(ItemStack(tridentMat))
                Bukkit.getPluginManager().callEvent(
                    EntityDamageByEntityEvent(attacker.player, victim.player, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 8.0)
                )
                HandlerList.unregisterAll(listener)
            }

            val dealt = damageCapture.get() ?: error("No damage recorded")
            dealt shouldBe (12.0 plusOrMinus 0.05)
            cleanup(attacker, victim)
        }
    }

    test("thrown trident uses configured damage") {
        val tridentMat = Material.matchMaterial("TRIDENT") ?: return@test
        if (!Reflector.versionIsNewerOrEqualTo(1, 13, 0)) return@test
        withWeaponConfig(tridentMelee = null, tridentThrown = 15.0, mace = null) {
            val world = checkNotNull(Bukkit.getWorld("world"))
            val victim = spawnFake(Location(world, 0.0, 100.0, 0.0))
            val tridentRef = AtomicReference<Trident>()
            runSync {
                tridentRef.set(
                    world.spawn(world.spawnLocation, Trident::class.java).apply {
                        this.item = ItemStack(tridentMat)
                    }
                )
            }
            val damageCapture = AtomicReference<Double?>()
            val listener = object : Listener {
                @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
                fun onHit(event: EntityDamageByEntityEvent) {
                    if (event.damager == tridentRef.get() && event.entity == victim.player) {
                        damageCapture.set(event.damage)
                    }
                }
            }
            runSync {
                Bukkit.getPluginManager().registerEvents(listener, testPlugin)
                Bukkit.getPluginManager().callEvent(
                    EntityDamageByEntityEvent(tridentRef.get(), victim.player, EntityDamageEvent.DamageCause.PROJECTILE, 8.0)
                )
                HandlerList.unregisterAll(listener)
            }

            val dealt = damageCapture.get() ?: error("No damage recorded")
            dealt shouldBeExactly 15.0
            cleanup(victim)
            runSync { tridentRef.get()?.remove() }
        }
    }

    test("mace melee uses configured base damage") {
        val maceMat = Material.matchMaterial("MACE") ?: return@test
        withWeaponConfig(tridentMelee = null, tridentThrown = null, mace = 10.0) {
            val world = checkNotNull(Bukkit.getWorld("world"))
            val attacker = spawnFake(Location(world, 0.0, 100.0, 0.0))
            val victim = spawnFake(Location(world, 1.5, 100.0, 0.0))
            val damageCapture = AtomicReference<Double?>()
            val listener = object : Listener {
                @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
                fun onHit(event: EntityDamageByEntityEvent) {
                    if (event.damager == attacker.player && event.entity == victim.player) {
                        damageCapture.set(event.damage)
                    }
                }
            }

            runSync {
                Bukkit.getPluginManager().registerEvents(listener, testPlugin)
                attacker.player.inventory.setItemInMainHand(ItemStack(maceMat))
                Bukkit.getPluginManager().callEvent(
                    EntityDamageByEntityEvent(attacker.player, victim.player, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 6.0)
                )
                HandlerList.unregisterAll(listener)
            }

            val dealt = damageCapture.get() ?: error("No damage recorded")
            dealt shouldBe (10.0 plusOrMinus 0.05)
            cleanup(attacker, victim)
        }
    }
})
