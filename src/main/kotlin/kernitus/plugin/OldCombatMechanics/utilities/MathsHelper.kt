/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities

import kotlin.math.max
import kotlin.math.min

/**
 * For all the maths utilities that I needed which (for some reason) aren't in the Math class.
 */
object MathsHelper {
    /**
     * Clamps a value between a minimum and a maximum.
     *
     * @param value The value to clamp.
     * @param min   The minimum value to clamp to.
     * @param max   The maximum value to clamp to.
     * @return The clamped value.
     */
    fun clamp(value: Double, min: Double, max: Double) = max(min(value, max), min)
}
