package kernitus.plugin.OldCombatMechanics

import io.kotest.matchers.shouldBe
import io.papermc.paper.datacomponent.DataComponentTypes.ATTACK_RANGE
import io.papermc.paper.datacomponent.item.AttackRange
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

/** Modern API assertions; callers dispatch synthetic lifecycle events through the real listeners. */
internal object AttackRangeOwnershipChecks {
    private val owner = NamespacedKey("oldcombatmechanics", "attack_range")

    fun runCase(
        name: String,
        apply: (ItemStack) -> Unit,
        clear: (ItemStack) -> ItemStack,
    ) {
        var item = ItemStack(Material.IRON_SWORD)
        val original = item.clone()
        val foreign =
            AttackRange
                .attackRange()
                .minReach(1f)
                .maxReach(7f)
                .minCreativeReach(2f)
                .maxCreativeReach(8f)
                .hitboxMargin(0.3f)
                .mobFactor(1.5f)
                .build()
        when (name) {
            "foreign override" -> {
                item.setData(ATTACK_RANGE, foreign)
                val before = item.clone()
                apply(item)
                item shouldBe before
                item = clear(item)
                item shouldBe before
            }

            "explicit removal" -> {
                // Spears have a native range, so its explicit removal cannot be canonicalised away.
                var spear = ItemStack(checkNotNull(Material.matchMaterial("IRON_SPEAR")))
                spear.unsetData(ATTACK_RANGE)
                val before = spear.clone()
                apply(spear)
                spear = clear(spear)
                spear shouldBe before
                spear.isDataOverridden(ATTACK_RANGE) shouldBe true
            }

            "owned serialised item" -> {
                apply(item)
                item.isDataOverridden(ATTACK_RANGE) shouldBe true
                item.persistentDataContainer.has(owner) shouldBe true
                val restored = ItemStack.deserializeBytes(item.serializeAsBytes())
                clear(restored) shouldBe original
            }

            "later foreign override" -> {
                apply(item)
                item.setData(ATTACK_RANGE, foreign)
                apply(item)
                item.getData(ATTACK_RANGE) shouldBe foreign
                item = clear(item)
                item.getData(ATTACK_RANGE) shouldBe foreign
                item.persistentDataContainer.has(owner) shouldBe false
            }

            "later foreign removal" -> {
                apply(item)
                item.unsetData(ATTACK_RANGE)
                apply(item)
                item.hasData(ATTACK_RANGE) shouldBe false
                item = clear(item)
                item.hasData(ATTACK_RANGE) shouldBe false
                item.persistentDataContainer.has(owner) shouldBe false
            }

            "unmarked matching values" -> {
                apply(item)
                item.editPersistentDataContainer { it.remove(owner) }
                val before = item.clone()
                apply(item)
                item = clear(item)
                item shouldBe before
            }

            "malformed ownership", "wrong marker type" -> {
                item.setData(ATTACK_RANGE, foreign)
                item.editPersistentDataContainer {
                    if (name == "wrong marker type") {
                        it.set(owner, PersistentDataType.STRING, "foreign")
                    } else {
                        it.set(owner, PersistentDataType.INTEGER_ARRAY, intArrayOf(1, 2))
                    }
                }
                apply(item)
                item = clear(item)
                item.getData(ATTACK_RANGE) shouldBe foreign
                item.persistentDataContainer.has(owner) shouldBe false
            }

            "unrelated metadata" -> {
                item.editMeta { it.setDisplayName("Custom sword") }
                item.editPersistentDataContainer {
                    it.set(
                        NamespacedKey("test", "foreign"),
                        PersistentDataType.STRING,
                        "keep",
                    )
                }
                val before = item.clone()
                apply(item)
                item = clear(item)
                item shouldBe before
            }

            else -> {
                val index =
                    listOf("minimum", "maximum", "creative minimum", "creative maximum", "margin", "mob factor")
                        .indexOf(name.removePrefix("changed "))
                check(index >= 0) { "Unknown ownership test: $name" }
                apply(item)
                val current = checkNotNull(item.getData(ATTACK_RANGE))
                val values =
                    floatArrayOf(
                        current.minReach(),
                        current.maxReach(),
                        current.minCreativeReach(),
                        current.maxCreativeReach(),
                        current.hitboxMargin(),
                        current.mobFactor(),
                    )
                values[index] += 0.1f
                val changed =
                    AttackRange
                        .attackRange()
                        .minReach(values[0])
                        .maxReach(values[1])
                        .minCreativeReach(
                            values[2],
                        ).maxCreativeReach(values[3])
                        .hitboxMargin(values[4])
                        .mobFactor(values[5])
                        .build()
                item.setData(ATTACK_RANGE, changed)
                apply(item)
                item.getData(ATTACK_RANGE) shouldBe changed
                clear(item).getData(ATTACK_RANGE) shouldBe changed
            }
        }
    }

    fun assertRange(
        item: ItemStack,
        maximum: Float,
    ) {
        checkNotNull(item.getData(ATTACK_RANGE)).maxReach() shouldBe maximum
        item.persistentDataContainer.has(owner) shouldBe true
    }

    fun assertClean(item: ItemStack) {
        item.getData(ATTACK_RANGE) shouldBe ItemStack(item.type).getData(ATTACK_RANGE)
        item.isDataOverridden(ATTACK_RANGE) shouldBe false
        item.persistentDataContainer.has(owner) shouldBe false
    }
}
