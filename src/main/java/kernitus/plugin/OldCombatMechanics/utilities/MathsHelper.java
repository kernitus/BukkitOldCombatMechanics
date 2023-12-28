/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities;

/**
 * For all the maths utilities that I needed which (for some reason) aren't in the Math class.
 */
public class MathsHelper {

    /**
     * Clamps a value between a minimum and a maximum.
     *
     * @param value The value to clamp.
     * @param min   The minimum value to clamp to.
     * @param max   The maximum value to clamp to.
     * @return The clamped value.
     */
    public static double clamp(double value, double min, double max) {
        return Math.max(Math.min(value, max), min);
    }

}
