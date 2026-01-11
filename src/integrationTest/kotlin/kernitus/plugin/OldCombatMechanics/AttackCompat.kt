/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics

import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

fun attackCompat(attacker: Player, target: Entity) {
    val apiAttack = attacker.javaClass.methods.firstOrNull { method ->
        method.name == "attack" &&
            method.parameterCount == 1 &&
            Entity::class.java.isAssignableFrom(method.parameterTypes[0])
    }
    val useApiAttack = Reflector.versionIsNewerOrEqualTo(1, 12, 0)
    if (useApiAttack && apiAttack != null) {
        try {
            apiAttack.invoke(attacker, target)
            return
        } catch (ignored: Exception) {
            // Fall through to NMS-based attack.
        }
    }

    // Fall back to NMS attack on legacy servers.
    val handleMethod = attacker.javaClass.methods.firstOrNull { method ->
        method.name == "getHandle" && method.parameterCount == 0
    } ?: error("Failed to resolve CraftPlayer#getHandle for ${attacker.javaClass.name}")

    val attackerHandle = handleMethod.invoke(attacker)
        ?: error("CraftPlayer#getHandle returned null for ${attacker.javaClass.name}")

    val targetHandle = target.javaClass.methods.firstOrNull { method ->
        method.name == "getHandle" && method.parameterCount == 0
    }?.invoke(target) ?: error("Failed to resolve CraftEntity#getHandle for ${target.javaClass.name}")

    val nmsAttackMethods = resolveNmsAttackMethods(attackerHandle.javaClass, targetHandle.javaClass)
    for (method in nmsAttackMethods) {
        try {
            method.invoke(attackerHandle, targetHandle)
            return
        } catch (ignored: Exception) {
            // Try the next candidate.
        }
    }

    // Legacy fallback: try Bukkit API even if we prefer NMS (helps 1.12 fake players)
    if (!useApiAttack && apiAttack != null) {
        runCatching { apiAttack.invoke(attacker, target); return }
    }

    error(
        "Failed to invoke NMS attack for attacker=${attackerHandle.javaClass.name} " +
            "target=${targetHandle.javaClass.name} (candidates=${nmsAttackMethods.size})"
    )
}

private val attackMethodCache = ConcurrentHashMap<Class<*>, List<Method>>()

private fun resolveNmsAttackMethods(attackerHandleClass: Class<*>, targetHandleClass: Class<*>): List<Method> {
    return attackMethodCache.computeIfAbsent(attackerHandleClass) {
        buildAttackMethodCandidates(attackerHandleClass, targetHandleClass)
    }
}

private fun buildAttackMethodCandidates(attackerHandleClass: Class<*>, targetHandleClass: Class<*>): List<Method> {
    // Prefer explicit names if they exist, then fall back to signature-based heuristics.
    val explicit = listOfNotNull(
        Reflector.getMethodAssignable(attackerHandleClass, "attack", targetHandleClass),
        Reflector.getMethodAssignable(attackerHandleClass, "a", targetHandleClass),
        Reflector.getMethodAssignable(attackerHandleClass, "B", targetHandleClass) // legacy 1.12 variants
    )
    if (explicit.isNotEmpty()) {
        explicit.forEach { it.isAccessible = true }
        return explicit
    }

    val candidates = collectAllMethods(attackerHandleClass)
        .asSequence()
        .filter { it.parameterCount == 1 }
        .filter { it.parameterTypes[0].isAssignableFrom(targetHandleClass) }
        .filter { it.returnType == Void.TYPE || it.returnType == java.lang.Boolean.TYPE }
        .map { method -> method to scoreAttackMethod(method) }
        .sortedByDescending { it.second }
        .map { it.first }
        .toList()

    candidates.forEach { it.isAccessible = true }
    return candidates
}

private fun collectAllMethods(start: Class<*>): List<Method> {
    val methods = LinkedHashMap<String, Method>()
    var current: Class<*>? = start
    while (current != null) {
        current.declaredMethods.forEach { method ->
            methods.putIfAbsent(methodSignature(method), method)
        }
        current = current.superclass
    }
    start.methods.forEach { method ->
        methods.putIfAbsent(methodSignature(method), method)
    }
    return methods.values.toList()
}

private fun methodSignature(method: Method): String {
    val params = method.parameterTypes.joinToString(",") { it.name }
    return "${method.declaringClass.name}#${method.name}($params):${method.returnType.name}"
}

private fun scoreAttackMethod(method: Method): Int {
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
