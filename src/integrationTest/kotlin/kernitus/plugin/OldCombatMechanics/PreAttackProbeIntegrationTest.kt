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
import kernitus.plugin.OldCombatMechanics.utilities.Config
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.entity.Zombie
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

/** Focused Paper experiment: every attack uses the native Bukkit attack entry point exactly once. */
@OptIn(ExperimentalKotest::class)
class PreAttackProbeIntegrationTest :
    FunSpec({
        val plugin = JavaPlugin.getPlugin(OCMTestMain::class.java)
        val ocm = JavaPlugin.getPlugin(OCMMain::class.java)
        extensions(MainThreadDispatcherExtension(plugin))

        for (scenario in listOf("control", "boost", "cancelled", "invulnerable", "lower boost")) {
            test("native preattack probe $scenario") {
                val originalConfig = ocm.config.saveToString()
                val fake = FakePlayer(plugin)
                var victim: Zombie? = null
                val listener = object : Listener {}
                var damageListener: Listener? = null
                val location = Location(checkNotNull(Bukkit.getWorld("world")), 20.0, 150.0, 0.0)
                val chunk = location.chunk
                val previouslyLoaded = chunk.isForceLoaded
                try {
                    val allModules =
                        (
                            ocm.config.getStringList("always_enabled_modules") +
                                ocm.config.getStringList("disabled_modules") +
                                ocm.config
                                    .getConfigurationSection("modesets")!!
                                    .getKeys(false)
                                    .flatMap { ocm.config.getStringList("modesets.$it") }
                        ).distinct()
                    ocm.config.set("always_enabled_modules", emptyList<String>())
                    ocm.config.set("disabled_modules", allModules)
                    ocm.config.set("modesets", null)
                    ocm.config.set("modesets.old", emptyList<String>())
                    ocm.saveConfig()
                    Config.reload()
                    chunk.isForceLoaded = true
                    fake.spawn(location)
                    val attacker = checkNotNull(Bukkit.getPlayer(fake.uuid))
                    attacker.gameMode = GameMode.SURVIVAL
                    attacker.inventory.clear()
                    attacker.isSprinting = false
                    attacker.fallDistance = 0f
                    attacker.isFlying = false
                    val attackDamage = checkNotNull(attacker.getAttribute(checkNotNull(XAttribute.ATTACK_DAMAGE.get())))
                    attackDamage.modifiers.toList().forEach { attackDamage.removeModifier(it) }
                    attackDamage.baseValue = 8.0
                    checkNotNull(attacker.getAttribute(checkNotNull(XAttribute.ATTACK_SPEED.get()))).baseValue = 1000.0
                    val target = location.world!!.spawn(location.clone().add(1.0, 0.0, 0.0), Zombie::class.java)
                    victim = target
                    target.setAI(false)
                    target.setGravity(false)
                    target.equipment?.clear()
                    checkNotNull(target.getAttribute(checkNotNull(XAttribute.ARMOR.get()))).baseValue = 0.0
                    target.health = 20.0
                    target.noDamageTicks = 0
                    var preCount = 0
                    val damages = mutableListOf<Double>()
                    val rawDamages = mutableListOf<Double>()
                    var armed = false

                    @Suppress("UNCHECKED_CAST")
                    val eventClassName = "io.papermc.paper.event.player.PrePlayerAttackEntityEvent"
                    val preClass = Class.forName(eventClassName) as Class<out Event>
                    Bukkit.getPluginManager().registerEvent(preClass, listener, EventPriority.LOWEST, { _, event ->
                        val source = preClass.getMethod("getPlayer").invoke(event) as Player
                        if (source.uniqueId == attacker.uniqueId) {
                            preCount++
                            if (armed && scenario != "control") {
                                attackDamage.baseValue = if (scenario == "lower boost") 6.0 else 12.0
                                if (scenario == "cancelled") (event as Cancellable).isCancelled = true
                            }
                        }
                    }, plugin)
                    damageListener =
                        object : Listener {
                            @EventHandler(priority = EventPriority.LOWEST)
                            fun onDamage(event: EntityDamageByEntityEvent) {
                                if (event.entity.uniqueId == target.uniqueId &&
                                    event.damager.uniqueId == attacker.uniqueId
                                ) {
                                    rawDamages.add(event.damage)
                                    damages.add(event.finalDamage)
                                    // Native damage has already been captured when this callback runs.
                                    attackDamage.baseValue = 8.0
                                }
                            }
                        }
                    Bukkit.getPluginManager().registerEvents(damageListener, plugin)
                    // CraftPlayer.attack invokes native Player.attack once, unlike attackCompat's retry path.
                    attacker.attack(target)
                    target.health shouldBe (12.0 plusOrMinus 0.0001)
                    damages shouldBe listOf(8.0)
                    preCount shouldBe 1
                    check(target.noDamageTicks > target.maximumNoDamageTicks / 2)
                    armed = true
                    if (scenario == "invulnerable") target.isInvulnerable = true
                    attacker.attack(target)
                    val report =
                        "$scenario pre=$preCount raw=$rawDamages final=$damages " +
                            "health=${target.health} attribute=${attackDamage.baseValue}"
                    File(plugin.dataFolder, "preattack-probe-results.txt").appendText("$report\n")
                    plugin.logger.info(report)
                    preCount shouldBe 2
                    if (scenario == "boost") {
                        damages shouldBe listOf(8.0, 4.0)
                        target.health shouldBe (8.0 plusOrMinus 0.0001)
                        attackDamage.baseValue shouldBe 8.0
                    } else {
                        damages shouldBe listOf(8.0)
                        target.health shouldBe (12.0 plusOrMinus 0.0001)
                        attackDamage.baseValue shouldBe
                            when (scenario) {
                                "control" -> 8.0
                                "lower boost" -> 6.0
                                else -> 12.0
                            }
                    }
                    // The harness can bracket the call; a production event listener cannot use this finally.
                    attackDamage.baseValue = 8.0
                } finally {
                    HandlerList.unregisterAll(listener)
                    damageListener?.let { HandlerList.unregisterAll(it) }
                    victim?.remove()
                    fake.removePlayer()
                    chunk.isForceLoaded = previouslyLoaded
                    ocm.config.loadFromString(originalConfig)
                    ocm.saveConfig()
                    Config.reload()
                }
            }
        }
    })
