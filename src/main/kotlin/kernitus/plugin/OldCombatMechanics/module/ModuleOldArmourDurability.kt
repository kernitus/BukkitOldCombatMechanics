/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module

import kernitus.plugin.OldCombatMechanics.OCMMain
import kernitus.plugin.OldCombatMechanics.versions.enchantments.EnchantmentCompat
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDamageEvent.DamageCause
import org.bukkit.event.player.PlayerItemDamageEvent
import org.bukkit.inventory.ItemStack
import java.util.*

class ModuleOldArmourDurability(plugin: OCMMain) : OCMModule(plugin, "old-armour-durability") {
    private val explosionDamaged: MutableMap<UUID, MutableList<ItemStack>> = WeakHashMap()

    @EventHandler(priority = EventPriority.LOWEST)
    fun onItemDamage(e: PlayerItemDamageEvent) {
        val player = e.player

        if (!isEnabled(player)) return
        val item = e.item
        val itemType = item.type

        // Check if it's a piece of armour they're currently wearing
        if (player.inventory.armorContents.filterNotNull()
                .none { it.type == itemType && it.type != Material.ELYTRA }
        ) return

        val uuid = player.uniqueId
        explosionDamaged[uuid]?.let { armour ->
            // ItemStack.equals() checks material, durability and quantity to make sure nothing changed in the meantime
            // We're checking all the pieces this way just in case they're wearing two helmets or something strange
            val matchedPieces = armour.stream().filter { piece: ItemStack -> piece == item }.toList()
            armour.removeAll(matchedPieces)
            debug("Item matched explosion, ignoring...", player)
            if (matchedPieces.isNotEmpty()) return
        }

        var reduction = module().getInt("reduction")

        // 60 + (40 / (level + 1) ) % chance that durability is reduced (for each point of durability)
        val damageChance = 60 + (40 / (item.getEnchantmentLevel(EnchantmentCompat.UNBREAKING.get()) + 1))
        val random = Random()
        val randomInt = random.nextInt(100) // between 0 (inclusive) and 100 (exclusive)
        if (randomInt >= damageChance) reduction = 0

        debug("Item damaged: " + itemType + " Damage: " + e.damage + " Changed to: " + reduction, player)
        e.damage = reduction
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerExplosionDamage(e: EntityDamageEvent) {
        if (e.entityType != EntityType.PLAYER) return
        val cause = e.cause
        if (cause != DamageCause.BLOCK_EXPLOSION && cause != DamageCause.ENTITY_EXPLOSION) return

        val player = e.entity as Player
        val uuid = player.uniqueId
        val armour = player.inventory.armorContents.filterNotNull().toMutableList()
        explosionDamaged[uuid] = armour

        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            explosionDamaged.remove(uuid)
            debug("Removed from explosion set!", player)
        }, 1L) // This delay seems enough for the durability events to fire

        debug("Detected explosion!", player)
    }
}
