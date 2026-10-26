package kernitus.plugin.OldCombatMechanics.paper

import io.papermc.paper.datacomponent.DataComponentTypes.CONSUMABLE
import io.papermc.paper.datacomponent.item.Consumable
import io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

/** Loaded through the component capability boundary, never by legacy module verification. */
internal class PublicSwordComponents : PaperSwordBlocking.Components {
    private val marker = NamespacedKey("oldcombatmechanics", "sword_consumable")
    private val blocking =
        (ItemStack(Material.BREAD).getData(CONSUMABLE)?.toBuilder() ?: Consumable.consumable().consumeSeconds(1.6f))
            .animation(ItemUseAnimation.BLOCK)
            .build()

    init {
        // Resolve the full public contract before choosing this capability. These operations affect only a temporary fixture.
        ItemStack(Material.DIAMOND_SWORD).also {
            it.isDataOverridden(CONSUMABLE)
            it.resetData(CONSUMABLE)
        }
    }

    override fun applyComponents(stack: ItemStack?) {
        if (stack == null || !PaperSwordBlocking.isSword(stack.type)) return
        // Existing values and explicit removals belong to the item owner, including material defaults.
        if (stack.hasData(CONSUMABLE) || stack.isDataOverridden(CONSUMABLE)) return
        val meta = stack.itemMeta ?: return
        meta.persistentDataContainer.set(marker, PersistentDataType.BYTE, 1.toByte())
        stack.itemMeta = meta
        stack.setData(CONSUMABLE, blocking)
    }

    override fun clearComponents(stack: ItemStack?): Boolean {
        if (stack == null || !PaperSwordBlocking.isSword(stack.type)) return false
        val meta = stack.itemMeta ?: return false
        if (!meta.persistentDataContainer.has(marker, PersistentDataType.BYTE)) return false
        val owned = stack.getData(CONSUMABLE) == blocking
        meta.persistentDataContainer.remove(marker)
        stack.itemMeta = meta
        // A changed foreign component wins. Equal-valued foreign writes remain indistinguishable.
        if (owned) stack.resetData(CONSUMABLE)
        return true
    }

    override fun hasConsumableComponent(stack: ItemStack?): Boolean =
        stack != null && PaperSwordBlocking.isSword(stack.type) && stack.hasData(CONSUMABLE)
}
