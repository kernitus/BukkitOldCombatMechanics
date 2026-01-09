/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics

import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import kernitus.plugin.OldCombatMechanics.utilities.Config
import kernitus.plugin.OldCombatMechanics.utilities.damage.OCMEntityDamageByEntityEvent
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector
import kotlinx.coroutines.delay
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Vindicator
import org.bukkit.entity.Villager
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.lang.reflect.Method
import java.util.concurrent.Callable

@OptIn(ExperimentalKotest::class)
class OldToolDamageMobIntegrationTest : FunSpec({
    val testPlugin = JavaPlugin.getPlugin(OCMTestMain::class.java)
    val ocm = JavaPlugin.getPlugin(OCMMain::class.java)
    extensions(MainThreadDispatcherExtension(testPlugin))

    fun runSync(action: () -> Unit) {
        if (Bukkit.isPrimaryThread()) {
            action()
        } else {
            Bukkit.getScheduler().callSyncMethod(testPlugin, Callable {
                action()
                null
            }).get()
        }
    }

    suspend fun delayTicks(ticks: Long) {
        delay(ticks * 50L)
    }

    suspend fun TestScope.withConfig(block: suspend TestScope.() -> Unit) {
        val toolEnabled = ocm.config.getBoolean("old-tool-damage.enabled")
        val damagesSection = ocm.config.getConfigurationSection("old-tool-damage.damages")
        val damagesSnapshot = damagesSection?.getKeys(false)?.associateWith { damagesSection.get(it) } ?: emptyMap()
        val disabledModules = ocm.config.getStringList("disabled_modules")
        val modesetsSection = ocm.config.getConfigurationSection("modesets")
            ?: error("Missing 'modesets' section in config")
        val modesetSnapshot = modesetsSection.getKeys(false).associateWith { key ->
            ocm.config.getStringList("modesets.$key")
        }

        fun reloadAll() {
            ocm.saveConfig()
            Config.reload()
        }

        try {
            block()
        } finally {
            ocm.config.set("old-tool-damage.enabled", toolEnabled)
            damagesSnapshot.forEach { (key, value) ->
                ocm.config.set("old-tool-damage.damages.$key", value)
            }
            ocm.config.set("disabled_modules", disabledModules)
            modesetSnapshot.forEach { (key, list) ->
                ocm.config.set("modesets.$key", list)
            }
            reloadAll()
        }
    }

    fun mobMethodSignature(method: Method): String {
        val params = method.parameterTypes.joinToString(",") { it.name }
        return "${method.declaringClass.name}#${method.name}($params):${method.returnType.name}"
    }

    fun mobCollectAllMethods(start: Class<*>): List<Method> {
        val methods = LinkedHashMap<String, Method>()
        var current: Class<*>? = start
        while (current != null) {
            current.declaredMethods.forEach { method ->
                methods.putIfAbsent(mobMethodSignature(method), method)
            }
            current = current.superclass
        }
        start.methods.forEach { method ->
            methods.putIfAbsent(mobMethodSignature(method), method)
        }
        return methods.values.toList()
    }

    fun mobScoreAttackMethod(method: Method): Int {
        var score = 0
        if (method.name == "attack") score += 100
        if (method.name == "a") score += 80
        val param = method.parameterTypes[0]
        if (param.simpleName == "Entity") score += 40
        if (param.simpleName.contains("Entity")) score += 10
        if (method.returnType == Void.TYPE) score += 10
        if (method.returnType == java.lang.Boolean.TYPE) score += 8
        val declaring = method.declaringClass.simpleName
        if (declaring.contains("EntityInsentient")) score += 20
        if (declaring.contains("Mob")) score += 10
        return score
    }

    fun resolveDebugFile(): java.io.File {
        val versionTag = Bukkit.getBukkitVersion().replace(Regex("[^A-Za-z0-9_.-]"), "_")
        return java.io.File("/home/ligma/gitstuff/BukkitOldCombatMechanics/build/mob-tool-damage-debug-$versionTag.txt")
    }

    fun attackCompat(attacker: LivingEntity, target: Entity): Boolean {
        val handleMethod = attacker.javaClass.methods.firstOrNull { method ->
            method.name == "getHandle" && method.parameterTypes.isEmpty()
        } ?: error("Failed to resolve CraftEntity#getHandle for ${attacker.javaClass.name}")

        val attackerHandle = handleMethod.invoke(attacker)
            ?: error("CraftEntity#getHandle returned null for ${attacker.javaClass.name}")

        val targetHandle = target.javaClass.methods.firstOrNull { method ->
            method.name == "getHandle" && method.parameterTypes.isEmpty()
        }?.invoke(target) ?: error("Failed to resolve CraftEntity#getHandle for ${target.javaClass.name}")

        val candidates = listOfNotNull(
            Reflector.getMethodAssignable(attackerHandle.javaClass, "attack", targetHandle.javaClass),
            Reflector.getMethodAssignable(attackerHandle.javaClass, "a", targetHandle.javaClass)
        ).ifEmpty {
            mobCollectAllMethods(attackerHandle.javaClass)
                .asSequence()
                .filter { it.parameterCount == 1 }
                .filter { it.parameterTypes[0].isAssignableFrom(targetHandle.javaClass) }
                .filter { it.returnType == Void.TYPE || it.returnType == java.lang.Boolean.TYPE }
                .sortedByDescending { mobScoreAttackMethod(it) }
                .toList()
        }

        candidates.forEach { it.isAccessible = true }
        for (method in candidates) {
            try {
                val result = method.invoke(attackerHandle, targetHandle)
                if (result is Boolean && !result) {
                    continue
                }
                return true
            } catch (ignored: Exception) {
                // try next
            }
        }

        return false
    }


    suspend fun captureVindicatorBaseDamage(debugFile: java.io.File, label: String): Double {
        val events = mutableListOf<OCMEntityDamageByEntityEvent>()
        val listener = object : Listener {
            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
            fun onDamage(event: OCMEntityDamageByEntityEvent) {
                if (event.damager is Vindicator && event.damagee is Villager) {
                    events.add(event)
                }
            }
        }

        lateinit var victim: Villager
        lateinit var vindicator: Vindicator

        runSync {
            val world = checkNotNull(Bukkit.getWorld("world"))
            val victimLocation = Location(world, 0.0, 100.0, 0.0)
            val vindicatorLocation = Location(world, 1.1, 100.0, 0.0)

            victim = world.spawn(victimLocation, Villager::class.java)
            victim.isInvulnerable = false
            victim.health = victim.maxHealth
            victim.maximumNoDamageTicks = 0
            victim.noDamageTicks = 0

            vindicator = world.spawn(vindicatorLocation, Vindicator::class.java)
            vindicator.isSilent = true
            vindicator.equipment?.setItemInMainHand(ItemStack(Material.IRON_AXE))

            Bukkit.getPluginManager().registerEvents(listener, testPlugin)
        }

        try {
            runSync {
                val setTarget = vindicator.javaClass.methods.firstOrNull { method ->
                    method.name == "setTarget" &&
                        method.parameterCount == 1 &&
                        LivingEntity::class.java.isAssignableFrom(method.parameterTypes[0])
                }
                setTarget?.invoke(vindicator, victim)
            }

            repeat(40) { iteration ->
                runSync {
                    val setTarget = vindicator.javaClass.methods.firstOrNull { method ->
                        method.name == "setTarget" &&
                            method.parameterCount == 1 &&
                            LivingEntity::class.java.isAssignableFrom(method.parameterTypes[0])
                    }
                    setTarget?.invoke(vindicator, victim)
                }
                runSync { attackCompat(vindicator, victim) }
                if (events.isNotEmpty()) {
                    val moduleEnabled = Config.moduleEnabled("old-tool-damage", victim.world)
                    debugFile.parentFile?.mkdirs()
                    debugFile.appendText(
                        "label=$label base=${events.last().baseDamage} raw=${events.last().rawDamage} " +
                            "weapon=${events.last().weapon.type} enabled=$moduleEnabled ticks=${iteration + 1}\n"
                    )
                    return events.last().baseDamage
                }
                delayTicks(1)
            }
            debugFile.parentFile?.mkdirs()
            debugFile.appendText("label=$label no-event\n")
            error("Expected a vindicator damage event")
        } finally {
            HandlerList.unregisterAll(listener)
            runSync {
                vindicator.remove()
                victim.remove()
            }
        }
    }

    test("mob tool damage follows configured old-tool-damage values") {
        withConfig {
            val debugFile = resolveDebugFile()
            debugFile.parentFile?.mkdirs()
            debugFile.writeText("start\n")
            try {
                ocm.config.set("old-tool-damage.enabled", true)
                ocm.config.set(
                    "disabled_modules",
                    ocm.config.getStringList("disabled_modules")
                        .filterNot { it.equals("old-tool-damage", true) }
                )
                val oldModeset = ocm.config.getStringList("modesets.old")
                    .filterNot { it.equals("old-tool-damage", true) }
                    .toMutableList()
                oldModeset.add("old-tool-damage")
                ocm.config.set("modesets.old", oldModeset)
                ocm.config.set("old-tool-damage.damages.IRON_AXE", 1)
                ocm.saveConfig()
                Config.reload()

                val lowDamage = try {
                    captureVindicatorBaseDamage(debugFile, "low")
                } catch (e: Throwable) {
                    debugFile.appendText("low-error=${e::class.java.simpleName}: ${e.message}\n")
                    throw e
                }

                ocm.config.set("old-tool-damage.damages.IRON_AXE", 20)
                ocm.saveConfig()
                Config.reload()

                val highDamage = try {
                    captureVindicatorBaseDamage(debugFile, "high")
                } catch (e: Throwable) {
                    debugFile.appendText("high-error=${e::class.java.simpleName}: ${e.message}\n")
                    throw e
                }

                val delta = highDamage - lowDamage
                debugFile.appendText("delta=$delta low=$lowDamage high=$highDamage\n")
                if (delta <= 10.0) {
                    error("Mob tool damage delta too small: delta=$delta low=$lowDamage high=$highDamage")
                }
            } catch (e: Throwable) {
                debugFile.appendText("test-error=${e::class.java.simpleName}: ${e.message}\n")
                throw e
            }
        }
    }
})
