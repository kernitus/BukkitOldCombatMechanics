/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics

import com.cryptomorin.xseries.XAttribute
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import kernitus.plugin.OldCombatMechanics.module.ModuleAttackCooldown
import kernitus.plugin.OldCombatMechanics.utilities.Config
import kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage
import kotlinx.coroutines.delay
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.Callable

@OptIn(ExperimentalKotest::class)
class AttackCooldownHeldItemIntegrationTest :
    FunSpec({
        val testPlugin = JavaPlugin.getPlugin(OCMTestMain::class.java)
        val ocm = JavaPlugin.getPlugin(OCMMain::class.java)
        val module = ModuleLoader.getModules().filterIsInstance<ModuleAttackCooldown>().firstOrNull()

        extensions(MainThreadDispatcherExtension(testPlugin))

        fun <T> runSync(action: () -> T): T =
            if (Bukkit.isPrimaryThread()) {
                action()
            } else {
                Bukkit.getScheduler().callSyncMethod(testPlugin, Callable { action() }).get()
            }

        fun currentAttackSpeed(player: Player): Double {
            val attackSpeedAttribute = checkNotNull(XAttribute.ATTACK_SPEED.get()) { "Missing attack speed attribute type" }
            val attribute = player.getAttribute(attackSpeedAttribute) ?: error("Missing attack speed attribute")
            return attribute.baseValue
        }

        fun setModeset(
            player: Player,
            world: World,
            modeset: String,
        ) {
            val data = PlayerStorage.getPlayerData(player.uniqueId)
            data.setModesetForWorld(world.uid, modeset)
            PlayerStorage.setPlayerData(player.uniqueId, data)
        }

        fun fireJoin(player: Player) {
            Bukkit.getPluginManager().callEvent(PlayerJoinEvent(player, "test"))
        }

        fun switchHotbar(
            player: Player,
            from: Int,
            to: Int,
        ) {
            player.inventory.heldItemSlot = to
            Bukkit.getPluginManager().callEvent(PlayerItemHeldEvent(player, from, to))
        }

        data class SpawnedPlayer(
            val fake: FakePlayer,
            val player: Player,
        )

        fun spawnFake(world: World): SpawnedPlayer {
            lateinit var fake: FakePlayer
            lateinit var player: Player
            runSync {
                fake = FakePlayer(testPlugin)
                fake.spawn(Location(world, 0.0, 100.0, 0.0, 0f, 0f))
                player = checkNotNull(Bukkit.getPlayer(fake.uuid))
                player.inventory.clear()
                player.inventory.heldItemSlot = 0
                player.activePotionEffects.forEach { player.removePotionEffect(it.type) }
                setModeset(player, world, "old")
            }
            return SpawnedPlayer(fake, player)
        }

        fun cleanup(spawnedPlayer: SpawnedPlayer) {
            runSync { spawnedPlayer.fake.removePlayer() }
        }

        suspend fun waitForPossibleDeferredWork() {
            delay(2 * 50L)
        }

        suspend fun withAttackCooldownConfig(
            genericAttackSpeed: Double,
            heldItemAttackSpeeds: Map<String, Double>,
            block: suspend () -> Unit,
        ) {
            val disabledModules = ocm.config.getStringList("disabled_modules")
            val alwaysEnabledModules = ocm.config.getStringList("always_enabled_modules")
            val modesetsSection = ocm.config.getConfigurationSection("modesets") ?: error("Missing 'modesets' section")
            val modesetSnapshot =
                modesetsSection.getKeys(false).associateWith { key ->
                    ocm.config.getStringList("modesets.$key")
                }
            val genericSnapshot = ocm.config.get("disable-attack-cooldown.generic-attack-speed")
            val heldItemSnapshot =
                ocm.config.getConfigurationSection("disable-attack-cooldown.held-item-attack-speeds")?.getValues(false)
                    ?: emptyMap<String, Any?>()

            fun reloadAll() {
                ocm.saveConfig()
                Config.reload()
                ModuleLoader.toggleModules()
                module?.reload()
            }

            try {
                ocm.config.set("disable-attack-cooldown.generic-attack-speed", genericAttackSpeed)
                ocm.config.set("disable-attack-cooldown.held-item-attack-speeds", null)
                heldItemAttackSpeeds.forEach { (key, value) ->
                    ocm.config.set("disable-attack-cooldown.held-item-attack-speeds.$key", value)
                }

                ocm.config.set("disabled_modules", disabledModules.filterNot { it == "disable-attack-cooldown" })
                ocm.config.set("always_enabled_modules", alwaysEnabledModules.filterNot { it == "disable-attack-cooldown" })

                val oldModeset =
                    ocm.config.getStringList("modesets.old").toMutableList().apply {
                        if (!contains("disable-attack-cooldown")) add("disable-attack-cooldown")
                    }
                val newModeset =
                    ocm.config.getStringList("modesets.new").toMutableList().apply {
                        remove("disable-attack-cooldown")
                    }
                ocm.config.set("modesets.old", oldModeset)
                ocm.config.set("modesets.new", newModeset)

                reloadAll()
                block()
            } finally {
                ocm.config.set("disabled_modules", disabledModules)
                ocm.config.set("always_enabled_modules", alwaysEnabledModules)
                modesetSnapshot.forEach { (key, list) -> ocm.config.set("modesets.$key", list) }
                ocm.config.set("disable-attack-cooldown.generic-attack-speed", genericSnapshot)
                ocm.config.set("disable-attack-cooldown.held-item-attack-speeds", null)
                heldItemSnapshot.forEach { (key, value) ->
                    ocm.config.set("disable-attack-cooldown.held-item-attack-speeds.$key", value)
                }
                reloadAll()
            }
        }

        test("applies configured held-item attack speeds and falls back to the generic value on hotbar switch") {
            withAttackCooldownConfig(
                genericAttackSpeed = 12.0,
                heldItemAttackSpeeds = mapOf("IRON_SWORD" to 19.0),
            ) {
                val world = checkNotNull(Bukkit.getWorld("world"))
                val spawned = spawnFake(world)

                try {
                    runSync {
                        spawned.player.inventory.setItem(0, ItemStack(Material.IRON_SWORD))
                        spawned.player.inventory.setItem(1, ItemStack(Material.STICK))
                        spawned.player.inventory.heldItemSlot = 0
                        fireJoin(spawned.player)
                    }
                    runSync { currentAttackSpeed(spawned.player) } shouldBe (19.0 plusOrMinus 0.01)

                    runSync { switchHotbar(spawned.player, from = 0, to = 1) }
                    runSync { currentAttackSpeed(spawned.player) } shouldBe (12.0 plusOrMinus 0.01)
                } finally {
                    cleanup(spawned)
                }
            }
        }

        test("materials without an explicit held-item entry use disable-attack-cooldown.generic-attack-speed") {
            withAttackCooldownConfig(
                genericAttackSpeed = 13.0,
                heldItemAttackSpeeds = mapOf("IRON_SWORD" to 19.0),
            ) {
                val world = checkNotNull(Bukkit.getWorld("world"))
                val spawned = spawnFake(world)

                try {
                    runSync {
                        spawned.player.inventory.setItemInMainHand(ItemStack(Material.STICK))
                        fireJoin(spawned.player)
                    }
                    runSync { currentAttackSpeed(spawned.player) } shouldBe (13.0 plusOrMinus 0.01)
                } finally {
                    cleanup(spawned)
                }
            }
        }

        test("world and modeset transitions restore vanilla 4.0 when disabled and reapply the held-item target when re-enabled") {
            withAttackCooldownConfig(
                genericAttackSpeed = 12.0,
                heldItemAttackSpeeds = mapOf("IRON_SWORD" to 19.0),
            ) {
                val world = checkNotNull(Bukkit.getWorld("world"))
                val otherWorld = checkNotNull(Bukkit.getWorld("world_nether"))
                val spawned = spawnFake(world)

                try {
                    runSync {
                        spawned.player.inventory.setItemInMainHand(ItemStack(Material.IRON_SWORD))
                        setModeset(spawned.player, world, "old")
                        setModeset(spawned.player, otherWorld, "new")
                        fireJoin(spawned.player)
                    }
                    runSync { currentAttackSpeed(spawned.player) } shouldBe (19.0 plusOrMinus 0.01)

                    runSync {
                        setModeset(spawned.player, world, "new")
                        module?.onModesetChange(spawned.player)
                    }
                    runSync { currentAttackSpeed(spawned.player) } shouldBe (4.0 plusOrMinus 0.01)

                    runSync {
                        setModeset(spawned.player, world, "old")
                        module?.onModesetChange(spawned.player)
                    }
                    runSync { currentAttackSpeed(spawned.player) } shouldBe (19.0 plusOrMinus 0.01)

                    runSync {
                        spawned.player.teleport(Location(otherWorld, 0.0, 100.0, 0.0, 0f, 0f))
                        Bukkit.getPluginManager().callEvent(PlayerChangedWorldEvent(spawned.player, world))
                    }
                    runSync { currentAttackSpeed(spawned.player) } shouldBe (4.0 plusOrMinus 0.01)

                    runSync {
                        spawned.player.teleport(Location(world, 0.0, 100.0, 0.0, 0f, 0f))
                        Bukkit.getPluginManager().callEvent(PlayerChangedWorldEvent(spawned.player, otherWorld))
                    }
                    runSync { currentAttackSpeed(spawned.player) } shouldBe (19.0 plusOrMinus 0.01)
                } finally {
                    cleanup(spawned)
                }
            }
        }

        test("user-added material keys are accepted when the running server recognises the material") {
            val material = Material.matchMaterial("MACE") ?: return@test

            withAttackCooldownConfig(
                genericAttackSpeed = 12.0,
                heldItemAttackSpeeds = mapOf(material.name to 7.0),
            ) {
                val world = checkNotNull(Bukkit.getWorld("world"))
                val spawned = spawnFake(world)

                try {
                    runSync {
                        spawned.player.inventory.setItemInMainHand(ItemStack(material))
                        fireJoin(spawned.player)
                    }
                    runSync { currentAttackSpeed(spawned.player) } shouldBe (7.0 plusOrMinus 0.01)
                } finally {
                    cleanup(spawned)
                }
            }
        }

        test("main-hand attack speed follows hand swaps and uses the newly held item") {
            withAttackCooldownConfig(
                genericAttackSpeed = 12.0,
                heldItemAttackSpeeds = mapOf("IRON_SWORD" to 19.0),
            ) {
                val world = checkNotNull(Bukkit.getWorld("world"))
                val spawned = spawnFake(world)

                try {
                    runSync {
                        spawned.player.inventory.setItemInMainHand(ItemStack(Material.STICK))
                        spawned.player.inventory.setItemInOffHand(ItemStack(Material.IRON_SWORD))
                        fireJoin(spawned.player)
                    }
                    runSync { currentAttackSpeed(spawned.player) } shouldBe (12.0 plusOrMinus 0.01)

                    runSync {
                        val swap =
                            PlayerSwapHandItemsEvent(
                                spawned.player,
                                spawned.player.inventory.itemInMainHand,
                                spawned.player.inventory.itemInOffHand,
                            )
                        Bukkit.getPluginManager().callEvent(swap)
                        spawned.player.inventory.setItemInMainHand(swap.offHandItem)
                        spawned.player.inventory.setItemInOffHand(swap.mainHandItem)
                    }
                    runSync { currentAttackSpeed(spawned.player) } shouldBe (19.0 plusOrMinus 0.01)
                } finally {
                    cleanup(spawned)
                }
            }
        }

        test("cancelled hotbar changes keep attack speed tied to the actually held item") {
            withAttackCooldownConfig(
                genericAttackSpeed = 12.0,
                heldItemAttackSpeeds = mapOf("IRON_SWORD" to 19.0),
            ) {
                val world = checkNotNull(Bukkit.getWorld("world"))
                val spawned = spawnFake(world)
                val canceller =
                    object : Listener {
                        @EventHandler(priority = EventPriority.LOWEST)
                        fun onHeld(event: PlayerItemHeldEvent) {
                            if (event.player.uniqueId == spawned.player.uniqueId) {
                                event.isCancelled = true
                            }
                        }
                    }

                try {
                    runSync {
                        Bukkit.getPluginManager().registerEvents(canceller, testPlugin)
                        spawned.player.inventory.setItem(0, ItemStack(Material.IRON_SWORD))
                        spawned.player.inventory.setItem(1, ItemStack(Material.STICK))
                        spawned.player.inventory.heldItemSlot = 0
                        fireJoin(spawned.player)
                    }
                    runSync { currentAttackSpeed(spawned.player) } shouldBe (19.0 plusOrMinus 0.01)

                    runSync {
                        val event = PlayerItemHeldEvent(spawned.player, 0, 1)
                        Bukkit.getPluginManager().callEvent(event)
                    }

                    runSync { spawned.player.inventory.heldItemSlot } shouldBe 0
                    runSync { currentAttackSpeed(spawned.player) } shouldBe (19.0 plusOrMinus 0.01)
                } finally {
                    runSync { HandlerList.unregisterAll(canceller) }
                    cleanup(spawned)
                }
            }
        }

        test("cancelled hand swaps keep attack speed tied to the actual main-hand item") {
            withAttackCooldownConfig(
                genericAttackSpeed = 12.0,
                heldItemAttackSpeeds = mapOf("IRON_SWORD" to 19.0),
            ) {
                val world = checkNotNull(Bukkit.getWorld("world"))
                val spawned = spawnFake(world)
                val canceller =
                    object : Listener {
                        @EventHandler(priority = EventPriority.LOWEST)
                        fun onSwap(event: PlayerSwapHandItemsEvent) {
                            if (event.player.uniqueId == spawned.player.uniqueId) {
                                event.isCancelled = true
                            }
                        }
                    }

                try {
                    runSync {
                        Bukkit.getPluginManager().registerEvents(canceller, testPlugin)
                        spawned.player.inventory.setItemInMainHand(ItemStack(Material.IRON_SWORD))
                        spawned.player.inventory.setItemInOffHand(ItemStack(Material.STICK))
                        fireJoin(spawned.player)
                    }
                    runSync { currentAttackSpeed(spawned.player) } shouldBe (19.0 plusOrMinus 0.01)

                    runSync {
                        val event =
                            PlayerSwapHandItemsEvent(
                                spawned.player,
                                spawned.player.inventory.itemInMainHand,
                                spawned.player.inventory.itemInOffHand,
                            )
                        Bukkit.getPluginManager().callEvent(event)
                    }

                    runSync { spawned.player.inventory.itemInMainHand.type } shouldBe Material.IRON_SWORD
                    runSync { currentAttackSpeed(spawned.player) } shouldBe (19.0 plusOrMinus 0.01)
                } finally {
                    runSync { HandlerList.unregisterAll(canceller) }
                    cleanup(spawned)
                }
            }
        }

        test("later inventory changes after a hotbar event do not trigger deferred attack-speed reconciliation") {
            withAttackCooldownConfig(
                genericAttackSpeed = 12.0,
                heldItemAttackSpeeds = mapOf("IRON_SWORD" to 19.0),
            ) {
                val world = checkNotNull(Bukkit.getWorld("world"))
                val spawned = spawnFake(world)

                try {
                    runSync {
                        spawned.player.inventory.setItem(0, ItemStack(Material.IRON_SWORD))
                        spawned.player.inventory.setItem(1, ItemStack(Material.STICK))
                        spawned.player.inventory.heldItemSlot = 0
                        fireJoin(spawned.player)
                    }
                    runSync { currentAttackSpeed(spawned.player) } shouldBe (19.0 plusOrMinus 0.01)

                    runSync {
                        val event = PlayerItemHeldEvent(spawned.player, 0, 1)
                        Bukkit.getPluginManager().callEvent(event)
                        spawned.player.inventory.heldItemSlot = 1
                        spawned.player.inventory.heldItemSlot = 0
                    }

                    waitForPossibleDeferredWork()

                    runSync { spawned.player.inventory.heldItemSlot } shouldBe 0
                    runSync { spawned.player.inventory.itemInMainHand.type } shouldBe Material.IRON_SWORD
                    runSync { currentAttackSpeed(spawned.player) } shouldBe (12.0 plusOrMinus 0.01)
                } finally {
                    cleanup(spawned)
                }
            }
        }

        test("unchanged post-swap inventory keeps the swap-applied attack speed without deferred re-checking") {
            withAttackCooldownConfig(
                genericAttackSpeed = 12.0,
                heldItemAttackSpeeds = mapOf("IRON_SWORD" to 19.0),
            ) {
                val world = checkNotNull(Bukkit.getWorld("world"))
                val spawned = spawnFake(world)

                try {
                    runSync {
                        spawned.player.inventory.setItemInMainHand(ItemStack(Material.STICK))
                        spawned.player.inventory.setItemInOffHand(ItemStack(Material.IRON_SWORD))
                        fireJoin(spawned.player)
                    }
                    runSync { currentAttackSpeed(spawned.player) } shouldBe (12.0 plusOrMinus 0.01)

                    runSync {
                        val event =
                            PlayerSwapHandItemsEvent(
                                spawned.player,
                                spawned.player.inventory.itemInMainHand,
                                spawned.player.inventory.itemInOffHand,
                            )
                        Bukkit.getPluginManager().callEvent(event)
                        spawned.player.inventory.setItemInMainHand(event.offHandItem)
                        spawned.player.inventory.setItemInOffHand(event.mainHandItem)
                    }

                    waitForPossibleDeferredWork()

                    runSync { spawned.player.inventory.itemInMainHand.type } shouldBe Material.IRON_SWORD
                    runSync { currentAttackSpeed(spawned.player) } shouldBe (19.0 plusOrMinus 0.01)
                } finally {
                    cleanup(spawned)
                }
            }
        }
    })
