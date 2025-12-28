/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics

import com.cryptomorin.xseries.XAttribute
import kernitus.plugin.OldCombatMechanics.utilities.damage.NewWeaponDamage
import org.bukkit.attribute.AttributeModifier
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.attribute.Attribute
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import com.google.common.collect.HashMultimap
import com.google.common.collect.Multimap
import java.util.UUID

fun createAttributeModifier(
    name: String,
    amount: Double,
    operation: AttributeModifier.Operation,
    slot: EquipmentSlot? = null,
    uuid: UUID = UUID.randomUUID()
): AttributeModifier {
    // Use the most specific constructor available at runtime.
    if (slot != null) {
        try {
            @Suppress("DEPRECATION")
            return AttributeModifier(uuid, name, amount, operation, slot)
        } catch (e: NoSuchMethodError) {
            // Fall back to the older signatures below.
        }
    }

    try {
        @Suppress("DEPRECATION")
        return AttributeModifier(uuid, name, amount, operation)
    } catch (e: NoSuchMethodError) {
        @Suppress("DEPRECATION")
        return AttributeModifier(name, amount, operation)
    }
}

fun addAttributeModifierCompat(meta: ItemMeta, attribute: Attribute, modifier: AttributeModifier) {
    try {
        meta.addAttributeModifier(attribute, modifier)
        return
    } catch (e: NoSuchMethodError) {
        // Older APIs do not expose addAttributeModifier on ItemMeta.
    }

    try {
        val multimap = HashMultimap.create<Attribute, AttributeModifier>()
        multimap.put(attribute, modifier)
        meta.setAttributeModifiers(multimap)
    } catch (e: NoSuchMethodError) {
        // Attribute modifiers are not supported on this API version.
    }
}

fun getDefaultAttributeModifiersCompat(
    item: ItemStack,
    slot: EquipmentSlot,
    attribute: Attribute
): Collection<AttributeModifier> {
    try {
        return item.type.getDefaultAttributeModifiers(slot)[attribute] ?: emptySet()
    } catch (e: NoSuchMethodError) {
        // Fall back to older Material APIs if present.
    }

    val modifiers = try {
        val method = item.type.javaClass.getMethod("getAttributeModifiers", EquipmentSlot::class.java)
        @Suppress("UNCHECKED_CAST")
        val multimap = method.invoke(item.type, slot) as Multimap<Attribute, AttributeModifier>
        multimap.get(attribute) ?: emptySet()
    } catch (e: Exception) {
        emptySet()
    }

    if (modifiers.isNotEmpty()) {
        return modifiers
    }

    val attackDamageAttribute = XAttribute.ATTACK_DAMAGE.get()
    if (attackDamageAttribute != null && attribute == attackDamageAttribute && slot == EquipmentSlot.HAND) {
        val fallbackDamage = NewWeaponDamage.getDamageOrNull(item.type) ?: return emptySet()
        val amount = fallbackDamage.toDouble() - 1.0
        val fallbackModifier = createAttributeModifier(
            name = "ocm-fallback-damage",
            amount = amount,
            operation = AttributeModifier.Operation.ADD_NUMBER,
            slot = slot
        )
        return setOf(fallbackModifier)
    }

    return emptySet()
}
