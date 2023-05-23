/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics.utilities.reflection;

import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Utilities to help with keeping compatibility across multiple versions of the game.
 */
public class VersionCompatUtils {
    private static Method cooldownMethod;
    private static final Map<Class<?>, Method> absorptionAmountMethodCache = new WeakHashMap<>();

    /**
     * Returns a Craft object from the given Spigot object, e.g. CraftPlayer from Player.
     * Useful for accessing properties not available through Spigot's API.
     *
     * @param spigotObject The spigot object to get the handle of, e.g. Player
     * @return The Craft object
     */
    private static Object getCraftHandle(Object spigotObject) {
        return Reflector.invokeMethod(Reflector.getMethod(spigotObject.getClass(), "getHandle"), spigotObject);
    }

    public static float getAttackCooldown(HumanEntity he) {
        final Object craftHumanEntity = getCraftHandle(he);
        // public float x(float a), grab by return and param type, cause name changes in each version
        if (cooldownMethod == null) // cache this to not search for it every single time
            cooldownMethod = Reflector.getMethod(getCraftHandle(he).getClass(), float.class, "float");
        return Reflector.invokeMethod(cooldownMethod, craftHumanEntity, 0.5F);
    }

    public static float getAbsorptionAmount(LivingEntity livingEntity) {
        final Object craftLivingEntity = getCraftHandle(livingEntity);
        final Class<?> leClass = craftLivingEntity.getClass();
        final Method absorptionAmountMethod;
        // Cache method for each subclass of LivingEntity to not search for it every single time
        // Cannot cache for LE itself because method is obtained from each subclass
        if(!absorptionAmountMethodCache.containsKey(leClass)){
            absorptionAmountMethod = Reflector.getMethod(craftLivingEntity.getClass(), "getAbsorptionHearts");
            absorptionAmountMethodCache.put(leClass, absorptionAmountMethod);
        } else {
            absorptionAmountMethod = absorptionAmountMethodCache.get(leClass);
        }

        // Give useful debugging information in case the method cannot be applied
        if (!absorptionAmountMethod.getDeclaringClass().isAssignableFrom(craftLivingEntity.getClass())) {
            throw new IllegalArgumentException(
                    "Cannot call method '" + absorptionAmountMethod + "' of class '" + absorptionAmountMethod.getDeclaringClass().getName()
                            + "' using object '" + craftLivingEntity + "' of class '" + craftLivingEntity.getClass().getName() + "' because"
                            + " object '" + craftLivingEntity + "' is not an instance of '" + absorptionAmountMethod.getDeclaringClass().getName() + "'");
        }

        return Reflector.invokeMethod(absorptionAmountMethod, craftLivingEntity);
    }
}
