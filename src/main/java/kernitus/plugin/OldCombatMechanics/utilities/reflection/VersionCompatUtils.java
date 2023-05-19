/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics.utilities.reflection;

import kernitus.plugin.OldCombatMechanics.utilities.Messenger;
import org.bukkit.entity.LivingEntity;

import java.lang.reflect.Method;

/**
 * Utilities to help with keeping compatibility across multiple versions of the game.
 */
public class VersionCompatUtils {
    private static Method absorptionAmountMethod;

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

    // Added in 1.15
    public static float getAbsorptionAmount(LivingEntity livingEntity) {
        // getAbsorptionAmount is from Damageable interface
        final Object craftLivingEntity = getCraftHandle(livingEntity);
        if (absorptionAmountMethod == null) { // cache this to not search for it every single time
            absorptionAmountMethod = Reflector.getMethod(craftLivingEntity.getClass(), "getAbsorptionHearts");
            Messenger.debug("Obtained absorption amount method name: " + absorptionAmountMethod.getName());
        }
        return Reflector.invokeMethod(absorptionAmountMethod, craftLivingEntity);
    }
}
