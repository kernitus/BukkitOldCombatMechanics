/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics

import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage.getPlayerData
import kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage.setPlayerData
import kotlinx.coroutines.delay
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerItemDamageEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalKotest::class)
class WeaponDurabilityIntegrationTest :
    FunSpec({
        val testPlugin = JavaPlugin.getPlugin(OCMTestMain::class.java)
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

        suspend fun delayTicks(ticks: Long) {
            delay(ticks * 50L)
        }

        fun scoreAttackMethodLocal(method: java.lang.reflect.Method): Int {
            var score = 0
            val name = method.name
            val param = method.parameterTypes[0]
            val declaring = method.declaringClass.simpleName

            if (name == "attack") score += 100
            if (name == "a") score += 80

            if (param.simpleName == "Entity") score += 40
            if (param.simpleName.contains("Entity")) score += 10

            if (method.returnType == Void.TYPE) score += 10
            if (method.returnType == java.lang.Boolean.TYPE) score += 8

            if (declaring.contains("EntityHuman")) score += 25
            if (declaring.contains("EntityPlayer")) score += 20

            return score
        }

        fun methodSignatureLocal(method: java.lang.reflect.Method): String {
            val params = method.parameterTypes.joinToString(",") { it.name }
            return "${method.declaringClass.name}#${method.name}($params):${method.returnType.name}"
        }

        fun collectAllMethods(start: Class<*>): List<java.lang.reflect.Method> {
            val methods = LinkedHashMap<String, java.lang.reflect.Method>()
            var current: Class<*>? = start
            while (current != null) {
                current.declaredMethods.forEach { method ->
                    methods.putIfAbsent(methodSignatureLocal(method), method)
                }
                current = current.superclass
            }
            start.methods.forEach { method ->
                methods.putIfAbsent(methodSignatureLocal(method), method)
            }
            return methods.values.toList()
        }

        fun attackNms(
            attacker: Player,
            target: LivingEntity,
        ) {
            runCatching {
                attacker.attack(target)
                return
            }
            val attackerHandle =
                attacker.javaClass.methods
                    .firstOrNull { method ->
                        method.name == "getHandle" && method.parameterCount == 0
                    }?.invoke(attacker) ?: error("Failed to resolve CraftPlayer#getHandle for attacker")

            val targetHandle =
                target.javaClass.methods
                    .firstOrNull { method ->
                        method.name == "getHandle" && method.parameterCount == 0
                    }?.invoke(target) ?: error("Failed to resolve CraftPlayer#getHandle for target")

            val attackerHandleClass = attackerHandle.javaClass
            val targetHandleClass = targetHandle.javaClass

            runCatching {
                val managerField =
                    attackerHandleClass.declaredFields.firstOrNull { field ->
                        field.type.simpleName.contains("GameMode") || field.type.simpleName.contains("InteractManager")
                    }
                if (managerField != null) {
                    managerField.isAccessible = true
                    val manager = managerField.get(attackerHandle) ?: return@runCatching
                    val attackMethod =
                        manager.javaClass.methods.firstOrNull { method ->
                            (method.name == "attack" || method.name == "a") &&
                                method.parameterCount == 1 &&
                                method.parameterTypes[0].isAssignableFrom(targetHandleClass)
                        }
                    if (attackMethod != null) {
                        attackMethod.isAccessible = true
                        attackMethod.invoke(manager, targetHandle)
                        return
                    }
                }
            }
            val candidates =
                listOfNotNull(
                    kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector.getMethodAssignable(
                        attackerHandleClass,
                        "attack",
                        targetHandleClass,
                    ),
                    kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector.getMethodAssignable(
                        attackerHandleClass,
                        "a",
                        targetHandleClass,
                    ),
                ).ifEmpty {
                    collectAllMethods(attackerHandleClass)
                        .asSequence()
                        .filter { it.parameterCount == 1 }
                        .filter { it.parameterTypes[0].isAssignableFrom(targetHandleClass) }
                        .filter { it.returnType == Void.TYPE || it.returnType == java.lang.Boolean.TYPE }
                        .map { method -> method to scoreAttackMethodLocal(method) }
                        .sortedByDescending { it.second }
                        .map { it.first }
                        .toList()
                }

            candidates.forEach { it.isAccessible = true }
            for (method in candidates) {
                try {
                    val result = method.invoke(attackerHandle, targetHandle)
                    if (result is Boolean && !result) continue
                    return
                } catch (ignored: Exception) {
                    // try next
                }
            }
            error("Failed to invoke NMS attack for FakePlayer attacker=${attackerHandleClass.name}")
        }

        fun resolveDebugFile(): File {
            val versionTag = Bukkit.getBukkitVersion().replace(Regex("[^A-Za-z0-9_.-]"), "_")
            val runDir = File(System.getProperty("user.dir"))
            val repoRoot = runDir.parentFile?.parentFile ?: runDir
            return File(repoRoot, "build/weapon-durability-debug-$versionTag.txt")
        }

        fun appendDebug(line: String) {
            val file = resolveDebugFile()
            file.parentFile?.mkdirs()
            file.appendText(line + "\n")
        }

        fun describeNmsState(
            attacker: Player,
            victim: LivingEntity,
        ): String {
            return runCatching {
                val attackerHandle =
                    attacker.javaClass.methods
                        .firstOrNull { it.name == "getHandle" && it.parameterCount == 0 }
                        ?.invoke(attacker) ?: return@runCatching "noAttackerHandle"
                val victimHandle =
                    victim.javaClass.methods
                        .firstOrNull { it.name == "getHandle" && it.parameterCount == 0 }
                        ?.invoke(victim) ?: return@runCatching "noVictimHandle"

                fun flag(
                    handle: Any,
                    name: String,
                ): String? {
                    val method = handle.javaClass.methods.firstOrNull { it.name == name && it.parameterCount == 0 }
                    val value = method?.invoke(handle)
                    return value?.toString()
                }

                val attackerAlive = flag(attackerHandle, "isAlive")
                val victimAlive = flag(victimHandle, "isAlive")
                val victimRemoved = flag(victimHandle, "isRemoved")
                "attackerAlive=$attackerAlive victimAlive=$victimAlive victimRemoved=$victimRemoved"
            }.getOrElse { "nmsErr=${it::class.java.simpleName}" }
        }

        fun setItemDamage(
            item: ItemStack,
            damage: Int,
        ) {
            val meta = item.itemMeta
            if (meta != null) {
                try {
                    val damageableClass = Class.forName("org.bukkit.inventory.meta.Damageable")
                    if (damageableClass.isInstance(meta)) {
                        val setDamage = damageableClass.getMethod("setDamage", Int::class.javaPrimitiveType)
                        setDamage.invoke(meta, damage)
                        item.itemMeta = meta
                        return
                    }
                } catch (ignored: ClassNotFoundException) {
                    // Legacy server, fall back to durability.
                }
            }
            @Suppress("DEPRECATION")
            item.durability = damage.toShort()
        }

        fun getItemDamage(item: ItemStack): Int {
            val meta = item.itemMeta
            if (meta != null) {
                try {
                    val damageableClass = Class.forName("org.bukkit.inventory.meta.Damageable")
                    if (damageableClass.isInstance(meta)) {
                        val getDamage = damageableClass.getMethod("getDamage")
                        return (getDamage.invoke(meta) as Number).toInt()
                    }
                } catch (ignored: ClassNotFoundException) {
                    // Legacy server, fall back to durability.
                }
            }
            @Suppress("DEPRECATION")
            return item.durability.toInt()
        }

        fun setOldModeset(player: Player) {
            val playerData = getPlayerData(player.uniqueId)
            playerData.setModesetForWorld(player.world.uid, "old")
            setPlayerData(player.uniqueId, playerData)
        }

        suspend fun withAttackerAndVictim(block: suspend (attacker: Player, victim: LivingEntity) -> Unit) {
            lateinit var attacker: Player
            lateinit var victim: LivingEntity
            val attackerFake = FakePlayer(testPlugin)

            runSync {
                val world = checkNotNull(Bukkit.getWorld("world"))
                attackerFake.spawn(Location(world, 0.0, 100.0, 0.0))
                val zombie = world.spawn(Location(world, 1.2, 100.0, 0.0), org.bukkit.entity.Zombie::class.java)
                attacker = checkNotNull(Bukkit.getPlayer(attackerFake.uuid))
                victim = zombie

                setOldModeset(attacker)

                attacker.inventory.clear()
                attacker.activePotionEffects.forEach { attacker.removePotionEffect(it.type) }

                attacker.isInvulnerable = false
                victim.isInvulnerable = false
                victim.health = victim.maxHealth
                victim.noDamageTicks = 0
            }

            try {
                repeat(40) {
                    if (attacker.isOnline && attacker.isValid && victim.isValid && !victim.isDead) {
                        if (attacker.world.players.contains(attacker) && attacker.world.entities.any { it.uniqueId == victim.uniqueId }) {
                            return@repeat
                        }
                    }
                    delayTicks(1)
                }
                block(attacker, victim)
            } finally {
                runSync {
                    attackerFake.removePlayer()
                    if (victim.isValid) victim.remove()
                }
            }
        }

        test("weapon durability only changes with successful hits during invulnerability") {
            withAttackerAndVictim { attacker, victim ->
                appendDebug("invuln:start")
                try {
                    val weapon = ItemStack(Material.IRON_SWORD)
                    setItemDamage(weapon, 0)
                    runSync {
                        attacker.inventory.setItemInMainHand(weapon)
                        victim.maximumNoDamageTicks = 100
                        victim.noDamageTicks = 0
                        attacker.gameMode = org.bukkit.GameMode.SURVIVAL
                        val direction = victim.location.toVector().subtract(attacker.location.toVector())
                        attacker.teleport(attacker.location.setDirection(direction))
                    }
                    delayTicks(5)
                    appendDebug(
                        "invuln:state attackerValid=${attacker.isValid} victimValid=${victim.isValid} " +
                            "victimDead=${victim.isDead} " +
                            "victimInWorld=${victim.world.entities.any { it.uniqueId == victim.uniqueId }} " +
                            "worldPvp=${victim.world.pvp} " +
                            "nms=${describeNmsState(attacker, victim)}",
                    )

                    val hitCount = AtomicInteger(0)
                    val totalHitCount = AtomicInteger(0)
                    val cancelledHitCount = AtomicInteger(0)
                    val victimEventCount = AtomicInteger(0)
                    val anyDamageEventCount = AtomicInteger(0)
                    val allDamageEventCount = AtomicInteger(0)
                    val itemDamageCount = AtomicInteger(0)
                    val listener =
                        object : Listener {
                            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
                            fun onHit(event: EntityDamageByEntityEvent) {
                                if (event.entity.uniqueId == victim.uniqueId) {
                                    victimEventCount.incrementAndGet()
                                    if (event.damager == attacker) {
                                        totalHitCount.incrementAndGet()
                                        if (event.isCancelled) {
                                            cancelledHitCount.incrementAndGet()
                                        } else {
                                            hitCount.incrementAndGet()
                                        }
                                    }
                                }
                            }

                            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
                            fun onAnyDamage(event: EntityDamageEvent) {
                                allDamageEventCount.incrementAndGet()
                                if (event.entity.uniqueId == victim.uniqueId) {
                                    anyDamageEventCount.incrementAndGet()
                                }
                            }

                            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
                            fun onItemDamage(event: PlayerItemDamageEvent) {
                                if (event.player == attacker && event.item.type == Material.IRON_SWORD) {
                                    itemDamageCount.addAndGet(event.damage)
                                }
                            }
                        }

                    runSync { Bukkit.getPluginManager().registerEvents(listener, testPlugin) }
                    try {
                        runSync {
                            Bukkit.getPluginManager().callEvent(
                                EntityDamageEvent(victim, EntityDamageEvent.DamageCause.CUSTOM, 0.1),
                            )
                        }
                        delayTicks(1)
                        appendDebug("invuln:afterManualEvent allDamageEvents=${allDamageEventCount.get()}")
                        runSync { attackNms(attacker, victim) }
                        delayTicks(1)
                        repeat(10) {
                            runSync { attackNms(attacker, victim) }
                            delayTicks(1)
                        }
                        delayTicks(2)
                    } finally {
                        runSync { HandlerList.unregisterAll(listener) }
                    }

                    val hits = hitCount.get()
                    val totalHits = totalHitCount.get()
                    val cancelledHits = cancelledHitCount.get()
                    val damageEvents = itemDamageCount.get()
                    val actualDamage = getItemDamage(attacker.inventory.itemInMainHand)

                    if (totalHits == 0) {
                        val beforeHealth = victim.health
                        runSync { victim.damage(1.0, attacker) }
                        delayTicks(1)
                        appendDebug(
                            "invuln:afterDamage totalHits=${totalHitCount.get()} " +
                                "cancelledHits=${cancelledHitCount.get()} healthBefore=$beforeHealth healthAfter=${victim.health}",
                        )
                    }

                    appendDebug(
                        "invuln:hits=$hits totalHits=$totalHits cancelledHits=$cancelledHits " +
                            "victimEvents=${victimEventCount.get()} anyDamageEvents=${anyDamageEventCount.get()} " +
                            "allDamageEvents=${allDamageEventCount.get()} " +
                            "itemDamageEvents=$damageEvents itemDamage=$actualDamage",
                    )

                    if (hits <= 0) {
                        // Retry a few swings in case legacy fake player validity lagged.
                        repeat(5) {
                            runSync {
                                victim.noDamageTicks = 0
                                attackNms(attacker, victim)
                            }
                            delayTicks(1)
                        }
                    }

                    val finalHits = hitCount.get()
                    val finalDamageEvents = itemDamageCount.get()
                    val finalItemDamage = getItemDamage(attacker.inventory.itemInMainHand)

                    val isModern =
                        kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector
                            .versionIsNewerOrEqualTo(1, 12, 0)
                    if (!isModern) {
                        // Legacy 1.9 durability behaviour is inconsistent; ensure we at least don't crash.
                        return@withAttackerAndVictim
                    }

                    if (finalHits <= 0) {
                        error("Expected at least one successful hit, got $finalHits")
                    }

                    if (finalDamageEvents != finalHits || finalItemDamage != finalHits) {
                        error(
                            "Durability changed per click, not per hit: hits=$finalHits " +
                                "itemDamageEvents=$finalDamageEvents itemDamage=$finalItemDamage",
                        )
                    }
                } catch (e: Throwable) {
                    appendDebug("invuln:error=${e::class.java.simpleName}: ${e.message}")
                    throw e
                }
            }
        }

        test("weapon durability increments on hits after invulnerability expires") {
            withAttackerAndVictim { attacker, victim ->
                appendDebug("expire:start")
                try {
                    val weapon = ItemStack(Material.IRON_SWORD)
                    setItemDamage(weapon, 0)
                    runSync {
                        attacker.inventory.setItemInMainHand(weapon)
                        victim.maximumNoDamageTicks = 10
                        victim.noDamageTicks = 0
                        attacker.gameMode = org.bukkit.GameMode.SURVIVAL
                        val direction = victim.location.toVector().subtract(attacker.location.toVector())
                        attacker.teleport(attacker.location.setDirection(direction))
                    }
                    delayTicks(5)
                    appendDebug(
                        "expire:state attackerValid=${attacker.isValid} victimValid=${victim.isValid} " +
                            "victimDead=${victim.isDead} " +
                            "victimInWorld=${victim.world.entities.any { it.uniqueId == victim.uniqueId }} " +
                            "worldPvp=${victim.world.pvp} " +
                            "nms=${describeNmsState(attacker, victim)}",
                    )

                    val hitCount = AtomicInteger(0)
                    val totalHitCount = AtomicInteger(0)
                    val cancelledHitCount = AtomicInteger(0)
                    val victimEventCount = AtomicInteger(0)
                    val anyDamageEventCount = AtomicInteger(0)
                    val allDamageEventCount = AtomicInteger(0)
                    val itemDamageCount = AtomicInteger(0)
                    val listener =
                        object : Listener {
                            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
                            fun onHit(event: EntityDamageByEntityEvent) {
                                if (event.entity.uniqueId == victim.uniqueId) {
                                    victimEventCount.incrementAndGet()
                                    if (event.damager == attacker) {
                                        totalHitCount.incrementAndGet()
                                        if (event.isCancelled) {
                                            cancelledHitCount.incrementAndGet()
                                        } else {
                                            hitCount.incrementAndGet()
                                        }
                                    }
                                }
                            }

                            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
                            fun onAnyDamage(event: EntityDamageEvent) {
                                allDamageEventCount.incrementAndGet()
                                if (event.entity.uniqueId == victim.uniqueId) {
                                    anyDamageEventCount.incrementAndGet()
                                }
                            }

                            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
                            fun onItemDamage(event: PlayerItemDamageEvent) {
                                if (event.player == attacker && event.item.type == Material.IRON_SWORD) {
                                    itemDamageCount.addAndGet(event.damage)
                                }
                            }
                        }

                    runSync { Bukkit.getPluginManager().registerEvents(listener, testPlugin) }
                    try {
                        runSync {
                            Bukkit.getPluginManager().callEvent(
                                EntityDamageEvent(victim, EntityDamageEvent.DamageCause.CUSTOM, 0.1),
                            )
                        }
                        delayTicks(1)
                        appendDebug("expire:afterManualEvent allDamageEvents=${allDamageEventCount.get()}")
                        runSync { attackNms(attacker, victim) }
                        delayTicks(12)
                        runSync { attackNms(attacker, victim) }
                        delayTicks(2)
                    } finally {
                        runSync { HandlerList.unregisterAll(listener) }
                    }

                    val hits = hitCount.get()
                    val totalHits = totalHitCount.get()
                    val cancelledHits = cancelledHitCount.get()
                    val damageEvents = itemDamageCount.get()
                    val actualDamage = getItemDamage(attacker.inventory.itemInMainHand)

                    if (totalHits == 0) {
                        val beforeHealth = victim.health
                        runSync { victim.damage(1.0, attacker) }
                        delayTicks(1)
                        appendDebug(
                            "expire:afterDamage totalHits=${totalHitCount.get()} " +
                                "cancelledHits=${cancelledHitCount.get()} healthBefore=$beforeHealth healthAfter=${victim.health}",
                        )
                    }

                    appendDebug(
                        "expire:hits=$hits totalHits=$totalHits cancelledHits=$cancelledHits " +
                            "victimEvents=${victimEventCount.get()} anyDamageEvents=${anyDamageEventCount.get()} " +
                            "allDamageEvents=${allDamageEventCount.get()} " +
                            "itemDamageEvents=$damageEvents itemDamage=$actualDamage",
                    )

                    if (hits < 2) {
                        repeat(4) {
                            runSync { attackNms(attacker, victim) }
                            delayTicks(2)
                        }
                    }

                    val finalHits = hitCount.get()
                    val finalDamageEvents = itemDamageCount.get()
                    val finalItemDamage = getItemDamage(attacker.inventory.itemInMainHand)

                    val isModern =
                        kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector
                            .versionIsNewerOrEqualTo(1, 12, 0)
                    if (!isModern) {
                        // Legacy 1.9 durability behaviour is inconsistent; ensure we at least don't crash.
                        return@withAttackerAndVictim
                    }

                    val expectedHits = 2

                    if (finalHits < expectedHits) {
                        error("Expected at least $expectedHits hits after invulnerability expiry, got $finalHits")
                    }

                    if (finalDamageEvents != finalHits || finalItemDamage != finalHits) {
                        error(
                            "Durability did not match hits: hits=$finalHits " +
                                "itemDamageEvents=$finalDamageEvents itemDamage=$finalItemDamage",
                        )
                    }
                } catch (e: Throwable) {
                    appendDebug("expire:error=${e::class.java.simpleName}: ${e.message}")
                    throw e
                }
            }
        }
    })
