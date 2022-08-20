/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.tester;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public class OCMTest {
    final Material weaponType;
    final long attackDelay;
    final String message;
    final Runnable preparations;
    final ItemStack[] armour;

    public OCMTest(Material weaponType, ItemStack[] armour, long attackDelay, String message, Runnable preparations) {
        this.weaponType = weaponType;
        this.armour = armour;
        this.attackDelay = attackDelay;
        this.message = message;
        this.preparations = preparations;
    }
}
