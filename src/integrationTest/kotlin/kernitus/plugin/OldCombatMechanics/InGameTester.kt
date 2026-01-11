/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics

import com.cryptomorin.xseries.XAttribute
import com.cryptomorin.xseries.XEnchantment
import com.cryptomorin.xseries.XPotion
import kernitus.plugin.OldCombatMechanics.TesterUtils.assertEquals
import kernitus.plugin.OldCombatMechanics.utilities.damage.DamageUtils.getOldSharpnessDamage
import kernitus.plugin.OldCombatMechanics.utilities.damage.DamageUtils.isCriticalHit1_8
import kernitus.plugin.OldCombatMechanics.utilities.damage.DefenceUtils.getDamageAfterArmour1_8
import kernitus.plugin.OldCombatMechanics.utilities.damage.WeaponDamages.getDamage
import kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage.getPlayerData
import kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage.setPlayerData
import kernitus.plugin.OldCombatMechanics.TesterUtils.getPotionEffectCompat
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.attribute.AttributeModifier
import org.bukkit.command.CommandSender
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionEffect
import java.util.*
import java.util.function.Consumer
import kotlin.math.max

class InGameTester(private val plugin: JavaPlugin) {
    private var tally: Tally? = null
    private var sender: CommandSender? = null
    private lateinit var attacker: Player
    private lateinit var defender: Player
    private lateinit var fakeAttacker: FakePlayer
    private lateinit var fakeDefender: FakePlayer
    private val testQueue: Queue<OCMTest> = ArrayDeque()

    /**
     * Perform all tests using the two specified players
     */
    fun performTests(sender: CommandSender?, location: Location) {
        plugin.logger.info("PERFORMING THE TESTS")
        this.sender = sender
        fakeAttacker = FakePlayer(plugin)
        plugin.logger.info("FAKE")
        fakeAttacker.spawn(location.add(2.0, 0.0, 0.0))
        plugin.logger.info("FAKE2")
        fakeDefender = FakePlayer(plugin)
        val defenderLocation = location.add(0.0, 0.0, 2.0)
        fakeDefender.spawn(defenderLocation)

        attacker = checkNotNull(Bukkit.getPlayer(fakeAttacker.uuid))
        defender = checkNotNull(Bukkit.getPlayer(fakeDefender.uuid))

        // Turn defender to face attacker
        defenderLocation.yaw = 180f
        defenderLocation.pitch = 0f
        defender.teleport(defenderLocation)

        // modeset of attacker takes precedence
        var playerData = getPlayerData(attacker.uniqueId)
        playerData.setModesetForWorld(attacker.world.uid, "old")
        setPlayerData(attacker.uniqueId, playerData)

        playerData = getPlayerData(defender.uniqueId)
        playerData.setModesetForWorld(defender.world.uid, "new")
        setPlayerData(defender.uniqueId, playerData)

        beforeAll()
        tally = Tally()

        // Queue all tests
        //runAttacks(new ItemStack[]{}, () -> {}); // with no armour
        testArmour()

        //testEnchantedMelee(new ItemStack[]{}, () -> {});

        // Run all tests in the queue
        runQueuedTests()
    }

    private fun runAttacks(armour: Array<ItemStack>, preparations: Runnable) {
        //testMelee(armour, preparations);
        testEnchantedMelee(armour, preparations)
        testOverdamage(armour, preparations)
    }

    private fun testArmour() {
        val materials = arrayOf("LEATHER", "CHAINMAIL", "GOLDEN", "IRON", "DIAMOND", "NETHERITE")
        val slots = arrayOf("BOOTS", "LEGGINGS", "CHESTPLATE", "HELMET")
        val random = Random(System.currentTimeMillis())

        val armourContents = Array(4) { i ->
            val slot = slots[i]
            // Pick a random material for each slot
            val material = materials[random.nextInt(materials.size)]

            val itemStack = ItemStack(Material.valueOf("${material}_$slot"))

            // Apply enchantment to the armour piece
            itemStack.addUnsafeEnchantment(XEnchantment.PROTECTION.get()!!, 50)

            itemStack
        }

        runAttacks(armourContents) {
            defender.inventory.setArmorContents(armourContents)
            // Test status effects on defence: resistance, fire resistance, absorption
            defender.addPotionEffect(PotionEffect(XPotion.RESISTANCE.potionEffectType!!, 10, 1))
            fakeDefender.doBlocking()
        }
    }

    private fun testEnchantedMelee(armour: Array<ItemStack>, preparations: Runnable) {
        for (weaponType in kernitus.plugin.OldCombatMechanics.utilities.damage.WeaponDamages.getMaterialDamages().keys) {
            val weapon = ItemStack(weaponType)

            // only axe and sword can have sharpness
            try {
                weapon.addEnchantment(XEnchantment.SHARPNESS.get()!!, 3)
            } catch (ignored: IllegalArgumentException) {
            }

            val message = weaponType.name + " Sharpness 3"
            queueAttack(OCMTest(weapon, armour, 2, message) {
                preparations.run()
                defender.maximumNoDamageTicks = 0
                attacker.addPotionEffect(PotionEffect(XPotion.STRENGTH.get()!!, 10, 0, false))
                attacker.addPotionEffect(PotionEffect(XPotion.WEAKNESS.get()!!, 10, -1, false))
                plugin.logger.info("TESTING WEAPON $weaponType")
                attacker.fallDistance = 2f // Crit
            })
        }
    }

    private fun testMelee(armour: Array<ItemStack>, preparations: Runnable) {
        for (weaponType in kernitus.plugin.OldCombatMechanics.utilities.damage.WeaponDamages.getMaterialDamages().keys) {
            val weapon = ItemStack(weaponType)
            queueAttack(OCMTest(weapon, armour, 1, weaponType.name) {
                preparations.run()
                defender.maximumNoDamageTicks = 0
            })
        }
    }

    private fun testOverdamage(armour: Array<ItemStack>, preparations: Runnable) {
        // 1, 5, 6, 7, 3, 8 according to OCM
        // 1, 4, 5, 6, 2, 7 according to 1.9+
        val weapons = arrayOf(
            Material.WOODEN_HOE,
            Material.WOODEN_SWORD,
            Material.STONE_SWORD,
            Material.IRON_SWORD,
            Material.WOODEN_PICKAXE,
            Material.DIAMOND_SWORD
        )

        for (weaponType in weapons) {
            val weapon = ItemStack(weaponType)
            queueAttack(OCMTest(weapon, armour, 3, weaponType.name, Runnable {
                preparations.run()
                defender.maximumNoDamageTicks = 30
            }))
        }
    }

    private fun queueAttack(test: OCMTest) {
        testQueue.add(test)
    }

    private fun calculateAttackDamage(weapon: ItemStack): Double {
        val weaponType = weapon.type
        // Attack components order: (Base + Potion effects, scaled by attack delay) + Critical Hit + (Enchantments, scaled by attack delay)
        // Hurt components order: Overdamage - Armour Effects
        var expectedDamage = getDamage(weaponType)

        // Weakness effect, 1.8: -0.5
        // We ignore the level as there is only one level of weakness potion
        val weaknessAddend = if (attacker.hasPotionEffect(XPotion.WEAKNESS.get()!!)) -0.5 else 0.0

        // Strength effect
        // 1.8: +130% for each strength level
        val strength = attacker.getPotionEffectCompat(XPotion.STRENGTH.get()!!)
        if (strength != null) expectedDamage += (strength.amplifier + 1) * 1.3 * expectedDamage

        expectedDamage += weaknessAddend

        // Take into account damage reduction because of cooldown
        val attackCooldown = defender.attackCooldown
        expectedDamage *= (0.2f + attackCooldown * attackCooldown * 0.8f).toDouble()

        // Critical hit
        if (isCriticalHit1_8(attacker)) {
            expectedDamage *= 1.5
        }

        // Weapon Enchantments
        var sharpnessDamage = getOldSharpnessDamage(weapon.getEnchantmentLevel(XEnchantment.SHARPNESS.get()!!))
        sharpnessDamage *= attackCooldown.toDouble() // Scale by attack cooldown strength
        expectedDamage += sharpnessDamage

        return expectedDamage
    }

    private fun wasFakeOverdamage(weapon: ItemStack): Boolean {
        val weaponDamage = calculateAttackDamage(weapon)
        val lastDamage = defender.lastDamage
        return defender.noDamageTicks.toFloat() > defender.maximumNoDamageTicks.toFloat() / 2.0f &&
                weaponDamage <= lastDamage
    }

    private fun wasOverdamaged(rawWeaponDamage: Double): Boolean {
        val lastDamage = defender.lastDamage
        return defender.noDamageTicks.toFloat() > defender.maximumNoDamageTicks.toFloat() / 2.0f &&
                rawWeaponDamage > lastDamage
    }

    private fun calculateExpectedDamage(weapon: ItemStack, armourContents: Array<ItemStack>): Float {
        var expectedDamage = calculateAttackDamage(weapon)

        if (wasOverdamaged(expectedDamage)) {
            val lastDamage = defender.lastDamage
            plugin.logger.info("Overdamaged: " + expectedDamage + " - " + lastDamage + " = " + (expectedDamage - lastDamage))
            expectedDamage -= lastDamage
        }

        if (defender.isBlocking) {
            plugin.logger.info("DEFENDER IS BLOCKING $expectedDamage")
            expectedDamage -= max(0.0, (expectedDamage - 1)) * 0.5
            plugin.logger.info("AFTER BLOCK $expectedDamage")
        }

        expectedDamage = getDamageAfterArmour1_8(
            defender,
            expectedDamage,
            armourContents,
            EntityDamageEvent.DamageCause.ENTITY_ATTACK,
            false
        )

        return expectedDamage.toFloat()
    }

    private fun runQueuedTests() {
        plugin.logger.info("Running " + testQueue.size + " tests")

        val listener: Listener = object : Listener {
            @EventHandler(priority = EventPriority.MONITOR)
            fun onEvent(e: EntityDamageByEntityEvent) {
                val damager = e.damager
                if (damager.uniqueId !== attacker.uniqueId ||
                    e.entity.uniqueId !== defender.uniqueId
                ) return

                val weapon = (damager as Player).inventory.itemInMainHand
                val weaponType = weapon.type
                var test = testQueue.remove()
                var expectedWeapon = test.weapon
                var expectedDamage = calculateExpectedDamage(expectedWeapon, test.armour)

                while (weaponType != expectedWeapon.type) {
                    expectedDamage = calculateExpectedDamage(expectedWeapon, test.armour)
                    plugin.logger.info("SKIPPED " + expectedWeapon.type + " Expected Damage: " + expectedDamage)
                    if (expectedDamage == 0f) tally!!.passed()
                    else tally!!.failed()
                    test = testQueue.remove()
                    expectedWeapon = test.weapon
                }

                if (wasFakeOverdamage(weapon) && e.isCancelled) {
                    plugin.logger.info("PASSED Fake overdamage " + expectedDamage + " < " + (e.entity as LivingEntity).lastDamage)
                    tally!!.passed()
                } else {
                    val weaponMessage = "E: " + expectedWeapon.type.name + " A: " + weaponType.name
                    assertEquals(
                        expectedDamage, e.finalDamage.toFloat(),
                        tally!!, weaponMessage, sender!!
                    )
                }
            }
        }

        Bukkit.getServer().pluginManager.registerEvents(listener, plugin)

        val testCount = testQueue.size.toLong()

        var attackDelay: Long = 0

        for (test in testQueue) {
            attackDelay += test.attackDelay

            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                beforeEach()
                test.preparations.run()
                preparePlayer(test.weapon)
                attackCompat(attacker, defender)
                afterEach()
            }, attackDelay)
        }

        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            afterAll(testCount)
            EntityDamageByEntityEvent.getHandlerList().unregister(listener)
        }, attackDelay + 1)
    }

    private fun beforeAll() {
        plugin.logger.info("Running before all")
        for (player in listOfNotNull(attacker, defender)) {
            player.gameMode = GameMode.SURVIVAL
            player.maximumNoDamageTicks = 20
            player.noDamageTicks = 0 // remove spawn invulnerability
            player.isInvulnerable = false
        }
    }

    private fun afterAll(testCount: Long) {
        fakeAttacker.removePlayer()
        fakeDefender.removePlayer()

        val missed = testCount - tally!!.total
        val message = String.format(
            "Passed: %d Failed: %d Total: %d Missed: %d",
            tally!!.passed,
            tally!!.failed,
            tally!!.total,
            missed
        )
        plugin.logger.info(message)
    }

    private fun beforeEach() {
        for (player in listOfNotNull(attacker, defender)) {
            player.inventory.clear()
            player.exhaustion = 0f
            player.health = 20.0
        }
    }

    private fun preparePlayer(weapon: ItemStack) {
        if (weapon.hasItemMeta()) {
            val meta = weapon.itemMeta
            val speedModifier = createAttributeModifier(
                name = "speed",
                amount = 1000.0,
                operation = AttributeModifier.Operation.ADD_NUMBER,
                slot = EquipmentSlot.HAND
            )
            val attackSpeedAttribute = XAttribute.ATTACK_SPEED.get()
            if (attackSpeedAttribute != null) {
                addAttributeModifierCompat(meta!!, attackSpeedAttribute, speedModifier)
            }
            weapon.setItemMeta(meta)
        }
        attacker.inventory.setItemInMainHand(weapon)
        attacker.updateInventory()

        val attackDamageAttribute = XAttribute.ATTACK_DAMAGE.get()
        val armourAttribute = XAttribute.ARMOR.get()
        val ai = attackDamageAttribute?.let { attacker.getAttribute(it) }
        val defenderArmour = armourAttribute?.let { defender.getAttribute(it) }

        if (attackDamageAttribute != null && ai != null) {
            getDefaultAttributeModifiersCompat(weapon, EquipmentSlot.HAND, attackDamageAttribute).forEach(
                Consumer { am: AttributeModifier? ->
                    ai.removeModifier(am!!)
                    ai.addModifier(am)
                })
        }

        val armourContents = defender.inventory.armorContents
        plugin.logger.info(
            "Armour: " + Arrays.stream(armourContents).filter { obj: ItemStack? -> Objects.nonNull(obj) }
                .map { `is`: ItemStack -> `is`.type.name }
                .reduce { a: String, b: String -> "$a, $b" }
                .orElse("none")
        )
        for (i in armourContents.indices) {
            val itemStack = armourContents[i] ?: continue
            val type = itemStack.type
            val slot =
                arrayOf(
                    EquipmentSlot.FEET,
                    EquipmentSlot.LEGS,
                    EquipmentSlot.CHEST,
                    EquipmentSlot.HEAD
                )[i]
            if (armourAttribute != null && defenderArmour != null) {
                for (attributeModifier in getDefaultAttributeModifiersCompat(itemStack, slot, armourAttribute)) {
                    defenderArmour.removeModifier(attributeModifier)
                    defenderArmour.addModifier(attributeModifier)
                }
            }
        }
    }

    private fun afterEach() {
        for (player in listOfNotNull(attacker, defender)) {
            player.exhaustion = 0f
            player.health = 20.0
        }
    }
}
