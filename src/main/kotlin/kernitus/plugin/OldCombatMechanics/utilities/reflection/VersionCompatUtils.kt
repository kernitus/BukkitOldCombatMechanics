/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities.reflection

import org.bukkit.entity.HumanEntity
import org.bukkit.entity.LivingEntity
import java.lang.reflect.Method
import java.util.*

/**
 * Utilities to help with keeping compatibility across multiple versions of the game.
 */
object VersionCompatUtils {
    private var cooldownMethod: Method? = null
    private val absorptionAmountMethodCache: MutableMap<Class<*>, Method?> = WeakHashMap()

    /**
     * Returns a Craft object from the given Spigot object, e.g. CraftPlayer from Player.
     * Useful for accessing properties not available through Spigot's API.
     *
     * @param spigotObject The spigot object to get the handle of, e.g. Player
     * @return The Craft object
     */
    private fun getCraftHandle(spigotObject: Any): Any? {
        return Reflector.invokeMethod(Reflector.getMethod(spigotObject.javaClass, "getHandle")!!, spigotObject)
    }

    fun getAttackCooldown(he: HumanEntity): Float {
        val craftHumanEntity = getCraftHandle(he)
        // public float x(float a), grab by return and param type, cause name changes in each version
        if (cooldownMethod == null)  // cache this to not search for it every single time
            cooldownMethod = Reflector.getMethod(
                getCraftHandle(he)!!.javaClass,
                Float::class.javaPrimitiveType, "float"
            )
        return Reflector.invokeMethod(
            cooldownMethod!!, craftHumanEntity, 0.5f
        )
    }

    fun getAbsorptionAmount(livingEntity: LivingEntity): Float {
        val craftLivingEntity = getCraftHandle(livingEntity)
        val leClass: Class<*> = craftLivingEntity!!.javaClass
        val absorptionAmountMethod: Method?
        // Cache method for each subclass of LivingEntity to not search for it every single time
        // Cannot cache for LE itself because method is obtained from each subclass
        if (!absorptionAmountMethodCache.containsKey(leClass)) {
            absorptionAmountMethod = Reflector.getMethod(
                craftLivingEntity.javaClass, "getAbsorptionHearts"
            )
            absorptionAmountMethodCache[leClass] = absorptionAmountMethod
        } else {
            absorptionAmountMethod = absorptionAmountMethodCache[leClass]
        }

        // Give useful debugging information in case the method cannot be applied
        require(absorptionAmountMethod!!.declaringClass.isAssignableFrom(craftLivingEntity.javaClass)) {
            ("Cannot call method '" + absorptionAmountMethod + "' of class '" + absorptionAmountMethod.declaringClass.name
                    + "' using object '" + craftLivingEntity + "' of class '" + craftLivingEntity.javaClass.name + "' because"
                    + " object '" + craftLivingEntity + "' is not an instance of '" + absorptionAmountMethod.declaringClass.name + "'")
        }

        return Reflector.invokeMethod(
            absorptionAmountMethod, craftLivingEntity
        )
    }
}
