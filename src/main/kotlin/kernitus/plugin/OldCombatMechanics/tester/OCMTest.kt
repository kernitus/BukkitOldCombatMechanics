/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.tester

import org.bukkit.inventory.ItemStack

class OCMTest(
    @JvmField val weapon: ItemStack,
    @JvmField val armour: Array<ItemStack>,
    @JvmField val attackDelay: Long,
    val message: String,
    @JvmField val preparations: Runnable
)
