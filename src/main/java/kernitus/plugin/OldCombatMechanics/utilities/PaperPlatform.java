/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities;

/** Platform boundary only; individual optional APIs still need independent capability checks. */
public final class PaperPlatform {
    private PaperPlatform() {}

    public static boolean isAvailable(ClassLoader serverClassLoader) {
        try {
            // Public Paper event, discovered without running its static initialiser.
            Class.forName("com.destroystokyo.paper.event.entity.EntityAddToWorldEvent", false, serverClassLoader);
            return true;
        } catch (ClassNotFoundException | LinkageError unavailable) {
            return false;
        }
    }
}
