/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

@file:Suppress("ktlint:standard:package-name")

package kernitus.plugin.OldCombatMechanics

import com.google.common.base.Function
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import kernitus.plugin.OldCombatMechanics.module.ModuleOldArmourStrength
import kernitus.plugin.OldCombatMechanics.utilities.Config
import kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage.getPlayerData
import kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage.setPlayerData
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.util.EnumMap
import java.util.Locale
import java.util.concurrent.Callable

@OptIn(ExperimentalKotest::class)
class OldArmourStrengthModesetIntegrationTest :
    FunSpec({
        val testPlugin = JavaPlugin.getPlugin(OCMTestMain::class.java)
        val ocm = JavaPlugin.getPlugin(OCMMain::class.java)
        val module =
            ModuleLoader
                .getModules()
                .filterIsInstance<ModuleOldArmourStrength>()
                .firstOrNull() ?: error("ModuleOldArmourStrength not registered")

        lateinit var player: Player
        lateinit var fakePlayer: FakePlayer

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
                        }
                    ).get()
            }
        }

        fun snapshotSection(path: String): Any? {
            val section = ocm.config.getConfigurationSection(path)
            return section?.getValues(false) ?: ocm.config.get(path)
        }

        fun restoreSection(
            path: String,
            value: Any?
        ) {
            ocm.config.set(path, null)
            when (value) {
                null -> {
                    Unit
                }

                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    ocm.config.createSection(path, value as Map<String, Any?>)
                }

                else -> {
                    ocm.config.set(path, value)
                }
            }
        }

        fun configurableModules(): List<String> {
            val internalModules =
                setOf(
                    "modeset-listener",
                    "attack-cooldown-tracker",
                    "entity-damage-listener"
                )
            val optionalModules =
                setOf(
                    "disable-attack-sounds",
                    "disable-sword-sweep-particles"
                )

            return (
                ModuleLoader
                    .getModules()
                    .map { it.configName.lowercase(Locale.ROOT) }
                    .filterNot { internalModules.contains(it) } + optionalModules
            ).distinct().sorted()
        }

        fun reloadConfigAndModules() {
            ocm.saveConfig()
            Config.reload()
            ModuleLoader.toggleModules()
            module.reload()
        }

        fun applyIssue861Config() {
            val oldArmourStrength = "old-armour-strength"
            ocm.config.set("always_enabled_modules", emptyList<String>())
            ocm.config.set("disabled_modules", configurableModules().filterNot { it == oldArmourStrength })
            ocm.config.set("modesets", null)
            ocm.config.createSection(
                "modesets",
                mapOf(
                    "old" to listOf(oldArmourStrength),
                    "new" to emptyList<String>()
                )
            )
            ocm.config.set("worlds", null)
            ocm.config.createSection("worlds", mapOf("world" to listOf("new")))
            reloadConfigAndModules()
        }

        suspend fun withIssue861Config(block: suspend () -> Unit) {
            val originalAlways = ocm.config.get("always_enabled_modules")
            val originalDisabled = ocm.config.get("disabled_modules")
            val originalModesets = snapshotSection("modesets")
            val originalWorlds = snapshotSection("worlds")

            try {
                applyIssue861Config()
                block()
            } finally {
                runSync {
                    ocm.config.set("always_enabled_modules", originalAlways)
                    ocm.config.set("disabled_modules", originalDisabled)
                    restoreSection("modesets", originalModesets)
                    restoreSection("worlds", originalWorlds)
                    reloadConfigAndModules()
                }
            }
        }

        fun setModeset(modeset: String) {
            val playerData = getPlayerData(player.uniqueId)
            playerData.setModesetForWorld(player.world.uid, modeset)
            setPlayerData(player.uniqueId, playerData)
        }

        @Suppress("DEPRECATION")
        fun createExplosionDamageEvent(): EntityDamageEvent {
            val modifiers =
                EnumMap<EntityDamageEvent.DamageModifier, Double>(
                    EntityDamageEvent.DamageModifier::class.java
                )
            modifiers[EntityDamageEvent.DamageModifier.BASE] = 20.0
            modifiers[EntityDamageEvent.DamageModifier.ARMOR] = -8.0
            modifiers[EntityDamageEvent.DamageModifier.MAGIC] = -2.0

            val identity =
                object : Function<Double, Double> {
                    override fun apply(input: Double): Double = input
                }
            val modifierFunctions =
                EnumMap<EntityDamageEvent.DamageModifier, Function<in Double, Double>>(
                    EntityDamageEvent.DamageModifier::class.java
                )
            modifiers.keys.forEach { modifierFunctions[it] = identity }

            return EntityDamageEvent(
                player,
                EntityDamageEvent.DamageCause.BLOCK_EXPLOSION,
                modifiers,
                modifierFunctions
            )
        }

        beforeSpec {
            runSync {
                val world = checkNotNull(Bukkit.getServer().getWorld("world"))
                fakePlayer = FakePlayer(testPlugin)
                fakePlayer.spawn(Location(world, 0.0, 100.0, 0.0))
                player = checkNotNull(Bukkit.getPlayer(fakePlayer.uuid))
            }
        }

        afterSpec {
            runSync {
                fakePlayer.removePlayer()
            }
        }

        beforeTest {
            runSync {
                player.inventory.clear()
                player.activePotionEffects.forEach { player.removePotionEffect(it.type) }
                player.inventory.chestplate = ItemStack(Material.DIAMOND_CHESTPLATE)
            }
        }

        test("old-armour-strength leaves explosion modifiers unchanged in new modeset") {
            withIssue861Config {
                runSync {
                    setModeset("new")

                    val event = createExplosionDamageEvent()
                    val originalArmour = event.getDamage(EntityDamageEvent.DamageModifier.ARMOR)
                    val originalMagic = event.getDamage(EntityDamageEvent.DamageModifier.MAGIC)
                    val originalFinalDamage = event.finalDamage

                    Bukkit.getPluginManager().callEvent(event)

                    event.getDamage(EntityDamageEvent.DamageModifier.ARMOR) shouldBe (originalArmour plusOrMinus 0.0001)
                    event.getDamage(EntityDamageEvent.DamageModifier.MAGIC) shouldBe (originalMagic plusOrMinus 0.0001)
                    event.finalDamage shouldBe (originalFinalDamage plusOrMinus 0.0001)
                }
            }
        }

        test("old-armour-strength applies old armour modifiers to explosion damage in old modeset") {
            withIssue861Config {
                runSync {
                    setModeset("old")

                    val event = createExplosionDamageEvent()

                    Bukkit.getPluginManager().callEvent(event)

                    event.getDamage(EntityDamageEvent.DamageModifier.ARMOR) shouldBe (-6.4 plusOrMinus 0.0001)
                    event.getDamage(EntityDamageEvent.DamageModifier.MAGIC) shouldBe (0.0 plusOrMinus 0.0001)
                    event.finalDamage shouldBe (13.6 plusOrMinus 0.0001)
                }
            }
        }
    })