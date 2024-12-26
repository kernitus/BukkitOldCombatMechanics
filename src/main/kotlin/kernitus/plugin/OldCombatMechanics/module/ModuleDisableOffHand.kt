/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module

import kernitus.plugin.OldCombatMechanics.OCMMain
import kernitus.plugin.OldCombatMechanics.utilities.ConfigUtils.loadMaterialList
import kernitus.plugin.OldCombatMechanics.utilities.Messenger.send
import org.bukkit.Material
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.inventory.ItemStack
import java.util.function.BiPredicate

/**
 * Disables usage of the off-hand.
 */
class ModuleDisableOffHand(plugin: OCMMain) : OCMModule(plugin, "disable-offhand") {
    private var materials: List<Material?>? = null
    private var deniedMessage: String? = null
    private var blockType: BlockType? = null

    init {
        reload()
    }

    override fun reload() {
        blockType = if (module()!!.getBoolean("whitelist")) BlockType.WHITELIST else BlockType.BLACKLIST
        materials = loadMaterialList(module()!!, "items")
        deniedMessage = module()!!.getString("denied-message")
    }

    private fun sendDeniedMessage(sender: CommandSender) {
        if (!deniedMessage!!.trim { it <= ' ' }.isEmpty()) send(
            sender,
            deniedMessage!!
        )
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onSwapHandItems(e: PlayerSwapHandItemsEvent) {
        val player = e.player
        if (isEnabled(player) && isItemBlocked(e.offHandItem)) {
            e.isCancelled = true
            sendDeniedMessage(player)
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onInventoryClick(e: InventoryClickEvent) {
        val player = e.whoClicked
        if (!isEnabled(player)) return
        val clickType = e.click

        try {
            if (clickType == ClickType.SWAP_OFFHAND) {
                e.result = Event.Result.DENY
                sendDeniedMessage(player)
                return
            }
        } catch (ignored: NoSuchFieldError) {
        } // For versions below 1.16


        val clickedInventory = e.clickedInventory ?: return
        val inventoryType = clickedInventory.type
        // Source inventory must be PLAYER
        if (inventoryType != InventoryType.PLAYER) return

        val view = e.view
        // If neither of the inventories is CRAFTING, player cannot be moving stuff to the offhand
        if (view.bottomInventory.type != InventoryType.CRAFTING &&
            view.topInventory.type != InventoryType.CRAFTING
        ) return

        // Prevent shift-clicking a shield into the offhand item slot
        val currentItem = e.currentItem
        if (currentItem != null && currentItem.type == Material.SHIELD && isItemBlocked(currentItem)
            && e.slot != OFFHAND_SLOT && e.isShiftClick
        ) {
            e.result = Event.Result.DENY
            sendDeniedMessage(player)
        }

        if (e.slot == OFFHAND_SLOT &&  // Let allowed items be placed into offhand slot with number keys (hotbar swap)
            ((clickType == ClickType.NUMBER_KEY && isItemBlocked(clickedInventory.getItem(e.hotbarButton)))
                    || isItemBlocked(e.cursor)) // Deny placing not allowed items into offhand slot
        ) {
            e.result = Event.Result.DENY
            sendDeniedMessage(player)
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onInventoryDrag(e: InventoryDragEvent) {
        val player = e.whoClicked
        if (!isEnabled(player) || e.inventory.type != InventoryType.CRAFTING || !e.inventorySlots.contains(OFFHAND_SLOT)) return

        if (isItemBlocked(e.oldCursor)) {
            e.result = Event.Result.DENY
            sendDeniedMessage(player)
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onWorldChange(e: PlayerChangedWorldEvent) {
        onModesetChange(e.player)
    }

    override fun onModesetChange(player: Player) {
        val inventory = player.inventory
        val offHandItem = inventory.itemInOffHand

        if (isItemBlocked(offHandItem)) {
            sendDeniedMessage(player)
            inventory.setItemInOffHand(ItemStack(Material.AIR))
            if (!inventory.addItem(offHandItem).isEmpty()) player.world.dropItemNaturally(player.location, offHandItem)
        }
    }

    private fun isItemBlocked(item: ItemStack?): Boolean {
        if (item == null || item.type == Material.AIR) {
            return false
        }

        return !blockType!!.isAllowed(materials, item.type)
    }

    private enum class BlockType(private val filter: BiPredicate<Collection<Material?>?, Material?>) {
        WHITELIST(BiPredicate { obj: Collection<Material?>?, o: Material? -> obj!!.contains(o) }),
        BLACKLIST(not { obj: Collection<Material?>?, o: Material? ->
            obj!!.contains(
                o
            )
        });

        /**
         * Checks whether the given material is allowed.
         *
         * @param list    the list to use for checking
         * @param toCheck the material to check
         * @return true if the item is allowed, based on the list and the current mode
         */
        fun isAllowed(list: Collection<Material?>?, toCheck: Material?): Boolean {
            return filter.test(list, toCheck)
        }
    }

    companion object {
        private const val OFFHAND_SLOT = 40
        private fun <T, U> not(predicate: BiPredicate<T, U>): BiPredicate<T, U> {
            return predicate.negate()
        }
    }
}