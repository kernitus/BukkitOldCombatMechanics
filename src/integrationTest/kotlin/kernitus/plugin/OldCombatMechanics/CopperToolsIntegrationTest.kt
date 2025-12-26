/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics

import com.cryptomorin.xseries.XMaterial
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.concurrency.TestExecutionMode
import kernitus.plugin.OldCombatMechanics.utilities.damage.WeaponDamages
import org.bukkit.Material
import org.bukkit.plugin.java.JavaPlugin

@OptIn(ExperimentalKotest::class)
class CopperToolsIntegrationTest : StringSpec({
    val plugin = JavaPlugin.getPlugin(OCMTestMain::class.java)
    extension(MainThreadDispatcherExtension(plugin))
    testExecutionMode = TestExecutionMode.Sequential

    val copperMaterials = listOf(
        XMaterial.COPPER_SWORD,
        XMaterial.COPPER_AXE,
        XMaterial.COPPER_PICKAXE,
        XMaterial.COPPER_SHOVEL,
        XMaterial.COPPER_HOE
    ).mapNotNull { xmat ->
        val material = runCatching { Material.valueOf(xmat.name) }.getOrNull()
        material?.let { xmat to it }
    }

    if (copperMaterials.isEmpty()) {
        "copper tools not present on this version" {
            plugin.logger.info("Copper tools not present on this version; skipping copper damage checks.")
        }
    } else {
        copperMaterials.forEach { (xmat, material) ->
            "copper tool damage is configurable for ${xmat.name}" {
                val damage = WeaponDamages.getDamage(material)
                if (damage < 0.0) {
                    throw AssertionError("Expected ${xmat.name} to be present in old-tool-damage.damages (config.yml)")
                }
            }
        }
    }

})
