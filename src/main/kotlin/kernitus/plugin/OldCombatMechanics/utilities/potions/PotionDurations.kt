/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities.potions

/**
 * Hold information on duration of drinkable & splash version of a potion type
 */
@JvmRecord
data class PotionDurations(@JvmField val drinkable: Int, @JvmField val splash: Int)
