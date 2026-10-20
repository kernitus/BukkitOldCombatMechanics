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

        fun makeExplicitAttributes(item: ItemStack) {
            val valued = Class.forName("io.papermc.paper.datacomponent.DataComponentType\$Valued")
            val type =
                Class
                    .forName("io.papermc.paper.datacomponent.DataComponentTypes")
                    .getField("ATTRIBUTE_MODIFIERS")
                    .get(null)
            val attributes = Class.forName("io.papermc.paper.datacomponent.item.ItemAttributeModifiers")
            val entryType = Class.forName("io.papermc.paper.datacomponent.item.ItemAttributeModifiers\$Entry")
            val builderType = Class.forName("io.papermc.paper.datacomponent.item.ItemAttributeModifiers\$Builder")
            val displayType = Class.forName("io.papermc.paper.datacomponent.item.attribute.AttributeModifierDisplay")
            val data = ItemStack::class.java.getMethod("getData", valued).invoke(item, type)
            val entries = attributes.getMethod("modifiers").invoke(data) as List<*>
            val builder = attributes.getMethod("itemAttributes").invoke(null)
            val foreignDisplay =
                displayType
                    .getMethod("override", net.kyori.adventure.text.ComponentLike::class.java)
                    .invoke(null, Component.text("Foreign damage text"))
            val add =
                builderType.getMethod(
                    "addModifier",
                    org.bukkit.attribute.Attribute::class.java,
                    org.bukkit.attribute.AttributeModifier::class.java,
                    Class.forName("org.bukkit.inventory.EquipmentSlotGroup"),
                    displayType,
                )
            for (entry in entries) {
                add.invoke(
                    builder,
                    entryType.getMethod("attribute").invoke(entry),
                    entryType.getMethod("modifier").invoke(entry),
                    entryType.getMethod("getGroup").invoke(entry),
                    foreignDisplay,
                )
            }
            val replacement = builderType.getMethod("build").invoke(builder)
            ItemStack::class.java.getMethod("setData", valued, Any::class.java).invoke(item, type, replacement)
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
                    ocm.config.set("old-tool-damage.tooltip.enabled", true)
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

        test("tooltip master switch disables native display as well as lore") {
            withConfig(weaponMaterialKey = "DIAMOND_SWORD", weaponDamage = 7.0) {
                val p = spawnFake(Location(checkNotNull(Bukkit.getWorld("world")), 0.0, 100.0, 0.0))
                try {
                    runSync {
                        ocm.config.set("old-tool-damage.tooltip.enabled", false)
                        ocm.config.set("old-tool-damage.tooltip.native-attribute", true)
                        ocm.saveConfig()
                        Config.reload()
                        ModuleLoader.toggleModules()
                        p.player.inventory.setItemInMainHand(ItemStack(Material.DIAMOND_SWORD))
                        fireJoin(p.player) // Synthetic dispatch tests the listener's master switch.
                    }
                    val item =
                        runSyncAndGet {
                            p.player.inventory.itemInMainHand
                                .clone()
                        }
                    findOcmLines(item).size shouldBe 0
                    if (hasNativeDisplayApi()) nativeDamageDisplay(item) shouldBe null
                } finally {
                    cleanup(p)
                }
            }
        }

        test("hotbar listener handles the native pre-selection event ordering") {
            withConfig(weaponMaterialKey = "DIAMOND_SWORD", weaponDamage = 7.0) {
                val p = spawnFake(Location(checkNotNull(Bukkit.getWorld("world")), 0.0, 100.0, 0.0))
                try {
                    runSync {
                        p.player.inventory.heldItemSlot = 0
                        p.player.inventory.setItem(0, ItemStack(Material.STICK))
                        p.player.inventory.setItem(1, ItemStack(Material.DIAMOND_SWORD))
                        // Synthetic event precedes selection, matching native Bukkit dispatch.
                        Bukkit.getPluginManager().callEvent(PlayerItemHeldEvent(p.player, 0, 1))
                        p.player.inventory.heldItemSlot = 1
                    }
                    delayTicks(2)
                    runSyncAndGet { findOcmLines(p.player.inventory.itemInMainHand).size } shouldBe 1
                } finally {
                    cleanup(p)
                }
            }
        }

        test("foreign explicit attribute components retain their exact state")
            .config(enabledOrReasonIf = {
                io.kotest.core.test
                    .Enabled(hasNativeDisplayApi(), "Paper per-modifier display API is absent")
            }) {
                withConfig(weaponMaterialKey = "DIAMOND_SWORD", weaponDamage = 7.0) {
                    val p = spawnFake(Location(checkNotNull(Bukkit.getWorld("world")), 0.0, 100.0, 0.0))
                    try {
                        runSync {
                            ocm.config.set("old-tool-damage.tooltip.native-attribute", true)
                            ocm.saveConfig()
                            Config.reload()
                            ModuleLoader.toggleModules()
                            val item = ItemStack(Material.DIAMOND_SWORD)
                            makeExplicitAttributes(item)
                            dataOverridden(item) shouldBe true
                            p.player.inventory.setItemInMainHand(item)
                            fireJoin(p.player)
                        }
                        val item =
                            runSyncAndGet {
                                p.player.inventory.itemInMainHand
                                    .clone()
                            }
                        nativeDamageDisplay(item) shouldBe Component.text("Foreign damage text")
                        dataOverridden(item) shouldBe true
                        runSync { switchHotbar(p.player, 0, 1) }
                        runSyncAndGet { dataOverridden(checkNotNull(p.player.inventory.getItem(0))) } shouldBe true
                        runSyncAndGet { nativeDamageDisplay(checkNotNull(p.player.inventory.getItem(0))) } shouldBe
                            Component.text("Foreign damage text")
                    } finally {
                        cleanup(p)
                    }
                }
            }

        test("removed attribute component survives fallback lore writes")
            .config(enabledOrReasonIf = {
                io.kotest.core.test
                    .Enabled(hasNativeDisplayApi(), "Paper data component API is absent")
            }) {
                withConfig(weaponMaterialKey = "DIAMOND_SWORD", weaponDamage = 7.0) {
                    val p = spawnFake(Location(checkNotNull(Bukkit.getWorld("world")), 0.0, 100.0, 0.0))
                    try {
                        runSync {
                            val type = Class.forName("io.papermc.paper.datacomponent.DataComponentType")
                            val valued = Class.forName("io.papermc.paper.datacomponent.DataComponentType\$Valued")
                            val component =
                                Class
                                    .forName("io.papermc.paper.datacomponent.DataComponentTypes")
                                    .getField("ATTRIBUTE_MODIFIERS")
                                    .get(null)
                            val item = ItemStack(Material.DIAMOND_SWORD)
                            ItemStack::class.java.getMethod("unsetData", type).invoke(item, component)
                            p.player.inventory.setItemInMainHand(item)
                            fireJoin(p.player) // Synthetic listener dispatch exercises fallback lore writes.
                            val held = p.player.inventory.itemInMainHand
                            findOcmLines(held).size shouldBe 1
                            ItemStack::class.java.getMethod("getData", valued).invoke(held, component) shouldBe null
                            dataOverridden(held) shouldBe true
                            switchHotbar(p.player, 0, 1)
                            val released = checkNotNull(p.player.inventory.getItem(0))
                            findOcmLines(released).size shouldBe 0
                            ItemStack::class.java.getMethod("getData", valued).invoke(released, component) shouldBe null
                            dataOverridden(released) shouldBe true
                        }
                    } finally {
                        cleanup(p)
                    }
                }
            }

        test("real modeset changes and native drops release owned tooltips") {
            withConfig(weaponMaterialKey = "DIAMOND_SWORD", weaponDamage = 7.0) {
                val p = spawnFake(Location(checkNotNull(Bukkit.getWorld("world")), 8.0, 100.0, 8.0))
                var dropped: org.bukkit.entity.Item? = null
                val recipients = mutableListOf<java.util.UUID>()
                val listener =
                    object : Listener {
                        @EventHandler(priority = EventPriority.MONITOR)
                        fun onDrop(event: org.bukkit.event.player.PlayerDropItemEvent) {
                            if (event.player == p.player) dropped = event.itemDrop
                        }

                        @Suppress("DEPRECATION")
                        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
                        fun onPickup(event: org.bukkit.event.player.PlayerPickupItemEvent) {
                            if (event.item.uniqueId == dropped?.uniqueId && event.remaining == 0) {
                                recipients.add(event.player.uniqueId)
                            }
                        }
                    }
                try {
                    runSync {
                        ocm.config.set("old-tool-damage.tooltip.native-attribute", true)
                        ocm.saveConfig()
                        Config.reload()
                        ModuleLoader.toggleModules()
                        p.player.inventory.setItemInMainHand(ItemStack(Material.DIAMOND_SWORD))
                        // Reload initialises the held item; subsequent transitions use real API events.
                        Config.reload()
                        val api =
                            checkNotNull(
                                Bukkit.getServicesManager().load(
                                    kernitus.plugin.OldCombatMechanics.api.OldCombatMechanicsAPI::class.java,
                                ),
                            )
                        val active = p.player.inventory.itemInMainHand
                        if (hasNativeDisplayApi()) {
                            checkNotNull(nativeDamageDisplay(active))
                        } else {
                            findOcmLines(active).size shouldBe 1
                        }
                        api.setModesetForPlayer(p.player, "new")
                        val inactive = p.player.inventory.itemInMainHand
                        findOcmLines(inactive).size shouldBe 0
                        if (hasNativeDisplayApi()) {
                            nativeDamageDisplay(inactive) shouldBe null
                            dataOverridden(inactive) shouldBe false
                        }
                        api.setModesetForPlayer(p.player, "old")
                        Bukkit.getPluginManager().registerEvents(listener, testPlugin)
                        p.player.location.chunk
                            .load(true) shouldBe true
                        WeaponBaselineNativeCompat.dropSelectedItem(p.player) shouldBe true
                        val released = checkNotNull(dropped).itemStack
                        findOcmLines(released).size shouldBe 0
                        if (hasNativeDisplayApi()) {
                            nativeDamageDisplay(released) shouldBe null
                            dataOverridden(released) shouldBe false
                        }
                    }
                    delayTicks(2)
                    runSync {
                        checkNotNull(dropped).pickupDelay = 0
                        check(checkNotNull(dropped).teleport(p.player.location)) {
                            "Drop teleport failed: valid=${dropped.isValid} dead=${dropped.isDead} stack=${dropped.itemStack} playerHeld=${p.player.inventory.itemInMainHand}"
                        }
                    }
                    repeat(20) {
                        if (runSyncAndGet {
                                p.player.inventory.itemInMainHand.type != Material.DIAMOND_SWORD
                            }
                        ) {
                            delayTicks(
                                1,
                            )
                        }
                    }
                    delayTicks(2)
                    runSync {
                        val pickedUp = p.player.inventory.itemInMainHand
                        pickedUp.type shouldBe Material.DIAMOND_SWORD
                        recipients shouldBe listOf(p.player.uniqueId)
                        if (hasNativeDisplayApi()) {
                            checkNotNull(nativeDamageDisplay(pickedUp))
                        } else {
                            findOcmLines(pickedUp).size shouldBe 1
                        }
                    }
                } finally {
                    runSync {
                        HandlerList.unregisterAll(listener)
                        dropped?.remove()
                    }
                    cleanup(p)
                }
            }
        }

        test("player overrides and real world transfers clear the held tooltip") {
            withConfig(weaponMaterialKey = "DIAMOND_SWORD", weaponDamage = 7.0) {
                val p = spawnFake(Location(checkNotNull(Bukkit.getWorld("world")), 0.0, 100.0, 0.0))
                val destination = Bukkit.getWorlds().first { it.uid != p.player.world.uid }
                val worldKey = "worlds.${destination.name}"
                val worldSnapshot = ocm.config.get(worldKey)
                try {
                    runSync {
                        ocm.config.set("old-tool-damage.tooltip.native-attribute", true)
                        ocm.config.set(worldKey, listOf("old", "new"))
                        ocm.saveConfig()
                        p.player.inventory.setItemInMainHand(ItemStack(Material.DIAMOND_SWORD))
                        Config.reload()
                        val api =
                            checkNotNull(
                                Bukkit.getServicesManager().load(
                                    kernitus.plugin.OldCombatMechanics.api.OldCombatMechanicsAPI::class.java,
                                ),
                            )
                        api.forceDisableModuleForPlayer(p.player, "old-tool-damage")
                        val inactive = p.player.inventory.itemInMainHand
                        findOcmLines(inactive).size shouldBe 0
                        if (hasNativeDisplayApi()) nativeDamageDisplay(inactive) shouldBe null
                        api.clearModuleOverrideForPlayer(p.player, "old-tool-damage")
                        val active = p.player.inventory.itemInMainHand
                        if (hasNativeDisplayApi()) {
                            checkNotNull(nativeDamageDisplay(active))
                        } else {
                            findOcmLines(active).size shouldBe 1
                        }
                        val data =
                            kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage
                                .getPlayerData(p.player.uniqueId)
                        data.setModesetForWorld(destination.uid, "new")
                        kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage
                            .setPlayerData(p.player.uniqueId, data)
                        p.player.teleport(Location(destination, 0.0, 100.0, 0.0)) shouldBe true
                        p.player.world.uid shouldBe destination.uid
                        val transferred = p.player.inventory.itemInMainHand
                        findOcmLines(transferred).size shouldBe 0
                        if (hasNativeDisplayApi()) nativeDamageDisplay(transferred) shouldBe null
                    }
                } finally {
                    cleanup(p)
                    runSync {
                        ocm.config.set(worldKey, worldSnapshot)
                        ocm.saveConfig()
                        Config.reload()
                    }
                }
            }
        }

        test("registered shutdown callback restores inventory and cancels pending refresh") {
            withConfig(weaponMaterialKey = "DIAMOND_SWORD", weaponDamage = 7.0) {
                val p = spawnFake(Location(checkNotNull(Bukkit.getWorld("world")), 0.0, 100.0, 0.0))
                try {
                    runSync {
                        ocm.config.set("old-tool-damage.tooltip.native-attribute", true)
                        ocm.saveConfig()
                        p.player.inventory.setItem(0, ItemStack(Material.STICK))
                        p.player.inventory.setItem(1, ItemStack(Material.DIAMOND_SWORD))
                        Config.reload()
                        switchHotbar(p.player, 0, 1) // Synthetic hotbar event queues the final refresh.
                        val module = checkNotNull(ModuleLoader.getConfigurableModule("old-tool-damage"))
                        val listenerField = module.javaClass.getDeclaredField("tooltipListener")
                        listenerField.isAccessible = true
                        val listener = listenerField.get(module)
                        val callbacksField = OCMMain::class.java.getDeclaredField("disableListeners")
                        callbacksField.isAccessible = true
                        val callbacks = callbacksField.get(ocm) as List<*>
                        callbacks
                            .filterIsInstance<Runnable>()
                            .single { callback ->
                                callback.javaClass.declaredFields.any { captured ->
                                    captured.isAccessible = true
                                    captured.get(callback) === listener
                                }
                            }.run()
                    }
                    delayTicks(2)
                    runSync {
                        val released = p.player.inventory.itemInMainHand
                        findOcmLines(released).size shouldBe 0
                        if (hasNativeDisplayApi()) {
                            nativeDamageDisplay(released) shouldBe null
                            dataOverridden(released) shouldBe false
                        }
                    }
                } finally {
                    cleanup(p)
                }
            }
        }

        test("synthetic inventory drag strips transferred stacks and final cursor") {
            withConfig(weaponMaterialKey = "DIAMOND_SWORD", weaponDamage = 7.0) {
                val p = spawnFake(Location(checkNotNull(Bukkit.getWorld("world")), 0.0, 100.0, 0.0))
                try {
                    runSync {
                        ocm.config.set("old-tool-damage.tooltip.native-attribute", true)
                        ocm.saveConfig()
                        p.player.inventory.setItemInMainHand(ItemStack(Material.DIAMOND_SWORD))
                        Config.reload()
                        val shown =
                            p.player.inventory.itemInMainHand
                                .clone()
                        val view = checkNotNull(p.player.openInventory(Bukkit.createInventory(null, 9)))
                        val drag =
                            org.bukkit.event.inventory.InventoryDragEvent(
                                view,
                                shown.clone(),
                                shown.clone(),
                                false,
                                mapOf(0 to shown.clone()),
                            )
                        Bukkit.getPluginManager().callEvent(drag)
                        val cursor = checkNotNull(drag.cursor)
                        val transferred = checkNotNull(drag.newItems[0])
                        for (item in listOf(cursor, transferred)) {
                            findOcmLines(item).size shouldBe 0
                            if (hasNativeDisplayApi()) {
                                nativeDamageDisplay(item) shouldBe null
                                dataOverridden(item) shouldBe false
                            }
                        }
                        p.player.closeInventory()
                    }
                } finally {
                    cleanup(p)
                }
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
                    val swap = PlayerSwapHandItemsEvent(p.player, sword, stick)
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
                            p.player.inventory.itemInOffHand,
                            p.player.inventory.itemInMainHand,
                        )
                    Bukkit.getPluginManager().callEvent(swap)

                    val newMainHand = swap.mainHandItem?.clone() ?: ItemStack(Material.AIR)
                    val newOffHand = swap.offHandItem?.clone() ?: ItemStack(Material.AIR)
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
