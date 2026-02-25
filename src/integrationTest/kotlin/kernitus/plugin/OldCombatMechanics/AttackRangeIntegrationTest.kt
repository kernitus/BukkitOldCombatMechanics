/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics

import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kernitus.plugin.OldCombatMechanics.module.ModuleAttackRange
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.entity.Zombie
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.Callable

/**
 * Behavioural reach tests for the attack-range module.
 *
 * The module extends melee reach; we assert a hit just outside vanilla reach succeeds
 * when enabled, but the same swing misses when disabled. A control swing inside vanilla
 * reach must hit in both cases.
 */
@OptIn(ExperimentalKotest::class)
class AttackRangeIntegrationTest :
    FunSpec({
        val testPlugin = JavaPlugin.getPlugin(OCMTestMain::class.java)
        val ocm = JavaPlugin.getPlugin(OCMMain::class.java)
        val attackRangeModule = ModuleLoader.getModules().filterIsInstance<ModuleAttackRange>().firstOrNull()
        val applyMethod =
            attackRangeModule?.javaClass?.getDeclaredMethod("applyToHeld", Player::class.java)?.apply {
                isAccessible = true
            }

        fun hasAttackRange(stack: ItemStack?): Boolean =
            try {
                if (stack == null) return false
                val dctClass = Class.forName("io.papermc.paper.datacomponent.DataComponentTypes")
                val typeField = dctClass.getField("ATTACK_RANGE")
                val type = typeField.get(null)
                val baseTypeClass = Class.forName("io.papermc.paper.datacomponent.DataComponentType")
                val getter = ItemStack::class.java.getMethod("getData", baseTypeClass)
                getter.invoke(stack, type) != null
            } catch (t: Throwable) {
                false
            }

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

        suspend fun withModuleState(
            enabled: Boolean,
            maxRange: Double = 6.0,
            margin: Double = 0.1,
            block: suspend () -> Unit,
        ) {
            // Snapshot
            val disabledOrig = ocm.config.getStringList("disabled_modules").toMutableList()
            val alwaysOrig = ocm.config.getStringList("always_enabled_modules").toMutableList()
            val maxOrig = ocm.config.getDouble("attack-range.max-range")
            val marginOrig = ocm.config.getDouble("attack-range.hitbox-margin")

            fun cachedSet(field: String): MutableSet<String> {
                val f = kernitus.plugin.OldCombatMechanics.utilities.Config::class.java.getDeclaredField(field)
                f.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                return f.get(null) as MutableSet<String>
            }
            val cachedDisabledOrig = cachedSet("disabledModules").toSet()
            val cachedAlwaysOrig = cachedSet("alwaysEnabledModules").toSet()

            try {
                // Update lists
                val disabled = disabledOrig.toMutableList()
                val always = alwaysOrig.toMutableList()
                if (enabled) {
                    disabled.remove("attack-range")
                    if (!always.contains("attack-range")) always.add("attack-range")
                } else {
                    if (!disabled.contains("attack-range")) disabled.add("attack-range")
                    always.remove("attack-range")
                }
                ocm.config.set("disabled_modules", disabled)
                ocm.config.set("always_enabled_modules", always)

                cachedSet("disabledModules").apply {
                    clear()
                    addAll(disabled.map { it.lowercase() })
                }
                cachedSet("alwaysEnabledModules").apply {
                    clear()
                    addAll(always.map { it.lowercase() })
                }

                // Config tweaks
                ocm.config.set("attack-range.max-range", maxRange)
                ocm.config.set("attack-range.hitbox-margin", margin)

                attackRangeModule?.reload()
                ModuleLoader.toggleModules()

                block()
            } finally {
                // Restore
                ocm.config.set("disabled_modules", disabledOrig)
                ocm.config.set("always_enabled_modules", alwaysOrig)
                ocm.config.set("attack-range.max-range", maxOrig)
                ocm.config.set("attack-range.hitbox-margin", marginOrig)

                cachedSet("disabledModules").apply {
                    clear()
                    addAll(cachedDisabledOrig)
                }
                cachedSet("alwaysEnabledModules").apply {
                    clear()
                    addAll(cachedAlwaysOrig)
                }

                attackRangeModule?.reload()
                ModuleLoader.toggleModules()
            }
        }

        data class Actors(
            val fake: FakePlayer,
            val player: Player,
            val zombie: Zombie,
        )

        fun spawnActors(): Actors {
            lateinit var fake: FakePlayer
            lateinit var player: Player
            lateinit var zombie: Zombie
            runSync {
                val world = checkNotNull(Bukkit.getWorld("world"))
                fake = FakePlayer(testPlugin)
                fake.spawn(Location(world, 0.0, 100.0, 0.0))
                player = checkNotNull(Bukkit.getPlayer(fake.uuid))
                player.gameMode = GameMode.SURVIVAL
                player.isInvulnerable = false
                player.inventory.clear()
                player.inventory.setItemInMainHand(ItemStack(Material.IRON_SWORD))

                zombie = world.spawnEntity(Location(world, 0.0, 100.0, 0.0), EntityType.ZOMBIE) as Zombie
                zombie.health = zombie.maxHealth
            }
            return Actors(fake, player, zombie)
        }

        fun cleanup(actors: Actors) {
            runSync {
                actors.zombie.remove()
            }
            runSync { actors.fake.removePlayer() }
        }

        fun faceEntity(
            player: Player,
            target: org.bukkit.entity.Entity,
        ) {
            val eye = player.eyeLocation
            val tgt = target.location.clone().add(0.0, target.height / 2.0, 0.0)
            val dir = tgt.toVector().subtract(eye.toVector())
            val yaw = Math.toDegrees(Math.atan2(-dir.x, dir.z)).toFloat()
            val pitch = Math.toDegrees(-Math.atan2(dir.y, Math.hypot(dir.x, dir.z))).toFloat()
            val newLoc = player.location.clone()
            newLoc.yaw = yaw
            newLoc.pitch = pitch
            player.teleport(newLoc)
        }

        fun swingAt(
            zombie: Zombie,
            player: Player,
        ) {
            runSync {
                faceEntity(player, zombie)
                player.attack(zombie)
            }
        }

        test("extended reach hits just outside vanilla range when module enabled (Paper 1.21.11+)") {
            if (!Reflector.versionIsNewerOrEqualTo(1, 21, 11)) return@test
            if (attackRangeModule == null) return@test

            withModuleState(enabled = true, maxRange = 5.0, margin = 0.1) {
                val actors = spawnActors()
                val (_, player, zombie) = actors
                runSync { applyMethod?.invoke(attackRangeModule, player) }

                runSync {
                    zombie.noDamageTicks = 0
                    zombie.health = zombie.maxHealth
                    zombie.teleport(player.location.clone().add(0.0, 0.0, 5.5))
                }

                var startHealth = zombie.health
                swingAt(zombie, player)

                runSync { zombie.health shouldBeLessThan startHealth }

                cleanup(actors)
            }
        }

        test("reach boost is removed after disabling while holding the same item (Paper 1.21.11+)") {
            if (!Reflector.versionIsNewerOrEqualTo(1, 21, 11)) return@test
            if (attackRangeModule == null) return@test

            val actors = spawnActors()
            val (_, player, zombie) = actors

            withModuleState(enabled = true, maxRange = 5.5) {
                runSync { applyMethod?.invoke(attackRangeModule, player) }
                runSync { zombie.teleport(player.location.clone().add(0.0, 0.0, 5.5)) }
                var start = zombie.health
                swingAt(zombie, player) // should hit with extended reach
                runSync { zombie.health shouldBeLessThan start }

                withModuleState(enabled = false) {
                    runSync {
                        // trigger hand re-evaluation
                        Bukkit.getPluginManager().callEvent(
                            PlayerItemHeldEvent(player, player.inventory.heldItemSlot, player.inventory.heldItemSlot),
                        )
                        zombie.health = zombie.maxHealth
                        zombie.teleport(player.location.clone().add(0.0, 0.0, 5.5))
                    }
                    start = zombie.health
                    swingAt(zombie, player) // should now miss
                    runSync { zombie.health shouldBe start }
                }
            }

            cleanup(actors)
        }

        test("dropped items do not retain reach boost (Paper 1.21.11+)") {
            if (!Reflector.versionIsNewerOrEqualTo(1, 21, 11)) return@test
            if (attackRangeModule == null) return@test

            val actors = spawnActors()
            val (_, player, zombie) = actors

            withModuleState(enabled = true) {
                runSync { applyMethod?.invoke(attackRangeModule, player) }
                runSync { zombie.teleport(player.location.clone().add(0.0, 0.0, 5.5)) }
                var start = zombie.health
                swingAt(zombie, player) // extended reach hit
                runSync { zombie.health shouldBeLessThan start }

                val dropped: ItemStack =
                    run {
                        var item: ItemStack? = null
                        runSync {
                            val drop = player.world.dropItem(player.location, player.inventory.itemInMainHand.clone())
                            Bukkit.getPluginManager().callEvent(PlayerDropItemEvent(player, drop))
                            item = drop.itemStack
                            drop.remove()
                        }
                        item!!
                    }

                withModuleState(enabled = false) {
                    runSync {
                        player.inventory.setItemInMainHand(dropped)
                        Bukkit.getPluginManager().callEvent(
                            PlayerItemHeldEvent(player, player.inventory.heldItemSlot, player.inventory.heldItemSlot),
                        )
                        zombie.health = zombie.maxHealth
                        zombie.teleport(player.location.clone().add(0.0, 0.0, 5.5))
                    }
                    start = zombie.health
                    swingAt(zombie, player) // should miss because drop cleaned component
                    runSync { zombie.health shouldBe start }
                }
            }

            cleanup(actors)
        }

        test("extended reach does not apply when module disabled; close swing still hits (Paper 1.21.11+)") {
            if (!Reflector.versionIsNewerOrEqualTo(1, 21, 11)) return@test
            if (attackRangeModule == null) return@test

            withModuleState(enabled = false) {
                val actors = spawnActors()
                val (_, player, zombie) = actors

                // Far swing should miss when module disabled
                runSync {
                    zombie.noDamageTicks = 0
                    zombie.health = zombie.maxHealth
                    zombie.teleport(player.location.clone().add(0.0, 0.0, 5.5))
                }
                var healthAfterFar = zombie.health
                swingAt(zombie, player)
                runSync { healthAfterFar = zombie.health }

                // Control: move inside vanilla reach and ensure it hits
                runSync {
                    zombie.noDamageTicks = 0
                    zombie.teleport(player.location.clone().add(0.0, 0.0, 2.5))
                }
                swingAt(zombie, player)

                runSync {
                    zombie.health shouldBeLessThan healthAfterFar
                }

                cleanup(actors)
            }
        }
    })
