/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module

import kernitus.plugin.OldCombatMechanics.OCMMain
import kernitus.plugin.OldCombatMechanics.utilities.reflection.VersionCompatUtils
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockCanBuildEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.*
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.PlayerInventory
import org.bukkit.scheduler.BukkitTask
import java.util.*

class ModuleSwordBlocking(plugin: OCMMain) : OCMModule(plugin, "sword-blocking") {
    // Not using WeakHashMaps here, for extra reliability
    private val storedItems: MutableMap<UUID, ItemStack> = HashMap()
    private val correspondingTasks: MutableMap<UUID, Collection<BukkitTask>> = HashMap()
    private var restoreDelay = 0

    // Only used <1.13, where BlockCanBuildEvent.getPlayer() is not available
    private var lastInteractedBlocks: MutableMap<Location, UUID>? = null

    companion object {
        private val SHIELD = ItemStack(Material.SHIELD)
    }

    init {
        if (!VersionCompatUtils.versionIsNewerOrEqualTo(1, 13, 0)) {
            lastInteractedBlocks = WeakHashMap()
        }
    }

    override fun reload() {
        restoreDelay = module().getInt("restoreDelay", 40)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onBlockPlace(e: BlockCanBuildEvent) {
        if (e.isBuildable) return

        // If <1.13 get player who last interacted with block
        val player = lastInteractedBlocks?.remove(e.block.location)?.let(Bukkit::getPlayer) ?: e.player

        if (player == null || !isEnabled(player)) return

        doShieldBlock(player)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onRightClick(e: PlayerInteractEvent) {
        val action = e.action
        val player = e.player

        if (!isEnabled(player)) return

        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) return
        // If they clicked on an interactive block, the 2nd event with the offhand won't fire
        // This is also the case if the main hand item was used, e.g. a bow
        // TODO right-clicking on a mob also only fires one hand
        if (action == Action.RIGHT_CLICK_BLOCK && e.hand == EquipmentSlot.HAND) return
        if (e.isBlockInHand) {
            e.clickedBlock?.location?.let { location ->
                lastInteractedBlocks?.set(location, player.uniqueId)
            }
            return  // Handle failed block place in separate listener
        }

        doShieldBlock(player)
    }

    private fun doShieldBlock(player: Player) {
        val inventory = player.inventory

        val mainHandItem = inventory.itemInMainHand
        val offHandItem = inventory.itemInOffHand

        if (!isHoldingSword(mainHandItem.type)) return

        if (module().getBoolean("use-permission") && !player.hasPermission("oldcombatmechanics.swordblock")) return

        val id = player.uniqueId

        if (!isPlayerBlocking(player)) {
            if (hasShield(inventory)) return
            debug("Storing $offHandItem", player)
            storedItems[id] = offHandItem

            inventory.setItemInOffHand(SHIELD)
            // Force an inventory update to avoid ghost items
            player.updateInventory()
        }
        scheduleRestore(player)
    }

    @EventHandler
    fun onHotBarChange(e: PlayerItemHeldEvent) = restore(e.player, true)

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onWorldChange(e: PlayerChangedWorldEvent) = restore(e.player, true)

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerLogout(e: PlayerQuitEvent) = restore(e.player, true)

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerDeath(e: PlayerDeathEvent) {
        val p = e.entity
        val id = p.uniqueId
        if (!areItemsStored(id)) return

        //TODO what if they legitimately had a shield?
        e.drops.replaceAll { item: ItemStack -> if (item.type == Material.SHIELD) storedItems.remove(id) else item }

        // Handle keepInventory = true
        restore(p, true)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerSwapHandItems(e: PlayerSwapHandItemsEvent) {
        if (areItemsStored(e.player.uniqueId)) e.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onInventoryClick(e: InventoryClickEvent) {
        if (e.whoClicked is Player) {
            val player = e.whoClicked as Player

            if (areItemsStored(player.uniqueId)) {
                val cursor = e.cursor
                val current = e.currentItem
                if (cursor != null && cursor.type == Material.SHIELD || current != null && current.type == Material.SHIELD) {
                    e.isCancelled = true
                    restore(player, true)
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onItemDrop(e: PlayerDropItemEvent) {
        val itemDrop = e.itemDrop
        val p = e.player

        if (areItemsStored(p.uniqueId) && itemDrop.itemStack.type == Material.SHIELD) {
            e.isCancelled = true
            restore(p)
        }
    }

    private fun restore(p: Player, force: Boolean = false) {
        val id = p.uniqueId

        tryCancelTask(id)

        if (!areItemsStored(id)) return

        // If they are still blocking with the shield, postpone restoring
        if (!force && isPlayerBlocking(p)) scheduleRestore(p)
        else p.inventory.setItemInOffHand(storedItems.remove(id))
    }

    private fun tryCancelTask(id: UUID) {
        correspondingTasks.remove(id)?.forEach { it.cancel() }
    }

    private fun scheduleRestore(p: Player) {
        val id = p.uniqueId
        tryCancelTask(id)

        val removeItem = Bukkit.getScheduler().runTaskLater(plugin, Runnable { restore(p) }, restoreDelay.toLong())

        val checkBlocking = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            if (!isPlayerBlocking(p)) restore(p)
        }, 10L, 2L)

        val tasks: MutableList<BukkitTask> = ArrayList(2)
        tasks.add(removeItem)
        tasks.add(checkBlocking)
        correspondingTasks[p.uniqueId] = tasks
    }

    private fun areItemsStored(uuid: UUID) = storedItems.containsKey(uuid)

    /**
     * Checks whether player is blocking or they have just begun to and shield is not fully up yet.
     */
    private fun isPlayerBlocking(player: Player): Boolean {
        return player.isBlocking || (VersionCompatUtils.versionIsNewerOrEqualTo(
            1, 11, 0
        ) && player.isHandRaised && hasShield(player.inventory))
    }

    private fun hasShield(inventory: PlayerInventory) = inventory.itemInOffHand.type == Material.SHIELD

    private fun isHoldingSword(mat: Material) = mat.toString().endsWith("_SWORD")
}
