package kernitus.plugin.OldCombatMechanics

import io.kotest.matchers.shouldBe
import io.papermc.paper.datacomponent.DataComponentTypes.CONSUMABLE
import io.papermc.paper.datacomponent.item.Consumable
import io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation
import kernitus.plugin.OldCombatMechanics.paper.PaperSwordBlocking
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/** Modern-only assertions loaded after the component capability check. Fixtures here are synthetic stacks. */
internal object SwordComponentCapabilityChecks {
    fun hasComponent(stack: ItemStack?): Boolean = stack != null && stack.hasData(CONSUMABLE)

    fun hasRemoval(stack: ItemStack?): Boolean =
        stack != null && stack.isDataOverridden(CONSUMABLE) && !stack.hasData(CONSUMABLE)

    fun preservesOwnership() {
        legacyFallbackRestoresDefaults()
        val adapter = PaperSwordBlocking()
        val sword = ItemStack(Material.DIAMOND_SWORD)
        sword.setData(
            io.papermc.paper.datacomponent.DataComponentTypes.ATTRIBUTE_MODIFIERS,
            checkNotNull(sword.getData(io.papermc.paper.datacomponent.DataComponentTypes.ATTRIBUTE_MODIFIERS)),
        )
        val original = sword.clone()
        adapter.applyComponents(sword)
        sword.hasData(CONSUMABLE) shouldBe true
        adapter.clearComponents(sword)
        sword.isDataOverridden(CONSUMABLE) shouldBe false
        sword shouldBe original

        val removed = ItemStack(Material.IRON_SWORD)
        removed.unsetData(CONSUMABLE)
        val originalRemoval = removed.clone()
        adapter.applyComponents(removed)
        adapter.clearComponents(removed)
        removed shouldBe originalRemoval
        // Paper may canonicalise removal of an already absent default. Preserve its exact representation.
        removed.isDataOverridden(CONSUMABLE) shouldBe originalRemoval.isDataOverridden(CONSUMABLE)

        val foreign =
            Consumable
                .consumable()
                .consumeSeconds(3f)
                .animation(ItemUseAnimation.EAT)
                .build()
        val foreignSword = ItemStack(Material.DIAMOND_SWORD)
        foreignSword.setData(CONSUMABLE, foreign)
        val originalForeign = foreignSword.clone()
        adapter.applyComponents(foreignSword)
        adapter.clearComponents(foreignSword)
        foreignSword shouldBe originalForeign

        adapter.applyComponents(sword)
        sword.setData(CONSUMABLE, foreign)
        adapter.clearComponents(sword)
        sword.getData(CONSUMABLE) shouldBe foreign

        // A foreign native write has no OCM ownership marker and must survive lifecycle cleanup.
        val nativeSword = ItemStack(Material.IRON_SWORD).ensureServerConversions()
        val native = SwordNativeUseChecks.heldStack(nativeSword)
        val componentType = Class.forName("net.minecraft.core.component.DataComponentType")
        val types = Class.forName("net.minecraft.core.component.DataComponents")
        val consumableType = types.getField("CONSUMABLE").get(null)
        val nativeBread = SwordNativeUseChecks.heldStack(ItemStack(Material.BREAD).ensureServerConversions())
        val nativeConsumable = nativeBread.javaClass.getMethod("get", componentType).invoke(nativeBread, consumableType)
        val set =
            native.javaClass.methods.first {
                it.name == "set" && it.parameterCount == 2 && it.parameterTypes[0] == componentType
            }
        set.invoke(native, consumableType, nativeConsumable)
        val beforeNativeCleanup = nativeSword.clone()
        adapter.applyComponents(nativeSword)
        adapter.clearComponents(nativeSword)
        nativeSword shouldBe beforeNativeCleanup

        val bread = ItemStack(Material.BREAD)
        val originalBread = bread.clone()
        adapter.applyComponents(bread)
        adapter.clearComponents(bread)
        bread shouldBe originalBread
        bread.unsetData(CONSUMABLE)
        hasRemoval(bread) shouldBe true
        val removedDefault = bread.clone()
        adapter.applyComponents(bread)
        adapter.clearComponents(bread)
        bread shouldBe removedDefault
        hasRemoval(bread) shouldBe true
    }

    fun legacyFallbackRestoresDefaults() {
        val patch = Class.forName("net.minecraft.core.component.DataComponentPatch")
        if (patch.methods.none { it.name == "entrySet" }) {
            println(
                "Legacy native component adapter unavailable: patch entrySet absent; public path verified separately",
            )
            return
        }
        val type = Class.forName("kernitus.plugin.OldCombatMechanics.paper.LegacySwordComponents")
        val constructor = type.getDeclaredConstructor().also { it.isAccessible = true }
        val adapter = constructor.newInstance()
        val apply = type.getMethod("applyComponents", ItemStack::class.java).also { it.isAccessible = true }
        val clear = type.getMethod("clearComponents", ItemStack::class.java).also { it.isAccessible = true }
        val item = ItemStack(Material.DIAMOND_SWORD).ensureServerConversions()
        val original = item.clone()
        apply.invoke(adapter, item)
        item.hasData(CONSUMABLE) shouldBe true
        clear.invoke(adapter, item) shouldBe true
        item shouldBe original
        item.isDataOverridden(CONSUMABLE) shouldBe false
    }

    fun worksWithoutNativeDiscovery(player: Player) {
        val parent = PaperSwordBlocking::class.java.classLoader
        var nativeLookups = 0
        val loader =
            object : ClassLoader(parent) {
                override fun loadClass(
                    name: String,
                    resolve: Boolean,
                ): Class<*> {
                    if (name.startsWith("net.minecraft.")) {
                        nativeLookups++
                        throw ClassNotFoundException("Native access forbidden: $name")
                    }
                    val adapterPackage = "kernitus.plugin.OldCombatMechanics.paper."
                    if (!name.startsWith(adapterPackage)) return super.loadClass(name, resolve)
                    val loaded =
                        findLoadedClass(name) ?: run {
                            val resource = name.replace('.', '/') + ".class"
                            val bytes = checkNotNull(parent.getResourceAsStream(resource)).use { it.readBytes() }
                            defineClass(name, bytes, 0, bytes.size)
                        }
                    if (resolve) resolveClass(loaded)
                    return loaded
                }
            }
        val type = loader.loadClass(PaperSwordBlocking::class.java.name)
        val adapter = type.getConstructor().newInstance()
        val sword = ItemStack(Material.DIAMOND_SWORD)
        type.getMethod("applyComponents", ItemStack::class.java).invoke(adapter, sword)
        sword.hasData(CONSUMABLE) shouldBe true
        type.getMethod("hasConsumableComponent", ItemStack::class.java).invoke(adapter, sword) shouldBe true
        type.getMethod("isBlockingSword", Player::class.java).invoke(adapter, player) shouldBe false
        type.getMethod("clearComponents", ItemStack::class.java).invoke(adapter, sword)
        sword.isDataOverridden(CONSUMABLE) shouldBe false
        nativeLookups shouldBe 0
    }
}
