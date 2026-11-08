package kernitus.plugin.OldCombatMechanics.module

import io.papermc.paper.datacomponent.DataComponentTypes.ATTACK_RANGE
import io.papermc.paper.datacomponent.item.AttackRange
import kernitus.plugin.OldCombatMechanics.utilities.Messenger
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

/** Loaded only through ModuleAttackRange's capability boundary. No native access is required. */
internal class PaperAttackRangeAdapter {
    private val owner = NamespacedKey("oldcombatmechanics", "attack_range")
    private var warned = false

    init {
        // Resolve the public contract before enabling the module on this server.
        ItemStack(Material.IRON_SWORD).also {
            it.getData(ATTACK_RANGE)
            it.isDataOverridden(ATTACK_RANGE)
            it.resetData(ATTACK_RANGE)
            it.editPersistentDataContainer { data -> data.remove(owner) }
        }
    }

    private fun record(value: AttackRange): IntArray =
        intArrayOf(
            1, // Persistent record format, followed by the exact float bits of our last write.
            java.lang.Float.floatToIntBits(value.minReach()),
            java.lang.Float.floatToIntBits(value.maxReach()),
            java.lang.Float.floatToIntBits(value.minCreativeReach()),
            java.lang.Float.floatToIntBits(value.maxCreativeReach()),
            java.lang.Float.floatToIntBits(value.hitboxMargin()),
            java.lang.Float.floatToIntBits(value.mobFactor()),
        )

    private fun matches(stack: ItemStack): Boolean {
        if (!stack.persistentDataContainer.has(owner, PersistentDataType.INTEGER_ARRAY)) return false
        val saved = stack.persistentDataContainer.get(owner, PersistentDataType.INTEGER_ARRAY) ?: return false
        val current = stack.getData(ATTACK_RANGE) ?: return false
        return saved.contentEquals(record(current))
    }

    fun apply(
        stack: ItemStack,
        min: Float,
        max: Float,
        minCreative: Float,
        maxCreative: Float,
        margin: Float,
        mobFactor: Float,
    ) {
        try {
            if (stack.persistentDataContainer.has(owner)) {
                if (!matches(stack)) {
                    // Another writer changed the component, or this record is unknown. Relinquish ownership.
                    stack.editPersistentDataContainer { it.remove(owner) }
                    return
                }
            } else if (stack.isDataOverridden(ATTACK_RANGE)) {
                // This includes explicit removals and unmarked items from older OCM releases.
                return
            }
            val value =
                AttackRange
                    .attackRange()
                    .minReach(min)
                    .maxReach(max)
                    .minCreativeReach(minCreative)
                    .maxCreativeReach(maxCreative)
                    .hitboxMargin(margin)
                    .mobFactor(mobFactor)
                    .build()
            val saved = record(value)
            if (stack.persistentDataContainer.get(owner, PersistentDataType.INTEGER_ARRAY)?.contentEquals(saved) ==
                true
            ) {
                return
            }
            stack.setData(ATTACK_RANGE, value)
            stack.editPersistentDataContainer { it.set(owner, PersistentDataType.INTEGER_ARRAY, saved) }
        } catch (failure: RuntimeException) {
            report(failure)
        }
    }

    fun clearOverride(stack: ItemStack) {
        try {
            if (!stack.persistentDataContainer.has(owner)) return
            val owned = matches(stack)
            // Restore the live material default only while the component still matches our recorded write.
            if (owned) stack.resetData(ATTACK_RANGE)
            stack.editPersistentDataContainer { it.remove(owner) }
        } catch (failure: RuntimeException) {
            report(failure)
        }
    }

    private fun report(failure: RuntimeException) {
        if (warned) return
        warned = true
        Messenger.warn("Attack range component operation failed (${failure.javaClass.simpleName}: ${failure.message})")
    }
}
