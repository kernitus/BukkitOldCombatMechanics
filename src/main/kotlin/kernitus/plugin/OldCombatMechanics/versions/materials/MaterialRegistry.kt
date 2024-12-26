/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.versions.materials

import org.bukkit.Material
import org.bukkit.inventory.ItemStack

/**
 * Contains Materials that are different in &gt; 1.13 and &lt; 1.13.
 */
object MaterialRegistry {
    var LAPIS_LAZULI: VersionedMaterial = DualVersionedMaterial(
        { ItemStack(Material.matchMaterial("INK_SACK")!!, 1, 4.toShort()) },
        { ItemStack(Material.matchMaterial("LAPIS_LAZULI")!!) }
    )

    var ENCHANTED_GOLDEN_APPLE: VersionedMaterial = DualVersionedMaterial(
        { ItemStack(Material.GOLDEN_APPLE, 1, 1.toShort()) },
        { ItemStack(Material.valueOf("ENCHANTED_GOLDEN_APPLE")) }
    )
}
