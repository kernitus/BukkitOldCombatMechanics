/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics.utilities.reflection;

import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;

import java.lang.reflect.Method;

/**
 * Utilities to help with keeping compatibility across multiple versions of the game.
 */
public class VersionCompatUtils {
    private static Method cooldownMethod, absorptionAmountMethod;

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
        if (absorptionAmountMethod == null) // cache this to not search for it every single time
            absorptionAmountMethod = Reflector.getMethod(craftLivingEntity.getClass(), "getAbsorptionHearts");
        return Reflector.invokeMethod(absorptionAmountMethod, craftLivingEntity);
    }
}
