package kernitus.plugin.OldCombatMechanics

import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import xyz.jpenilla.reflectionremapper.ReflectionRemapper

internal object SwordNativeUseChecks {
    private val remapper =
        runCatching {
            ReflectionRemapper.forReobfMappingsInPaperJar()
        }.getOrElse { ReflectionRemapper.noop() }

    fun activeStack(player: Player): Any? {
        val handle = player.javaClass.getMethod("getHandle").invoke(player)
        val living =
            generateSequence<Class<*>>(handle.javaClass) { it.superclass }.first {
                it.simpleName in
                    listOf("LivingEntity", "EntityLiving")
            }
        val method =
            listOf(remapper.remapMethodName(living, "getUseItem"), "cJ", "cw").firstNotNullOfOrNull {
                Reflector.getMethod(handle.javaClass, it, 0)?.takeIf { method ->
                    method.returnType.simpleName == "ItemStack"
                }
            }
        return checkNotNull(method) { "Missing native active stack getter" }.invoke(handle)
    }

    fun heldStack(item: ItemStack): Any = checkNotNull(Reflector.getField(item.javaClass, "handle").get(item))

    fun stop(player: Player) {
        val handle = player.javaClass.getMethod("getHandle").invoke(player)
        val living =
            generateSequence<Class<*>>(handle.javaClass) { it.superclass }.first {
                it.simpleName in
                    listOf("LivingEntity", "EntityLiving")
            }
        val method =
            listOf(remapper.remapMethodName(living, "stopUsingItem"), "clearActiveItem").firstNotNullOfOrNull {
                Reflector.getMethod(handle.javaClass, it, 0)
            }
        checkNotNull(method) { "Missing native stop-use method" }.invoke(handle)
    }
}
