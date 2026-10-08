/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics

import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import kernitus.plugin.OldCombatMechanics.utilities.Config
import kernitus.plugin.OldCombatMechanics.utilities.damage.WeaponDamages
import kotlinx.coroutines.delay
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.Callable

@OptIn(ExperimentalKotest::class)
class ToolDamageTooltipIntegrationTest :
    FunSpec({
        val testPlugin = JavaPlugin.getPlugin(OCMTestMain::class.java)
        val ocm = JavaPlugin.getPlugin(OCMMain::class.java)

        extensions(MainThreadDispatcherExtension(testPlugin))

        fun runSync(action: () -> Unit) {
            if (Bukkit.isPrimaryThread()) {
                action()
            } else {
                Bukkit
                    .getScheduler()
                    .callSyncMethod(
                        testPlugin,
                        Callable {
                            action()
                            null
                        },
                    ).get()
            }
        }

        fun <T> runSyncAndGet(action: () -> T): T =
            if (Bukkit.isPrimaryThread()) {
                action()
            } else {
                Bukkit.getScheduler().callSyncMethod(testPlugin, Callable { action() }).get()
            }

        suspend fun delayTicks(ticks: Long) {
            delay(ticks * 50L)
        }

        val lorePrefix = "OCM Damage:"

        fun stripColour(line: String): String = ChatColor.stripColor(line) ?: line

        fun findOcmLines(item: ItemStack): List<String> {
            val lore = item.itemMeta?.lore ?: emptyList()
            return lore.filter { stripColour(it).startsWith(lorePrefix) }
        }

        fun parseFirstDamage(item: ItemStack): Double? {
            val line = findOcmLines(item).firstOrNull() ?: return null
            val stripped = stripColour(line)
            val match = Regex("(-?\\d+(?:\\.\\d+)?)").find(stripped) ?: return null
            return match.value.toDoubleOrNull()
        }

        fun nativeDamageDisplay(item: ItemStack): Component? {
            val valued = Class.forName("io.papermc.paper.datacomponent.DataComponentType\$Valued")
            val type =
                Class
                    .forName("io.papermc.paper.datacomponent.DataComponentTypes")
                    .getField("ATTRIBUTE_MODIFIERS")
                    .get(null)
            val modifiers = item.javaClass.getMethod("getData", valued).invoke(item, type)
            val entries = modifiers.javaClass.getMethod("modifiers").invoke(modifiers) as List<*>
            val damage = org.bukkit.attribute.Attribute::class.java.getField("ATTACK_DAMAGE").get(null)
            val entry = entries.first { it != null && it.javaClass.getMethod("attribute").invoke(it) == damage }
            val display = entry!!.javaClass.getMethod("display").invoke(entry)
            return if (Class
                    .forName(
                        "io.papermc.paper.datacomponent.item.attribute.AttributeModifierDisplay\$OverrideText",
                    ).isInstance(display)
            ) {
                display.javaClass.getMethod("text").invoke(display) as Component
            } else {
                null
            }
        }

        fun nativeModifiers(item: ItemStack): List<Any> {
            val valued = Class.forName("io.papermc.paper.datacomponent.DataComponentType\$Valued")
            val type =
                Class
                    .forName(
                        "io.papermc.paper.datacomponent.DataComponentTypes",
                    ).getField("ATTRIBUTE_MODIFIERS")
                    .get(null)
            val modifiers = item.javaClass.getMethod("getData", valued).invoke(item, type)
            val entries = modifiers.javaClass.getMethod("modifiers").invoke(modifiers) as List<*>
            return entries.map { it!!.javaClass.getMethod("modifier").invoke(it) }
        }

        fun hasNativeDisplayApi(): Boolean =
            runCatching {
                val valued = Class.forName("io.papermc.paper.datacomponent.DataComponentType\$Valued")
                val type = Class.forName("io.papermc.paper.datacomponent.DataComponentType")
                Class.forName("io.papermc.paper.datacomponent.item.attribute.AttributeModifierDisplay\$OverrideText")
                ItemStack::class.java.getMethod("getData", valued)
                ItemStack::class.java.getMethod("isDataOverridden", type)
                Material::class.java.getMethod("getDefaultData", valued)
            }.isSuccess

        fun dataOverridden(item: ItemStack): Boolean {
            val type = Class.forName("io.papermc.paper.datacomponent.DataComponentType")
            val component =
                Class
                    .forName(
                        "io.papermc.paper.datacomponent.DataComponentTypes",
                    ).getField("ATTRIBUTE_MODIFIERS")
                    .get(null)
            return item.javaClass.getMethod("isDataOverridden", type).invoke(item, component) as Boolean
        }

        fun setLore(
            item: ItemStack,
            lines: List<String>?,
        ) {
            val meta = item.itemMeta ?: return
            meta.lore = lines
            item.itemMeta = meta
        }

        data class SpawnedPlayer(
            val fake: FakePlayer,
            val player: Player,
        )

        fun spawnFake(location: Location): SpawnedPlayer {
            lateinit var fake: FakePlayer
            lateinit var player: Player
            runSync {
                fake = FakePlayer(testPlugin)
                fake.spawn(location)
                player = checkNotNull(Bukkit.getPlayer(fake.uuid))
                player.inventory.clear()
                player.isInvulnerable = false
                player.activePotionEffects.forEach { player.removePotionEffect(it.type) }
                val data =
                    kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage
                        .getPlayerData(player.uniqueId)
                data.setModesetForWorld(player.world.uid, "old")
                kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage
                    .setPlayerData(player.uniqueId, data)
            }
            return SpawnedPlayer(fake, player)
        }

        fun cleanup(vararg players: SpawnedPlayer) {
            runSync { players.forEach { it.fake.removePlayer() } }
        }

        suspend fun TestScope.withConfig(
            weaponMaterialKey: String,
            weaponDamage: Double,
            block: suspend TestScope.() -> Unit,
        ) {
            val disabledModules = ocm.config.getStringList("disabled_modules")
            val modesetsSection =
                ocm.config.getConfigurationSection("modesets") ?: error("Missing 'modesets' section in config")
            val modesetSnapshot =
                modesetsSection.getKeys(false).associateWith { key ->
                    ocm.config.getStringList("modesets.$key")
                }
            val damagesSnapshot =
                ocm.config
                    .getConfigurationSection("old-tool-damage.damages")
                    ?.getValues(false)
                    ?: emptyMap<String, Any?>()
            val tooltipEnabledSnapshot = ocm.config.get("old-tool-damage.tooltip.enabled")
            val tooltipPrefixSnapshot = ocm.config.get("old-tool-damage.tooltip.prefix")
            val nativeAttributeSnapshot = ocm.config.get("old-tool-damage.tooltip.native-attribute")

            fun reloadAll() {
                ocm.saveConfig()
                Config.reload()
                WeaponDamages.initialise(ocm)
                ModuleLoader.toggleModules()
            }

            try {
                ocm.config.set("old-tool-damage.damages.$weaponMaterialKey", weaponDamage)
                ocm.config.set("old-tool-damage.tooltip.enabled", true)
                ocm.config.set("old-tool-damage.tooltip.prefix", lorePrefix)
                ocm.config.set("old-tool-damage.tooltip.native-attribute", false)
                ocm.config.set("disabled_modules", disabledModules.filterNot { it == "old-tool-damage" })
                val oldModeset = ocm.config.getStringList("modesets.old").toMutableList()
                if (!oldModeset.contains("old-tool-damage")) {
                    oldModeset.add("old-tool-damage")
                }
                ocm.config.set("modesets.old", oldModeset)
                reloadAll()
                block()
            } finally {
                ocm.config.set("disabled_modules", disabledModules)
                modesetSnapshot.forEach { (key, list) -> ocm.config.set("modesets.$key", list) }
                ocm.config.set("old-tool-damage.damages", null)
                damagesSnapshot.forEach { (k, v) -> ocm.config.set("old-tool-damage.damages.$k", v) }
                ocm.config.set("old-tool-damage.tooltip.enabled", tooltipEnabledSnapshot)
                ocm.config.set("old-tool-damage.tooltip.prefix", tooltipPrefixSnapshot)
                ocm.config.set("old-tool-damage.tooltip.native-attribute", nativeAttributeSnapshot)
                reloadAll()
            }
        }

        fun fireJoin(player: Player) {
            Bukkit.getPluginManager().callEvent(PlayerJoinEvent(player, "test"))
        }

        fun switchHotbar(
            player: Player,
            from: Int,
            to: Int,
        ) {
            player.inventory.heldItemSlot = to
            Bukkit.getPluginManager().callEvent(PlayerItemHeldEvent(player, from, to))
        }

        test("adds a tooltip lore line for configured vanilla weapon damage") {
            withConfig(weaponMaterialKey = "DIAMOND_SWORD", weaponDamage = 7.0) {
                val world = checkNotNull(Bukkit.getWorld("world"))
                val p = spawnFake(Location(world, 0.0, 100.0, 0.0))
                val sword = ItemStack(Material.DIAMOND_SWORD)
                runSync {
                    p.player.inventory.setItem(0, sword)
                    p.player.inventory.setItem(1, ItemStack(Material.STICK))
                    p.player.inventory.heldItemSlot = 1
                }

                runSync { switchHotbar(p.player, from = 1, to = 0) }

                val held =
                    runSyncAndGet {
                        p.player.inventory.itemInMainHand
                            .clone()
                    }
                val loreLines = findOcmLines(held)
                loreLines.size shouldBe 1
                parseFirstDamage(held) shouldBe (7.0 plusOrMinus 0.01)
                cleanup(p)
            }
        }

        test("Paper shows configured attack damage without changing the weapon modifier") {
            withConfig(weaponMaterialKey = "DIAMOND_AXE", weaponDamage = 7.0) {
                runSync {
                    ocm.config.set("old-tool-damage.tooltip.enabled", !hasNativeDisplayApi())
                    ocm.config.set("old-tool-damage.tooltip.native-attribute", true)
                    ocm.saveConfig()
                    Config.reload()
                    WeaponDamages.initialise(ocm)
                    ModuleLoader.toggleModules()
                }
                val world = checkNotNull(Bukkit.getWorld("world"))
                val p = spawnFake(Location(world, 0.0, 100.0, 0.0))
                val axe = ItemStack(Material.DIAMOND_AXE)
                setLore(axe, listOf("$lorePrefix 5"))
                val original = axe.clone()
                runSync {
                    p.player.inventory.setItem(0, axe)
                    p.player.inventory.setItem(1, ItemStack(Material.STICK))
                    p.player.inventory.heldItemSlot = 1
                    switchHotbar(p.player, from = 1, to = 0)
                }

                val held =
                    runSyncAndGet {
                        p.player.inventory.itemInMainHand
                            .clone()
                    }
                if (hasNativeDisplayApi()) {
                    val nativeDisplay = checkNotNull(nativeDamageDisplay(held))
                    nativeDisplay.toString().contains("7") shouldBe true
                    nativeDamageDisplay(held)?.insertion() shouldBe "ocm:old-tool-damage-tooltip"
                    nativeModifiers(held) shouldBe nativeModifiers(original)
                    findOcmLines(held).size shouldBe 0
                } else {
                    parseFirstDamage(held) shouldBe (7.0 plusOrMinus 0.01)
                }

                runSync { switchHotbar(p.player, from = 0, to = 1) }
                val released =
                    runSyncAndGet {
                        p.player.inventory
                            .getItem(0)
                            ?.clone() ?: error("Missing axe")
                    }
                if (hasNativeDisplayApi()) {
                    nativeDamageDisplay(released) shouldBe null
                    nativeModifiers(released) shouldBe nativeModifiers(original)
                    dataOverridden(released) shouldBe false
                } else {
                    findOcmLines(released).size shouldBe 0
                }
                cleanup(p)
            }
        }

        test("does not duplicate the tooltip lore line when applied repeatedly") {
            withConfig(weaponMaterialKey = "DIAMOND_SWORD", weaponDamage = 7.0) {
                val world = checkNotNull(Bukkit.getWorld("world"))
                val p = spawnFake(Location(world, 0.0, 100.0, 0.0))
                val sword = ItemStack(Material.DIAMOND_SWORD)
                runSync {
                    p.player.inventory.setItemInMainHand(sword)
                }

                runSync {
                    fireJoin(p.player)
                    fireJoin(p.player)
                }

                val held =
                    runSyncAndGet {
                        p.player.inventory.itemInMainHand
                            .clone()
                    }
                val loreLines = findOcmLines(held)
                loreLines.size shouldBe 1
                cleanup(p)
            }
        }

        test("preserves existing lore when adding the tooltip line") {
            withConfig(weaponMaterialKey = "DIAMOND_SWORD", weaponDamage = 7.0) {
                val world = checkNotNull(Bukkit.getWorld("world"))
                val p = spawnFake(Location(world, 0.0, 100.0, 0.0))
                val sword = ItemStack(Material.DIAMOND_SWORD)
                runSync {
                    setLore(sword, listOf("OtherPlugin: Example"))
                    p.player.inventory.setItemInMainHand(sword)
                    fireJoin(p.player)
                }

                val held =
                    runSyncAndGet {
                        p.player.inventory.itemInMainHand
                            .clone()
                    }
                val lore = held.itemMeta?.lore ?: emptyList()
                lore.any { stripColour(it) == "OtherPlugin: Example" } shouldBe true
                findOcmLines(held).size shouldBe 1
                cleanup(p)
            }
        }

        test("updates tooltip damage after config reload") {
            withConfig(weaponMaterialKey = "DIAMOND_SWORD", weaponDamage = 7.0) {
                val world = checkNotNull(Bukkit.getWorld("world"))
                val p = spawnFake(Location(world, 0.0, 100.0, 0.0))
                val sword = ItemStack(Material.DIAMOND_SWORD)
                runSync {
                    p.player.inventory.setItemInMainHand(sword)
                    fireJoin(p.player)
                }
                runSyncAndGet { parseFirstDamage(p.player.inventory.itemInMainHand) } shouldBe (7.0 plusOrMinus 0.01)

                runSync {
                    ocm.config.set("old-tool-damage.damages.DIAMOND_SWORD", 9.0)
                    ocm.saveConfig()
                    Config.reload()
                    WeaponDamages.initialise(ocm)
                    ModuleLoader.toggleModules()
                    fireJoin(p.player)
                }

                val held =
                    runSyncAndGet {
                        p.player.inventory.itemInMainHand
                            .clone()
                    }
                findOcmLines(held).size shouldBe 1
                parseFirstDamage(held) shouldBe (9.0 plusOrMinus 0.01)
                cleanup(p)
            }
        }

        test("cleans the tooltip lore line when the module is disabled") {
            withConfig(weaponMaterialKey = "DIAMOND_SWORD", weaponDamage = 7.0) {
                val world = checkNotNull(Bukkit.getWorld("world"))
                val p = spawnFake(Location(world, 0.0, 100.0, 0.0))
                val sword = ItemStack(Material.DIAMOND_SWORD)
                runSync {
                    p.player.inventory.setItem(0, sword)
                    p.player.inventory.setItem(1, ItemStack(Material.STICK))
                    p.player.inventory.heldItemSlot = 1
                    switchHotbar(p.player, from = 1, to = 0)
                }
                runSyncAndGet {
                    val slot0 = p.player.inventory.getItem(0) ?: ItemStack(Material.AIR)
                    findOcmLines(slot0).size
                } shouldBe 1

                runSync {
                    val data =
                        kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage
                            .getPlayerData(p.player.uniqueId)
                    data.setModesetForWorld(p.player.world.uid, "new")
                    kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage
                        .setPlayerData(p.player.uniqueId, data)
                    switchHotbar(p.player, from = 0, to = 1) // should clean the old hand
                }
                delayTicks(1)

                runSyncAndGet {
                    val slot0 = p.player.inventory.getItem(0) ?: ItemStack(Material.AIR)
                    findOcmLines(slot0).size
                } shouldBe 0
                cleanup(p)
            }
        }

        test("does not add a tooltip lore line for non-weapons") {
            withConfig(weaponMaterialKey = "DIAMOND_SWORD", weaponDamage = 7.0) {
                val world = checkNotNull(Bukkit.getWorld("world"))
                val p = spawnFake(Location(world, 0.0, 100.0, 0.0))
                val stick = ItemStack(Material.STICK)
                runSync {
                    p.player.inventory.setItemInMainHand(stick)
                    fireJoin(p.player)
                }

                findOcmLines(stick).size shouldBe 0
                cleanup(p)
            }
        }

        test("swap hand items applies tooltip to new main hand and keeps offhand clean") {
            withConfig(weaponMaterialKey = "DIAMOND_SWORD", weaponDamage = 7.0) {
                val world = checkNotNull(Bukkit.getWorld("world"))
                val p = spawnFake(Location(world, 0.0, 100.0, 0.0))
                val sword = ItemStack(Material.DIAMOND_SWORD)
                val stick = ItemStack(Material.STICK)
                runSync {
                    p.player.inventory.setItemInMainHand(stick)
                    p.player.inventory.setItemInOffHand(sword)
                    val swap = PlayerSwapHandItemsEvent(p.player, stick, sword)
                    Bukkit.getPluginManager().callEvent(swap)
                }

                findOcmLines(sword).size shouldBe 1
                findOcmLines(stick).size shouldBe 0
                cleanup(p)
            }
        }

        test("swap hand finalisation keeps tooltip only on new main-hand weapon") {
            withConfig(weaponMaterialKey = "DIAMOND_SWORD", weaponDamage = 7.0) {
                val world = checkNotNull(Bukkit.getWorld("world"))
                val p = spawnFake(Location(world, 0.0, 100.0, 0.0))
                runSync {
                    p.player.inventory.setItemInMainHand(ItemStack(Material.STICK))
                    p.player.inventory.setItemInOffHand(ItemStack(Material.DIAMOND_SWORD))

                    val swap =
                        PlayerSwapHandItemsEvent(
                            p.player,
                            p.player.inventory.itemInMainHand,
                            p.player.inventory.itemInOffHand,
                        )
                    Bukkit.getPluginManager().callEvent(swap)

                    val newMainHand = swap.offHandItem?.clone() ?: ItemStack(Material.AIR)
                    val newOffHand = swap.mainHandItem?.clone() ?: ItemStack(Material.AIR)
                    p.player.inventory.setItemInMainHand(newMainHand)
                    p.player.inventory.setItemInOffHand(newOffHand)
                }

                val mainHand =
                    runSyncAndGet {
                        p.player.inventory.itemInMainHand
                            .clone()
                    }
                val offHand =
                    runSyncAndGet {
                        p.player.inventory.itemInOffHand
                            .clone()
                    }
                mainHand.type shouldBe Material.DIAMOND_SWORD
                offHand.type shouldBe Material.STICK
                findOcmLines(mainHand).size shouldBe 1
                findOcmLines(offHand).size shouldBe 0
                cleanup(p)
            }
        }

        test("plays nicely with a lore-rewriting plugin (other lore preserved, no duplication)") {
            withConfig(weaponMaterialKey = "DIAMOND_SWORD", weaponDamage = 7.0) {
                val world = checkNotNull(Bukkit.getWorld("world"))
                val p = spawnFake(Location(world, 0.0, 100.0, 0.0))
                val sword = ItemStack(Material.DIAMOND_SWORD)

                val otherPlugin =
                    object : Listener {
                        @EventHandler(priority = EventPriority.LOWEST)
                        fun onJoin(event: PlayerJoinEvent) {
                            if (event.player != p.player) return
                            val item = event.player.inventory.itemInMainHand
                            if (item.type != Material.DIAMOND_SWORD) return
                            setLore(item, listOf("OtherPlugin: Rewritten"))
                        }

                        @EventHandler(priority = EventPriority.LOWEST)
                        fun onHeld(event: PlayerItemHeldEvent) {
                            if (event.player != p.player) return
                            val item = event.player.inventory.itemInMainHand
                            if (item.type != Material.DIAMOND_SWORD) return
                            setLore(item, listOf("OtherPlugin: Rewritten"))
                        }
                    }

                runSync {
                    Bukkit.getPluginManager().registerEvents(otherPlugin, testPlugin)
                    p.player.inventory.setItemInMainHand(sword)
                    fireJoin(p.player)
                    fireJoin(p.player)
                    HandlerList.unregisterAll(otherPlugin)
                }

                val held =
                    runSyncAndGet {
                        p.player.inventory.itemInMainHand
                            .clone()
                    }
                val lore = held.itemMeta?.lore ?: emptyList()
                lore.any { stripColour(it) == "OtherPlugin: Rewritten" } shouldBe true
                findOcmLines(held).size shouldBe 1
                cleanup(p)
            }
        }
    })
