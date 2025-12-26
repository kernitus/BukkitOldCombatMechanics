/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics

import kernitus.plugin.OldCombatMechanics.module.ModuleGoldenApple
import kernitus.plugin.OldCombatMechanics.utilities.Config
import kernitus.plugin.OldCombatMechanics.utilities.damage.WeaponDamages
import com.cryptomorin.xseries.XMaterial
import com.cryptomorin.xseries.XPotion
import kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage.getPlayerData
import kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage.setPlayerData
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.attribute.Attribute
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.inventory.CraftingInventory
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.lang.reflect.Constructor
import java.util.logging.Level

object LegacyTestRunner {
    @JvmStatic
    fun run(plugin: JavaPlugin) {
        plugin.logger.info("Running legacy integration tests (Java 8 runtime).")
        Bukkit.getScheduler().runTask(plugin, Runnable {
            LegacyTestSuite(plugin).start()
        })
    }

    private class LegacyTestSuite(private val plugin: JavaPlugin) {
        private var failures = 0
        private var total = 0
        private var delayTicks = 0L

        private var meleeAttacker: FakePlayer? = null
        private var meleeDefender: FakePlayer? = null
        private var meleeAttackerPlayer: Player? = null
        private var meleeDefenderPlayer: Player? = null

        private var singleFake: FakePlayer? = null
        private var singlePlayer: Player? = null
        private var craftingInventory: CraftingInventory? = null

        private val ocm = Bukkit.getPluginManager().getPlugin("OldCombatMechanics") as? OCMMain
        private val module = ModuleGoldenApple.getInstance()

        fun start() {
            if (ocm == null || !ocm.isEnabled) {
                fail("OldCombatMechanics plugin is not enabled.")
                finish()
                return
            }

            schedule("sanity") {
                assertTrue(WeaponDamages.getDamage(Material.STONE_SWORD) > 0, "Weapon damages loaded")
            }

            spawnMeleePlayers()
            schedule("melee attack") { runMeleeAttack() }
            schedule(1, "melee assert") { assertMeleeAttack() }
            schedule("remove melee players") { removeMeleePlayers() }

            spawnSinglePlayer()
            schedule("sword blocking") { runSwordBlockingTest() }

            addGoldenAppleTests()

            schedule("cleanup single player") { removeSinglePlayer() }
            schedule("finish") { finish() }
        }

        private fun schedule(name: String, action: () -> Unit) = schedule(0, name, action)

        private fun schedule(delay: Long, name: String, action: () -> Unit) {
            delayTicks += delay
            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                try {
                    action()
                } catch (e: Throwable) {
                    fail("$name threw ${e.javaClass.simpleName}: ${e.message}")
                }
            }, delayTicks)
        }

        private fun finish() {
            val success = failures == 0
            plugin.logger.info("Legacy test summary: passed ${total - failures}/$total")
            TestResultWriter.writeAndShutdown(plugin, success)
        }

        private fun assertTrue(condition: Boolean, message: String) {
            total++
            if (condition) {
                plugin.logger.info("PASS: $message")
            } else {
                fail(message)
            }
        }

        private fun fail(message: String) {
            failures++
            plugin.logger.severe("FAIL: $message")
        }

        private fun spawnMeleePlayers() {
            val world = Bukkit.getServer().getWorld("world")
            if (world == null) {
                fail("World 'world' is not loaded.")
                return
            }
            val baseLocation = Location(world, 0.0, 100.0, 0.0)
            meleeAttacker = FakePlayer(plugin).also { it.spawn(baseLocation.clone().add(2.0, 0.0, 0.0)) }
            meleeDefender = FakePlayer(plugin).also { it.spawn(baseLocation.clone().add(0.0, 0.0, 2.0)) }
            meleeAttackerPlayer = Bukkit.getPlayer(meleeAttacker!!.uuid)
            meleeDefenderPlayer = Bukkit.getPlayer(meleeDefender!!.uuid)
            if (meleeAttackerPlayer == null || meleeDefenderPlayer == null) {
                fail("Failed to resolve melee players.")
                return
            }
            preparePlayers(meleeAttackerPlayer!!, meleeDefenderPlayer!!)
            applyModesets(meleeAttackerPlayer!!, meleeDefenderPlayer!!)
        }

        private fun runMeleeAttack() {
            val attacker = meleeAttackerPlayer ?: return
            val defender = meleeDefenderPlayer ?: return
            attacker.inventory.setItemInMainHand(ItemStack(Material.STONE_SWORD))
            defender.maximumNoDamageTicks = 0
            val attackMethod = attacker.javaClass.methods.firstOrNull { method ->
                method.name == "attack" &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0].isAssignableFrom(defender.javaClass)
            }
            if (attackMethod != null) {
                attackMethod.invoke(attacker, defender)
            } else {
                defender.damage(1.0, attacker)
            }
        }

        private fun assertMeleeAttack() {
            val attacker = meleeAttackerPlayer ?: return
            @Suppress("DEPRECATION") // Deprecated API kept for older server compatibility in tests.
            assertTrue(attacker.health == attacker.maxHealth, "Melee attack does not hurt attacker")
        }

        private fun removeMeleePlayers() {
            meleeAttacker?.removePlayer()
            meleeDefender?.removePlayer()
            meleeAttacker = null
            meleeDefender = null
            meleeAttackerPlayer = null
            meleeDefenderPlayer = null
        }

        private fun spawnSinglePlayer() {
            val world = Bukkit.getServer().getWorld("world")
            if (world == null) {
                fail("World 'world' is not loaded.")
                return
            }
            val location = Location(world, 0.0, 100.0, 0.0)
            singleFake = FakePlayer(plugin).also { it.spawn(location) }
            singlePlayer = Bukkit.getPlayer(singleFake!!.uuid)
            if (singlePlayer == null) {
                fail("Failed to resolve single player.")
                return
            }
            preparePlayers(singlePlayer!!)
            singlePlayer!!.isOp = true
        }

        private fun removeSinglePlayer() {
            singleFake?.removePlayer()
            singleFake = null
            singlePlayer = null
        }

        private fun runSwordBlockingTest() {
            val player = singlePlayer ?: return
            resetPlayerState(player)
            player.inventory.setItemInMainHand(ItemStack(Material.DIAMOND_SWORD))
            callInteractEvent(player, Action.RIGHT_CLICK_AIR, player.inventory.itemInMainHand)
            assertTrue(player.inventory.itemInOffHand.type == Material.SHIELD, "Sword blocking: shield in offhand")
            singleFake?.doBlocking()
            assertTrue(player.isBlocking || player.isHandRaised, "Sword blocking: player blocking or hand raised")
        }

        private fun addGoldenAppleTests() {
            val player = singlePlayer ?: return
            val snapshot = snapshotConfig()
            val modesetSnapshot = snapshotModeset("new")
            removeModuleFromModeset("new", "old-golden-apples")

            schedule("gapple effects setup") {
                resetPlayerState(player)
                applyConfig(snapshot)
                player.inventory.setItemInMainHand(ItemStack(Material.GOLDEN_APPLE))
                callConsumeEvent(player, player.inventory.itemInMainHand)
            }
            schedule(2, "gapple effects assert") {
                assertPotion(player.getPotionEffect(PotionEffectType.REGENERATION), 5 * 20, 1, "Gapple regen")
                assertPotion(player.getPotionEffect(PotionEffectType.ABSORPTION), 120 * 20, 0, "Gapple absorption")
            }

            schedule("napple effects setup") {
                resetPlayerState(player)
                applyConfig(snapshot)
                player.inventory.setItemInMainHand(requireNotNull(XMaterial.ENCHANTED_GOLDEN_APPLE.parseItem()))
                callConsumeEvent(player, player.inventory.itemInMainHand)
            }
            schedule(2, "napple effects assert") {
                assertPotion(player.getPotionEffect(PotionEffectType.REGENERATION), 30 * 20, 4, "Napple regen")
                assertPotion(player.getPotionEffect(PotionEffectType.ABSORPTION), 120 * 20, 0, "Napple absorption")
                assertPotion(player.getPotionEffect(XPotion.RESISTANCE.get()!!), 300 * 20, 0, "Napple resistance")
                assertPotion(player.getPotionEffect(PotionEffectType.FIRE_RESISTANCE), 300 * 20, 0, "Napple fire resistance")
            }

            schedule("higher amplifier setup") {
                resetPlayerState(player)
                applyConfig(snapshot)
                player.addPotionEffect(PotionEffect(PotionEffectType.REGENERATION, 100, 0))
                player.inventory.setItemInMainHand(ItemStack(Material.GOLDEN_APPLE))
                callConsumeEvent(player, player.inventory.itemInMainHand)
            }
            schedule(4, "higher amplifier assert") {
                assertPotion(player.getPotionEffect(PotionEffectType.REGENERATION), 5 * 20, 1, "Higher amplifier regen")
            }

            schedule("same amplifier setup") {
                resetPlayerState(player)
                applyConfig(snapshot)
                player.addPotionEffect(PotionEffect(PotionEffectType.REGENERATION, 50, 1))
                player.inventory.setItemInMainHand(ItemStack(Material.GOLDEN_APPLE))
                callConsumeEvent(player, player.inventory.itemInMainHand)
            }
            schedule(4, "same amplifier assert") {
                assertPotion(player.getPotionEffect(PotionEffectType.REGENERATION), 5 * 20, 1, "Same amplifier regen")
            }

            schedule("cooldown normal setup") {
                resetPlayerState(player)
                applyConfig(snapshot)
                ocm!!.config.set("old-golden-apples.cooldown.normal", 2)
                module.reload()
                player.inventory.setItemInMainHand(ItemStack(Material.GOLDEN_APPLE))
                val event1 = callConsumeEvent(player, player.inventory.itemInMainHand)
                assertTrue(!event1.isCancelled, "Gapple cooldown: first consume allowed")
                val event2 = callConsumeEvent(player, player.inventory.itemInMainHand)
                assertTrue(event2.isCancelled, "Gapple cooldown: second consume blocked")
            }
            schedule(secondsToTicks(2) + 2, "cooldown normal assert") {
                player.inventory.setItemInMainHand(ItemStack(Material.GOLDEN_APPLE))
                val event3 = callConsumeEvent(player, player.inventory.itemInMainHand)
                assertTrue(!event3.isCancelled, "Gapple cooldown: expires")
                applyConfig(snapshot)
            }

            schedule("shared cooldown setup") {
                resetPlayerState(player)
                applyConfig(snapshot)
                ocm!!.config.set("old-golden-apples.cooldown.normal", 5)
                ocm.config.set("old-golden-apples.cooldown.enchanted", 10)
                ocm.config.set("old-golden-apples.cooldown.is-shared", true)
                module.reload()
                player.inventory.setItemInMainHand(ItemStack(Material.GOLDEN_APPLE))
                val gappleEvent = callConsumeEvent(player, player.inventory.itemInMainHand)
                assertTrue(!gappleEvent.isCancelled, "Shared cooldown: gapple allowed")
                player.inventory.setItemInMainHand(requireNotNull(XMaterial.ENCHANTED_GOLDEN_APPLE.parseItem()))
                val nappleEvent = callConsumeEvent(player, player.inventory.itemInMainHand)
                assertTrue(nappleEvent.isCancelled, "Shared cooldown: napple blocked")
                applyConfig(snapshot)
            }

            schedule("non-shared cooldown setup") {
                resetPlayerState(player)
                applyConfig(snapshot)
                ocm!!.config.set("old-golden-apples.cooldown.normal", 5)
                ocm.config.set("old-golden-apples.cooldown.enchanted", 10)
                ocm.config.set("old-golden-apples.cooldown.is-shared", false)
                module.reload()
                player.inventory.setItemInMainHand(ItemStack(Material.GOLDEN_APPLE))
                val gappleEvent = callConsumeEvent(player, player.inventory.itemInMainHand)
                assertTrue(!gappleEvent.isCancelled, "Non-shared cooldown: gapple allowed")
                player.inventory.setItemInMainHand(requireNotNull(XMaterial.ENCHANTED_GOLDEN_APPLE.parseItem()))
                val nappleEvent = callConsumeEvent(player, player.inventory.itemInMainHand)
                assertTrue(!nappleEvent.isCancelled, "Non-shared cooldown: napple allowed")
                applyConfig(snapshot)
            }

            schedule(2, "module disabled via modeset setup") {
                resetPlayerState(player)
                applyConfig(snapshot)
                ocm!!.config.set("old-golden-apples.golden-apple-effects.regeneration.duration", 99)
                module.reload()
                setModeset(player, "new")
                player.inventory.setItemInMainHand(ItemStack(Material.GOLDEN_APPLE))
                callConsumeEvent(player, player.inventory.itemInMainHand, applyVanillaEffects = true)
            }
            schedule(2, "module disabled via modeset assert") {
                assertPotion(player.getPotionEffect(PotionEffectType.REGENERATION), 5 * 20, 1, "Modeset disables module")
                setModeset(player, "old")
                applyConfig(snapshot)
            }

            schedule("old-potion-effects disabled setup") {
                resetPlayerState(player)
                applyConfig(snapshot)
                ocm!!.config.set("old-golden-apples.old-potion-effects", false)
                ocm.config.set("old-golden-apples.golden-apple-effects.regeneration.duration", 99)
                module.reload()
                player.inventory.setItemInMainHand(ItemStack(Material.GOLDEN_APPLE))
                callConsumeEvent(player, player.inventory.itemInMainHand, applyVanillaEffects = true)
            }
            schedule(2, "old-potion-effects disabled assert") {
                assertPotion(player.getPotionEffect(PotionEffectType.REGENERATION), 5 * 20, 1, "Old potion effects disabled")
                applyConfig(snapshot)
            }

            schedule("crafting enabled setup") {
                resetPlayerState(player)
                applyConfig(snapshot)
                ocm!!.config.set("old-golden-apples.enchanted-golden-apple-crafting", true)
                module.reload()
                setModeset(player, "old")
                craftingInventory = openCraftingInventory(player)
                if (craftingInventory == null) {
                    fail("Crafting inventory not available")
                    return@schedule
                }
                prepareCraftingGrid(craftingInventory!!)
            }
            schedule(2, "crafting enabled assert") {
                val inv = craftingInventory
                if (inv == null) {
                    fail("Crafting inventory missing for enabled assert")
                } else {
                    assertTrue(inv.result != null && XMaterial.ENCHANTED_GOLDEN_APPLE.isSimilar(inv.result!!), "Crafting enabled")
                }
                player.closeInventory()
                craftingInventory = null
            }

            schedule("crafting disabled config setup") {
                resetPlayerState(player)
                applyConfig(snapshot)
                ocm!!.config.set("old-golden-apples.enchanted-golden-apple-crafting", false)
                module.reload()
                setModeset(player, "old")
                craftingInventory = openCraftingInventory(player)
                if (craftingInventory == null) {
                    fail("Crafting inventory not available")
                    return@schedule
                }
                prepareCraftingGrid(craftingInventory!!)
            }
            schedule(2, "crafting disabled config assert") {
                val inv = craftingInventory
                if (inv == null) {
                    fail("Crafting inventory missing for disabled config assert")
                } else {
                    assertTrue(inv.result == null, "Crafting disabled in config")
                }
                player.closeInventory()
                craftingInventory = null
                applyConfig(snapshot)
            }

            schedule("crafting disabled modeset setup") {
                resetPlayerState(player)
                applyConfig(snapshot)
                ocm!!.config.set("old-golden-apples.enchanted-golden-apple-crafting", true)
                module.reload()
                setModeset(player, "new")
                craftingInventory = openCraftingInventory(player)
                if (craftingInventory == null) {
                    fail("Crafting inventory not available")
                    return@schedule
                }
                prepareCraftingGrid(craftingInventory!!)
            }
            schedule(2, "crafting disabled modeset assert") {
                val inv = craftingInventory
                if (inv == null) {
                    fail("Crafting inventory missing for disabled modeset assert")
                } else {
                    assertTrue(inv.result == null, "Crafting disabled by modeset")
                }
                player.closeInventory()
                craftingInventory = null
                setModeset(player, "old")
                applyConfig(snapshot)
            }

            schedule("no-conflict-mode setup") {
                resetPlayerState(player)
                applyConfig(snapshot)
                ocm!!.config.set("old-golden-apples.enchanted-golden-apple-crafting", true)
                ocm.config.set("old-golden-apples.no-conflict-mode", true)
                module.reload()
                setModeset(player, "new")
                craftingInventory = openCraftingInventory(player)
                if (craftingInventory == null) {
                    fail("Crafting inventory not available")
                    return@schedule
                }
                prepareCraftingGrid(craftingInventory!!)
            }
            schedule(2, "no-conflict-mode assert") {
                val inv = craftingInventory
                if (inv == null) {
                    fail("Crafting inventory missing for no-conflict assert")
                } else {
                    assertTrue(inv.result != null && XMaterial.ENCHANTED_GOLDEN_APPLE.isSimilar(inv.result!!), "No-conflict mode allows crafting")
                }
                player.closeInventory()
                craftingInventory = null
                setModeset(player, "old")
                applyConfig(snapshot)
            }

            schedule("getGappleCooldown setup") {
                resetPlayerState(player)
                applyConfig(snapshot)
                ocm!!.config.set("old-golden-apples.cooldown.normal", 10)
                module.reload()
                player.inventory.setItemInMainHand(ItemStack(Material.GOLDEN_APPLE))
                callConsumeEvent(player, player.inventory.itemInMainHand)
            }
            schedule(2, "getGappleCooldown assert 1") {
                val cd = module.getGappleCooldown(player.uniqueId)
                assertTrue(cd in 9..10, "getGappleCooldown initial")
            }
            schedule(secondsToTicks(5), "getGappleCooldown assert 2") {
                val cd = module.getGappleCooldown(player.uniqueId)
                assertTrue(cd in 4..5, "getGappleCooldown mid")
            }
            schedule(secondsToTicks(5), "getGappleCooldown assert 3") {
                val cd = module.getGappleCooldown(player.uniqueId)
                assertTrue(cd == 0L, "getGappleCooldown end")
                applyConfig(snapshot)
            }

            schedule("getNappleCooldown setup") {
                resetPlayerState(player)
                applyConfig(snapshot)
                ocm!!.config.set("old-golden-apples.cooldown.enchanted", 20)
                module.reload()
                player.inventory.setItemInMainHand(requireNotNull(XMaterial.ENCHANTED_GOLDEN_APPLE.parseItem()))
                callConsumeEvent(player, player.inventory.itemInMainHand)
            }
            schedule(2, "getNappleCooldown assert 1") {
                val cd = module.getNappleCooldown(player.uniqueId)
                assertTrue(cd in 19..20, "getNappleCooldown initial")
            }
            schedule(secondsToTicks(10), "getNappleCooldown assert 2") {
                val cd = module.getNappleCooldown(player.uniqueId)
                assertTrue(cd in 9..10, "getNappleCooldown mid")
            }
            schedule(secondsToTicks(10), "getNappleCooldown assert 3") {
                val cd = module.getNappleCooldown(player.uniqueId)
                assertTrue(cd == 0L, "getNappleCooldown end")
                applyConfig(snapshot)
            }

            schedule("restore modesets") {
                restoreModeset("new", modesetSnapshot)
            }
        }

        private fun snapshotConfig(): GappleConfigSnapshot {
            val config = ocm!!.config
            return GappleConfigSnapshot(
                config.getBoolean("old-golden-apples.old-potion-effects"),
                config.getLong("old-golden-apples.cooldown.normal"),
                config.getLong("old-golden-apples.cooldown.enchanted"),
                config.getBoolean("old-golden-apples.cooldown.is-shared"),
                config.getBoolean("old-golden-apples.enchanted-golden-apple-crafting"),
                config.getBoolean("old-golden-apples.no-conflict-mode"),
                config.getInt("old-golden-apples.golden-apple-effects.regeneration.duration")
            )
        }

        private fun applyConfig(snapshot: GappleConfigSnapshot) {
            val config = ocm!!.config
            config.set("old-golden-apples.old-potion-effects", snapshot.oldPotionEffects)
            config.set("old-golden-apples.cooldown.normal", snapshot.normalCooldown)
            config.set("old-golden-apples.cooldown.enchanted", snapshot.enchantedCooldown)
            config.set("old-golden-apples.cooldown.is-shared", snapshot.sharedCooldown)
            config.set("old-golden-apples.enchanted-golden-apple-crafting", snapshot.craftingEnabled)
            config.set("old-golden-apples.no-conflict-mode", snapshot.noConflictMode)
            config.set("old-golden-apples.golden-apple-effects.regeneration.duration", snapshot.regenDuration)
            module.reload()
        }

        private fun resetPlayerState(player: Player) {
            player.inventory.clear()
            player.activePotionEffects.forEach { player.removePotionEffect(it.type) }
            val maxAttribute = resolveMaxHealthAttribute()
            val maxHealth = if (maxAttribute != null) {
                player.getAttribute(maxAttribute)?.value ?: 20.0
            } else {
                20.0
            }
            player.health = maxHealth
            player.foodLevel = 20
            setModeset(player, "old")
            player.isInvulnerable = false
        }

        private fun resolveMaxHealthAttribute(): Attribute? {
            return runCatching { Attribute.valueOf("MAX_HEALTH") }
                .getOrElse { runCatching { Attribute.valueOf("GENERIC_MAX_HEALTH") }.getOrNull() }
        }

        private fun preparePlayers(vararg players: Player) {
            for (player in players) {
                player.gameMode = GameMode.SURVIVAL
                player.maximumNoDamageTicks = 20
                player.noDamageTicks = 0
                player.isInvulnerable = false
            }
        }

        private fun applyModesets(attacker: Player, defender: Player) {
            var playerData = getPlayerData(attacker.uniqueId)
            playerData.setModesetForWorld(attacker.world.uid, "old")
            setPlayerData(attacker.uniqueId, playerData)

            playerData = getPlayerData(defender.uniqueId)
            playerData.setModesetForWorld(defender.world.uid, "new")
            setPlayerData(defender.uniqueId, playerData)
        }

        private fun setModeset(player: Player, modeset: String) {
            val playerData = getPlayerData(player.uniqueId)
            playerData.setModesetForWorld(player.world.uid, modeset)
            setPlayerData(player.uniqueId, playerData)
        }

        private fun snapshotModeset(name: String): Set<String> {
            val modesets = Config.getModesets()
            val existing = modesets[name] ?: emptySet()
            return HashSet(existing)
        }

        private fun removeModuleFromModeset(name: String, moduleName: String) {
            val modesets = Config.getModesets()
            val set = modesets[name] ?: return
            set.remove(moduleName)
        }

        private fun restoreModeset(name: String, snapshot: Set<String>) {
            val modesets = Config.getModesets()
            val set = modesets[name] ?: return
            set.clear()
            set.addAll(snapshot)
        }

        private fun assertPotion(effect: PotionEffect?, duration: Int, amplifier: Int, label: String) {
            assertTrue(effect != null, "$label present")
            if (effect != null) {
                val minDuration = duration - 3
                assertTrue(effect.duration in minDuration..duration, "$label duration (got ${effect.duration})")
                assertTrue(effect.amplifier == amplifier, "$label amplifier (got ${effect.amplifier})")
            }
        }

        private fun prepareCraftingGrid(inventory: CraftingInventory) {
            inventory.matrix = arrayOf(
                ItemStack(Material.GOLD_BLOCK), ItemStack(Material.GOLD_BLOCK), ItemStack(Material.GOLD_BLOCK),
                ItemStack(Material.GOLD_BLOCK), ItemStack(Material.APPLE), ItemStack(Material.GOLD_BLOCK),
                ItemStack(Material.GOLD_BLOCK), ItemStack(Material.GOLD_BLOCK), ItemStack(Material.GOLD_BLOCK)
            )
        }

        private fun openCraftingInventory(player: Player): CraftingInventory? {
            return runCatching {
                val method = player.javaClass.methods.firstOrNull { candidate ->
                    candidate.name == "openWorkbench" && candidate.parameterTypes.size == 2
                } ?: return null
                val view = method.invoke(player, null, true) ?: return null
                val topInventory = view.javaClass.getMethod("getTopInventory").invoke(view)
                topInventory as? CraftingInventory
            }.onFailure { throwable ->
                plugin.logger.log(Level.WARNING, "Failed to open crafting inventory: ${throwable.message}")
            }.getOrNull()
        }

        private fun applyVanillaEffects(player: Player, item: ItemStack) {
            val isEnchanted = XMaterial.ENCHANTED_GOLDEN_APPLE.isSimilar(item)
            if (item.type != Material.GOLDEN_APPLE && !isEnchanted) return

            if (isEnchanted) {
                player.addPotionEffect(PotionEffect(PotionEffectType.REGENERATION, 30 * 20, 1))
                player.addPotionEffect(PotionEffect(PotionEffectType.ABSORPTION, 120 * 20, 3))
                player.addPotionEffect(PotionEffect(XPotion.RESISTANCE.get()!!, 300 * 20, 0))
                player.addPotionEffect(PotionEffect(PotionEffectType.FIRE_RESISTANCE, 300 * 20, 0))
            } else {
                player.addPotionEffect(PotionEffect(PotionEffectType.REGENERATION, 5 * 20, 1))
                player.addPotionEffect(PotionEffect(PotionEffectType.ABSORPTION, 120 * 20, 0))
            }
        }

        private fun callConsumeEvent(
            player: Player,
            item: ItemStack,
            applyVanillaEffects: Boolean = false
        ): PlayerItemConsumeEvent {
            val ctor = findConsumeCtor()
            val event = if (ctor.parameterTypes.size == 3) {
                ctor.newInstance(player, item, EquipmentSlot.HAND) as PlayerItemConsumeEvent
            } else {
                ctor.newInstance(player, item) as PlayerItemConsumeEvent
            }
            Bukkit.getPluginManager().callEvent(event)
            if (!event.isCancelled && applyVanillaEffects) {
                applyVanillaEffects(player, item)
            }
            return event
        }

        private fun callInteractEvent(player: Player, action: Action, item: ItemStack): PlayerInteractEvent {
            val ctor = findInteractCtor()
            val event = if (ctor.parameterTypes.size == 6) {
                ctor.newInstance(player, action, item, null, BlockFace.SELF, EquipmentSlot.HAND) as PlayerInteractEvent
            } else {
                ctor.newInstance(player, action, item, null, BlockFace.SELF) as PlayerInteractEvent
            }
            Bukkit.getPluginManager().callEvent(event)
            return event
        }

        private fun findConsumeCtor(): Constructor<*> {
            val ctors = PlayerItemConsumeEvent::class.java.constructors
            return ctors.firstOrNull { it.parameterTypes.size == 3 }
                ?: ctors.firstOrNull { it.parameterTypes.size == 2 }
                ?: ctors.first()
        }

        private fun findInteractCtor(): Constructor<*> {
            val ctors = PlayerInteractEvent::class.java.constructors
            return ctors.firstOrNull { it.parameterTypes.size == 6 }
                ?: ctors.firstOrNull { it.parameterTypes.size == 5 }
                ?: ctors.first()
        }

        private fun secondsToTicks(seconds: Long): Long = seconds * 20
    }

    private data class GappleConfigSnapshot(
        val oldPotionEffects: Boolean,
        val normalCooldown: Long,
        val enchantedCooldown: Long,
        val sharedCooldown: Boolean,
        val craftingEnabled: Boolean,
        val noConflictMode: Boolean,
        val regenDuration: Int
    )
}
