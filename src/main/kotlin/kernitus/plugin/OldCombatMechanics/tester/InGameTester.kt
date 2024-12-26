/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.tester

import kernitus.plugin.OldCombatMechanics.OCMMain
import kernitus.plugin.OldCombatMechanics.tester.TesterUtils.assertEquals
import kernitus.plugin.OldCombatMechanics.utilities.Messenger.debug
import kernitus.plugin.OldCombatMechanics.utilities.Messenger.send
import kernitus.plugin.OldCombatMechanics.utilities.Messenger.sendNoPrefix
import kernitus.plugin.OldCombatMechanics.utilities.damage.DamageUtils.getOldSharpnessDamage
import kernitus.plugin.OldCombatMechanics.utilities.damage.DamageUtils.isCriticalHit1_8
import kernitus.plugin.OldCombatMechanics.utilities.damage.DefenceUtils.getDamageAfterArmour1_8
import kernitus.plugin.OldCombatMechanics.utilities.damage.WeaponDamages.getDamage
import kernitus.plugin.OldCombatMechanics.utilities.damage.WeaponDamages.materialDamages
import kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage.getPlayerData
import kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage.setPlayerData
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.command.CommandSender
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.*
import java.util.function.Consumer
import kotlin.math.max

class InGameTester(private val ocm: OCMMain) {
    private var tally: Tally? = null
    private var sender: CommandSender? = null
    private var attacker: Player? = null
    private var defender: Player? = null
    private var fakeAttacker: FakePlayer? = null
    private var fakeDefender: FakePlayer? = null
    private val testQueue: Queue<OCMTest> = ArrayDeque()

    /**
     * Perform all tests using the two specified players
     */
    fun performTests(sender: CommandSender?, location: Location) {
        this.sender = sender
        fakeAttacker = FakePlayer()
        fakeAttacker!!.spawn(location.add(2.0, 0.0, 0.0))
        fakeDefender = FakePlayer()
        val defenderLocation = location.add(0.0, 0.0, 2.0)
        fakeDefender!!.spawn(defenderLocation)

        attacker = Bukkit.getPlayer(fakeAttacker!!.uuid)
        defender = Bukkit.getPlayer(fakeDefender!!.uuid)

        // Turn defender to face attacker
        defenderLocation.yaw = 180f
        defenderLocation.pitch = 0f
        defender!!.teleport(defenderLocation)

        // modeset of attacker takes precedence
        var playerData = getPlayerData(attacker!!.uniqueId)
        playerData.setModesetForWorld(attacker!!.world.uid, "old")
        setPlayerData(attacker!!.uniqueId, playerData)

        playerData = getPlayerData(defender!!.uniqueId)
        playerData.setModesetForWorld(defender!!.world.uid, "new")
        setPlayerData(defender!!.uniqueId, playerData)

        beforeAll()
        tally = Tally()

        // Queue all tests
        //runAttacks(new ItemStack[]{}, () -> {}); // with no armour
        testArmour()

        //testEnchantedMelee(new ItemStack[]{}, () -> {});

        // Run all tests in the queue
        runQueuedTests()
    }

    private fun runAttacks(armour: Array<ItemStack?>, preparations: Runnable) {
        //testMelee(armour, preparations);
        testEnchantedMelee(armour, preparations)
        testOverdamage(armour, preparations)
    }

    private fun testArmour() {
        val materials = arrayOf("LEATHER", "CHAINMAIL", "GOLDEN", "IRON", "DIAMOND", "NETHERITE")
        val slots = arrayOf("BOOTS", "LEGGINGS", "CHESTPLATE", "HELMET")
        val random = Random(System.currentTimeMillis())

        val armourContents = arrayOfNulls<ItemStack>(4)
        for (i in slots.indices) {
            val slot = slots[i]
            // Pick a random material for each slot
            val material = materials[random.nextInt(6)]

            val itemStack = ItemStack(Material.valueOf(material + "_" + slot))

            // Apply enchantment to armour piece
            itemStack.addUnsafeEnchantment(Enchantment.PROTECTION_ENVIRONMENTAL, 50)

            armourContents[i] = itemStack
        }

        runAttacks(armourContents) {
            defender!!.inventory.armorContents = armourContents
            // Test status effects on defence: resistance, fire resistance, absorption
            defender!!.addPotionEffect(PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 10, 1))
            fakeDefender!!.doBlocking()
        }
    }

    private fun testEnchantedMelee(armour: Array<ItemStack?>, preparations: Runnable) {
        for (weaponType in materialDamages.keys) {
            val weapon = ItemStack(weaponType)

            // only axe and sword can have sharpness
            try {
                weapon.addEnchantment(Enchantment.DAMAGE_ALL, 3)
            } catch (ignored: IllegalArgumentException) {
            }

            val message = weaponType.name + " Sharpness 3"
            queueAttack(OCMTest(weapon, armour, 2, message, Runnable {
                preparations.run()
                defender!!.maximumNoDamageTicks = 0
                attacker!!.addPotionEffect(PotionEffect(PotionEffectType.INCREASE_DAMAGE, 10, 0, false))
                attacker!!.addPotionEffect(PotionEffect(PotionEffectType.WEAKNESS, 10, -1, false))
                debug("TESTING WEAPON $weaponType")
                attacker!!.fallDistance = 2f // Crit
            }))
        }
    }

    private fun testMelee(armour: Array<ItemStack>, preparations: Runnable) {
        for (weaponType in materialDamages.keys) {
            val weapon = ItemStack(weaponType)
            queueAttack(OCMTest(weapon, armour, 1, weaponType.name) {
                preparations.run()
                defender!!.maximumNoDamageTicks = 0
            })
        }
    }

    private fun testOverdamage(armour: Array<ItemStack?>, preparations: Runnable) {
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
                defender!!.maximumNoDamageTicks = 30
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
        val weaknessAddend = if (attacker!!.hasPotionEffect(PotionEffectType.WEAKNESS)) -0.5 else 0.0

        // Strength effect
        // 1.8: +130% for each strength level
        val strength = attacker!!.getPotionEffect(PotionEffectType.INCREASE_DAMAGE)
        if (strength != null) expectedDamage += (strength.amplifier + 1) * 1.3 * expectedDamage

        expectedDamage += weaknessAddend

        // Take into account damage reduction because of cooldown
        val attackCooldown = defender!!.attackCooldown
        expectedDamage *= (0.2f + attackCooldown * attackCooldown * 0.8f).toDouble()

        // Critical hit
        if (isCriticalHit1_8(attacker!!)) {
            expectedDamage *= 1.5
        }

        // Weapon Enchantments
        var sharpnessDamage = getOldSharpnessDamage(weapon.getEnchantmentLevel(Enchantment.DAMAGE_ALL))
        sharpnessDamage *= attackCooldown.toDouble() // Scale by attack cooldown strength
        expectedDamage += sharpnessDamage

        return expectedDamage
    }

    private fun wasFakeOverdamage(weapon: ItemStack): Boolean {
        val weaponDamage = calculateAttackDamage(weapon)
        val lastDamage = defender!!.lastDamage
        return defender!!.noDamageTicks.toFloat() > defender!!.maximumNoDamageTicks.toFloat() / 2.0f &&
                weaponDamage <= lastDamage
    }

    private fun wasOverdamaged(rawWeaponDamage: Double): Boolean {
        val lastDamage = defender!!.lastDamage
        return defender!!.noDamageTicks.toFloat() > defender!!.maximumNoDamageTicks.toFloat() / 2.0f &&
                rawWeaponDamage > lastDamage
    }

    private fun calculateExpectedDamage(weapon: ItemStack, armourContents: Array<ItemStack?>): Float {
        var expectedDamage = calculateAttackDamage(weapon)

        // Overdamage
        if (wasOverdamaged(expectedDamage)) {
            val lastDamage = defender!!.lastDamage
            send(
                sender!!,
                "Overdamaged: " + expectedDamage + " - " + lastDamage + " = " + (expectedDamage - lastDamage)
            )
            debug("Overdamaged: " + expectedDamage + " - " + lastDamage + " = " + (expectedDamage - lastDamage))
            expectedDamage -= lastDamage
        }

        // BASE -> HARD_HAT -> BLOCKING -> ARMOUR -> RESISTANCE -> MAGIC -> ABSORPTION

        // Blocking
        //1.8 default: (damage - 1) * 50%  1.9 default: 33%   1.11 default: 100%
        if (defender!!.isBlocking) {
            debug("DEFENDER IS BLOCKING $expectedDamage")
            //expectedDamage = (1.0F + expectedDamage) * 0.5F;
            expectedDamage -= max(0.0, (expectedDamage - 1)) * 0.5
            debug("AFTER BLOCC $expectedDamage")
        }

        // Armour, resistance, armour enchants (1.8, with OldArmourStrength module)
        expectedDamage = getDamageAfterArmour1_8(
            defender!!,
            expectedDamage,
            armourContents,
            EntityDamageEvent.DamageCause.ENTITY_ATTACK,
            false
        )

        /* 1.8 NMS
        float f1 = f;
        f = Math.max(f - this.getAbsorptionHearts(), 0.0F);
        this.setAbsorptionHearts(this.getAbsorptionHearts() - (f1 - f));
        if (f != 0.0F) {
            this.applyExhaustion(damagesource.getExhaustionCost());
            float f2 = this.getHealth();

            this.setHealth(this.getHealth() - f);
            this.bs().a(damagesource, f2, f);
            if (f < 3.4028235E37F) {
                this.a(StatisticList.x, Math.round(f * 10.0F));
            }
        }
         */
        return expectedDamage.toFloat()
    }

    private fun runQueuedTests() {
        send(sender!!, "Running " + testQueue.size + " tests")

        // Listener gets called every time defender is damaged
        val listener: Listener = object : Listener {
            @EventHandler(priority = EventPriority.MONITOR)
            fun onEvent(e: EntityDamageByEntityEvent) {
                val damager = e.damager
                if (damager.uniqueId !== attacker!!.uniqueId ||
                    e.entity.uniqueId !== defender!!.uniqueId
                ) return

                val weapon = (damager as Player).inventory.itemInMainHand
                val weaponType = weapon.type
                var test = testQueue.remove()
                var expectedWeapon = test.weapon
                var expectedDamage = calculateExpectedDamage(expectedWeapon, test.armour)

                while (weaponType != expectedWeapon.type) { // One of the attacks dealt no damage
                    expectedDamage = calculateExpectedDamage(expectedWeapon, test.armour)
                    send(sender!!, "&bSKIPPED &f" + expectedWeapon.type + " &fExpected Damage: &b" + expectedDamage)
                    if (expectedDamage == 0f) tally!!.passed()
                    else tally!!.failed()
                    test = testQueue.remove()
                    expectedWeapon = test.weapon
                }


                if (wasFakeOverdamage(weapon) && e.isCancelled) {
                    send(
                        sender!!,
                        "&aPASSED &fFake overdamage " + expectedDamage + " < " + (e.entity as LivingEntity).lastDamage
                    )
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

        Bukkit.getServer().pluginManager.registerEvents(listener, ocm)

        val testCount = testQueue.size.toLong()

        // Cumulative attack delay for scheduling all the tests
        var attackDelay: Long = 0

        for (test in testQueue) {
            attackDelay += test.attackDelay

            Bukkit.getScheduler().runTaskLater(ocm, Runnable {
                beforeEach()
                test.preparations.run()
                preparePlayer(test.weapon)
                attacker!!.attack(defender!!)
                afterEach()
            }, attackDelay)
        }

        Bukkit.getScheduler().runTaskLater(ocm, Runnable {
            afterAll(testCount)
            EntityDamageByEntityEvent.getHandlerList().unregister(listener)
        }, attackDelay + 1)
    }

    private fun beforeAll() {
        for (player in arrayOf<Player>(attacker, defender)) {
            player.gameMode = GameMode.SURVIVAL
            player.maximumNoDamageTicks = 20
            player.noDamageTicks = 0 // remove spawn invulnerability
            player.isInvulnerable = false
        }
    }

    private fun afterAll(testCount: Long) {
        fakeAttacker!!.removePlayer()
        fakeDefender!!.removePlayer()

        val missed = testCount - tally!!.total
        sendNoPrefix(
            sender!!,
            "Passed: &a%d &rFailed: &c%d &rTotal: &7%d &rMissed: &7%d",
            tally!!.passed,
            tally!!.failed,
            tally!!.total,
            missed
        )
    }

    private fun beforeEach() {
        for (player in arrayOf<Player>(attacker, defender)) {
            player.inventory.clear()
            player.exhaustion = 0f
            player.health = 20.0
        }
    }

    private fun preparePlayer(weapon: ItemStack) {
        if (weapon.hasItemMeta()) {
            val meta = weapon.itemMeta
            meta!!.addAttributeModifier(
                Attribute.GENERIC_ATTACK_SPEED,
                AttributeModifier(
                    UUID.randomUUID(), "speed", 1000.0,
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.HAND
                )
            )
            weapon.setItemMeta(meta)
        }
        attacker!!.inventory.setItemInMainHand(weapon)
        attacker!!.updateInventory()

        // Update attack attribute cause it won't get done with fake players
        val ai = attacker!!.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE)
        val defenderArmour = defender!!.getAttribute(Attribute.GENERIC_ARMOR)

        weapon.type.getDefaultAttributeModifiers(EquipmentSlot.HAND)[Attribute.GENERIC_ATTACK_DAMAGE].forEach(
            Consumer { am: AttributeModifier? ->
                ai!!.removeModifier(am!!)
                ai.addModifier(am)
            })

        // Update armour attribute
        val armourContents = defender!!.inventory.armorContents
        debug("Armour: " + Arrays.stream(armourContents).filter { obj: ItemStack? -> Objects.nonNull(obj) }
            .map { `is`: ItemStack -> `is`.type.name }.reduce { a: String, b: String -> "$a, $b" }.orElse("none"))
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
            for (attributeModifier in type.getDefaultAttributeModifiers(slot)[Attribute.GENERIC_ARMOR]) {
                defenderArmour!!.removeModifier(attributeModifier)
                defenderArmour.addModifier(attributeModifier)
            }
        }
    }

    private fun afterEach() {
        for (player in arrayOf<Player>(attacker, defender)) {
            player.exhaustion = 0f
            player.health = 20.0
        }
    }
}
