package kernitus.plugin.OldCombatMechanics.paper

import io.papermc.paper.datacomponent.DataComponentTypes.CONSUMABLE
import io.papermc.paper.datacomponent.item.Consumable
import io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation
import net.kyori.adventure.key.Key
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

/** Loaded through the component capability boundary, never by legacy module verification. */
internal class PublicSwordComponents : PaperSwordBlocking.Components {
    private val marker = NamespacedKey("oldcombatmechanics", "sword_consumable")

    // Retain exact historical values solely to repair marked items from earlier builds.
    private val faultyBlocking =
        (ItemStack(Material.BREAD).getData(CONSUMABLE)?.toBuilder() ?: Consumable.consumable().consumeSeconds(1.6f))
            .animation(ItemUseAnimation.BLOCK)
            .build()
    private val legacyBlocking =
        Consumable
            .consumable()
            .consumeSeconds(2_592_000f)
            .animation(ItemUseAnimation.BLOCK)
            .build()
    private val blocking =
        Consumable
            .consumable()
            // Thirty days of uninterrupted use, safely below the native integer tick limit.
            .consumeSeconds(2_592_000f)
            .animation(ItemUseAnimation.BLOCK)
            .sound(Key.key("minecraft:intentionally_empty"))
            .hasConsumeParticles(false)
            .effects(emptyList())
            .build()

    init {
        // Resolve the full public contract before choosing this capability. These operations affect only a temporary fixture.
        ItemStack(Material.DIAMOND_SWORD).also {
            it.isDataOverridden(CONSUMABLE)
            it.resetData(CONSUMABLE)
        }
    }

    override fun applyComponents(stack: ItemStack?): Boolean {
        if (stack == null || !PaperSwordBlocking.isSword(stack.type)) return false
        val meta = stack.itemMeta ?: return false
        val current = stack.getData(CONSUMABLE)
        val marked = meta.persistentDataContainer.has(marker, PersistentDataType.BYTE)
        val outdated = marked && (current == faultyBlocking || current == legacyBlocking)
        // Existing values and explicit removals belong to the item owner, including material defaults.
        if (!outdated && (current != null || stack.isDataOverridden(CONSUMABLE))) return false
        meta.persistentDataContainer.set(marker, PersistentDataType.BYTE, 1.toByte())
        stack.itemMeta = meta
        stack.setData(CONSUMABLE, blocking)
        return true
    }

    override fun clearComponents(stack: ItemStack?): Boolean {
        if (stack == null || !PaperSwordBlocking.isSword(stack.type)) return false
        val meta = stack.itemMeta ?: return false
        if (!meta.persistentDataContainer.has(marker, PersistentDataType.BYTE)) return false
        val current = stack.getData(CONSUMABLE)
        val owned = current == blocking || current == faultyBlocking || current == legacyBlocking
        meta.persistentDataContainer.remove(marker)
        stack.itemMeta = meta
        // A changed foreign component wins. Equal-valued foreign writes remain indistinguishable.
        if (owned) stack.resetData(CONSUMABLE)
        return true
    }

    override fun hasConsumableComponent(stack: ItemStack?): Boolean =
        stack != null && PaperSwordBlocking.isSword(stack.type) && stack.hasData(CONSUMABLE)
}
