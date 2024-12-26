/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module

import kernitus.plugin.OldCombatMechanics.OCMMain
import kernitus.plugin.OldCombatMechanics.versions.materials.MaterialRegistry
import org.bukkit.entity.HumanEntity
import org.bukkit.event.EventHandler
import org.bukkit.event.enchantment.EnchantItemEvent
import org.bukkit.event.inventory.*
import org.bukkit.inventory.EnchantingInventory
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.permissions.Permissible

/**
 * Allows enchanting without needing lapis.
 */
class ModuleNoLapisEnchantments(plugin: OCMMain) : OCMModule(plugin, "no-lapis-enchantments") {
    private val lapisLazuli = MaterialRegistry.LAPIS_LAZULI

    @EventHandler
    fun onEnchant(e: EnchantItemEvent) {
        val player = e.enchanter
        if (!isEnabled(player)) return

        if (hasNoPermission(player)) return

        val ei = e.inventory as EnchantingInventory //Not checking here because how else would event be fired?
        ei.secondary = lapis
    }

    @EventHandler
    fun onInventoryClick(e: InventoryClickEvent) {
        if (!isEnabled(e.whoClicked)) return

        if (e.inventory.type != InventoryType.ENCHANTING) return

        if (hasNoPermission(e.whoClicked)) return

        val item = e.currentItem ?: return

        // prevent taking it out
        if (lapisLazuli.isSame(item) && e.rawSlot == 1) {
            e.isCancelled = true
        } else if (e.cursor != null && lapisLazuli.isSame(e.cursor!!) && e.click == ClickType.DOUBLE_CLICK) {
            e.isCancelled = true
        }
    }

    @EventHandler
    fun onInventoryClose(e: InventoryCloseEvent) {
        if (!isEnabled(e.player)) return

        val inventory = e.inventory
        if (inventory == null || inventory.type != InventoryType.ENCHANTING) return

        // always clear it, so nothing is left over in the table
        (inventory as EnchantingInventory).secondary = null
    }

    @EventHandler
    fun onInventoryOpen(e: InventoryOpenEvent) {
        fillUpEnchantingTable(e.player, e.inventory)
    }

    private fun fillUpEnchantingTable(player: HumanEntity, inventory: Inventory?) {
        if (!isEnabled(player)) return

        if (inventory == null || inventory.type != InventoryType.ENCHANTING || hasNoPermission(player)) return
        (inventory as EnchantingInventory).secondary = lapis
    }

    private val lapis: ItemStack
        get() {
            val lapis = lapisLazuli.newInstance()
            lapis!!.amount = 64
            return lapis
        }

    private fun hasNoPermission(player: Permissible): Boolean {
        return isSettingEnabled("usePermission") && !player.hasPermission("oldcombatmechanics.nolapis")
    }
}
