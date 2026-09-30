/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics

import com.cryptomorin.xseries.XAttribute
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector
import org.bukkit.Bukkit
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.entity.Player
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import java.util.UUID

/** Native fixtures for item behaviour predating Bukkit's item-attribute and drop APIs. */
internal object WeaponBaselineNativeCompat {
    data class Modifier(
        val uuid: UUID,
        val name: String,
        val amount: Double,
        val operation: Int,
        val slot: String?,
    )

    private fun craftItemStack(): Class<*> =
        Class.forName("${Bukkit.getServer().javaClass.`package`.name}.inventory.CraftItemStack")

    private fun nativeCopy(item: ItemStack): Any =
        checkNotNull(craftItemStack().getMethod("asNMSCopy", ItemStack::class.java).invoke(null, item))

    private fun call(
        instance: Any,
        name: String,
        vararg args: Any,
    ): Any? =
        Reflector.invokeMethod<Any?>(
            checkNotNull(Reflector.getMethod(instance.javaClass, name, args.size)),
            instance,
            *args,
        )

    private fun key(attribute: Attribute): String =
        when (attribute) {
            XAttribute.ATTACK_DAMAGE.get() -> "generic.attackDamage"
            XAttribute.ATTACK_SPEED.get() -> "generic.attackSpeed"
            else -> error("Unsupported native weapon fixture attribute: $attribute")
        }

    private fun snapshot(modifier: AttributeModifier): Modifier =
        Modifier(
            modifier.uniqueId,
            modifier.name,
            modifier.amount,
            modifier.operation.ordinal,
            when (modifier.slot) {
                EquipmentSlot.HAND -> "mainhand"
                EquipmentSlot.OFF_HAND -> "offhand"
                else -> modifier.slot?.name
            },
        )

    fun modifiers(
        item: ItemStack,
        attribute: Attribute,
    ): List<Modifier> {
        try {
            return item.itemMeta!!
                .getAttributeModifiers(
                    attribute,
                ).orEmpty()
                .map { snapshot(it) }
                .sortedBy { it.uuid.toString() }
        } catch (_: NoSuchMethodError) {
            // Native ItemStack modifiers exist on older servers even though ItemMeta cannot expose them.
        }
        val tag = call(nativeCopy(item), "getTag") ?: return emptyList()
        val list = checkNotNull(call(tag, "getList", "AttributeModifiers", 10))
        return (0 until (call(list, "size") as Int))
            .mapNotNull { index ->
                val entry = checkNotNull(call(list, "get", index))
                if (call(entry, "getString", "AttributeName") != key(attribute)) return@mapNotNull null
                Modifier(
                    UUID(call(entry, "getLong", "UUIDMost") as Long, call(entry, "getLong", "UUIDLeast") as Long),
                    call(entry, "getString", "Name") as String,
                    call(entry, "getDouble", "Amount") as Double,
                    call(entry, "getInt", "Operation") as Int,
                    (call(entry, "getString", "Slot") as String).takeIf { it.isNotEmpty() },
                )
            }.sortedBy { it.uuid.toString() }
    }

    fun customWeapon(
        item: ItemStack,
        operation: AttributeModifier.Operation,
        amount: Double,
        slot: EquipmentSlot,
    ): ItemStack {
        val damage = checkNotNull(XAttribute.ATTACK_DAMAGE.get())
        val speed = checkNotNull(XAttribute.ATTACK_SPEED.get())
        val damageId = UUID.randomUUID()
        val speedId = UUID.randomUUID()
        val meta = item.itemMeta!!
        var expectedDamage =
            Modifier(
                damageId,
                "foreign-damage",
                amount,
                operation.ordinal,
                if (slot == EquipmentSlot.OFF_HAND) "offhand" else "mainhand",
            )
        var expectedSpeed = Modifier(speedId, "foreign-speed", 2.0, 0, "mainhand")
        try {
            val damageModifier = createAttributeModifier("foreign-damage", amount, operation, slot, damageId)
            val speedModifier =
                createAttributeModifier(
                    "foreign-speed",
                    2.0,
                    AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlot.HAND,
                    speedId,
                )
            meta.addAttributeModifier(damage, damageModifier)
            meta.addAttributeModifier(speed, speedModifier)
            // Retain the API constructor's identity representation on servers using namespaced modifier keys.
            expectedDamage = snapshot(damageModifier)
            expectedSpeed = snapshot(speedModifier)
            item.itemMeta = meta
        } catch (_: NoSuchMethodError) {
            val native = nativeCopy(item)
            val prefix = native.javaClass.`package`.name
            val compound = Class.forName("$prefix.NBTTagCompound")
            val list = Class.forName("$prefix.NBTTagList").getConstructor().newInstance()
            for ((attribute, modifier) in listOf(
                damage to
                    Modifier(
                        damageId,
                        "foreign-damage",
                        amount,
                        operation.ordinal,
                        if (slot == EquipmentSlot.OFF_HAND) "offhand" else "mainhand",
                    ),
                speed to Modifier(speedId, "foreign-speed", 2.0, 0, "mainhand"),
            )) {
                val entry = compound.getConstructor().newInstance()
                call(entry, "setString", "AttributeName", key(attribute))
                call(entry, "setString", "Name", modifier.name)
                call(entry, "setString", "Slot", checkNotNull(modifier.slot))
                call(entry, "setDouble", "Amount", modifier.amount)
                call(entry, "setInt", "Operation", modifier.operation)
                call(entry, "setLong", "UUIDMost", modifier.uuid.mostSignificantBits)
                call(entry, "setLong", "UUIDLeast", modifier.uuid.leastSignificantBits)
                call(list, "add", entry)
            }
            val tag = call(native, "getTag") ?: compound.getConstructor().newInstance()
            call(tag, "set", "AttributeModifiers", list)
            call(native, "setTag", tag)
            val converted =
                craftItemStack()
                    .getMethod(
                        "asBukkitCopy",
                        native.javaClass,
                    ).invoke(null, native) as ItemStack
            item.itemMeta = converted.itemMeta
        }
        val actualDamage = modifiers(item, damage).single()
        check(actualDamage == expectedDamage) { "Native custom damage modifier did not survive item conversion" }
        check(modifiers(item, speed).single() == expectedSpeed) {
            "Native custom speed modifier did not survive item conversion"
        }
        return item
    }

    fun dropSelectedItem(player: Player): Boolean {
        try {
            return player.dropItem(true)
        } catch (_: NoSuchMethodError) {
            val handle = checkNotNull(call(player, "getHandle"))
            val method =
                handle.javaClass.methods.single {
                    it.parameterTypes.contentEquals(arrayOf(Boolean::class.javaPrimitiveType)) &&
                        it.returnType.simpleName == "EntityItem"
                }
            return method.invoke(handle, true) != null
        }
    }

    fun ensureOffhandUse(player: Player) {
        if (player.isBlocking) return
        val handle = checkNotNull(call(player, "getHandle"))
        val hand = Class.forName("${handle.javaClass.`package`.name}.EnumHand")
        val offhand = hand.enumConstants.single { (it as Enum<*>).name == "OFF_HAND" }
        Reflector.invokeMethod<Any?>(
            checkNotNull(Reflector.getMethod(handle.javaClass, "c", hand.simpleName)),
            handle,
            offhand,
        )
    }
}
