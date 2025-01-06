/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics

import org.bukkit.inventory.ItemStack

class OCMTest(
    val weapon: ItemStack,
    val armour: Array<ItemStack>,
    val attackDelay: Long,
    val message: String,
    val preparations: Runnable
)
