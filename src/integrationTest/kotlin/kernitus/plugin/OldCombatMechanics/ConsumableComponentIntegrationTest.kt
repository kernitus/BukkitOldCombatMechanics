/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics

import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kernitus.plugin.OldCombatMechanics.module.ModuleSwordBlocking
import kernitus.plugin.OldCombatMechanics.utilities.Config
import kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage.getPlayerData
import kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage.setPlayerData
import kotlinx.coroutines.delay
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import org.bukkit.event.block.Action
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.util.Optional
import java.util.UUID
import java.util.concurrent.Callable

@OptIn(ExperimentalKotest::class)
class ConsumableComponentIntegrationTest :
    FunSpec({
        val testPlugin = JavaPlugin.getPlugin(OCMTestMain::class.java)
        val ocm = JavaPlugin.getPlugin(OCMMain::class.java)
        val swordBlocking =
            ModuleLoader.getModules().filterIsInstance<ModuleSwordBlocking>().firstOrNull()
                ?: error("ModuleSwordBlocking not registered")
        extensions(MainThreadDispatcherExtension(testPlugin))

        fun <T> runSync(action: () -> T): T =
            if (Bukkit.isPrimaryThread()) {
                action()
            } else {
                Bukkit
                    .getScheduler()
                    .callSyncMethod(testPlugin, Callable { action() })
                    .get()
            }

        suspend fun delayTicks(ticks: Long) {
            delay(ticks * 50L)
        }

        fun rightClickMainHand(player: Player) {
            runSync {
                val event =
                    PlayerInteractEvent(
                        player,
                        Action.RIGHT_CLICK_AIR,
                        player.inventory.itemInMainHand,
                        null,
                        BlockFace.SELF,
                        EquipmentSlot.HAND,
                    )
                Bukkit.getPluginManager().callEvent(event)
            }
        }

        fun paperDataComponentApiPresent(): Boolean =
            try {
                Class.forName("io.papermc.paper.datacomponent.DataComponentTypes")
                true
            } catch (_: Throwable) {
                false
            }

        fun paperConsumablePathAvailable(): Boolean {
            if (!paperDataComponentApiPresent()) return false
            val module = ModuleLoader.getModules().filterIsInstance<ModuleSwordBlocking>().firstOrNull() ?: return false
            return try {
                val supportedField = ModuleSwordBlocking::class.java.getDeclaredField("paperSupported")
                supportedField.isAccessible = true
                val adapterField = ModuleSwordBlocking::class.java.getDeclaredField("paperAdapter")
                adapterField.isAccessible = true
                supportedField.getBoolean(module) && adapterField.get(module) != null
            } catch (_: Throwable) {
                false
            }
        }

        fun packetEventsClass(name: String): Class<*> = Class.forName(name, true, ocm.javaClass.classLoader)

        fun packetEventsClientVersionClass(): Class<*> =
            packetEventsClass("kernitus.plugin.OldCombatMechanics.lib.packetevents.api.protocol.player.ClientVersion")

        fun packetEventsUserClass(): Class<*> =
            packetEventsClass("kernitus.plugin.OldCombatMechanics.lib.packetevents.api.protocol.player.User")

        fun packetEventsUserProfileClass(): Class<*> =
            packetEventsClass("kernitus.plugin.OldCombatMechanics.lib.packetevents.api.protocol.player.UserProfile")

        fun packetEventsConnectionStateClass(): Class<*> =
            packetEventsClass("kernitus.plugin.OldCombatMechanics.lib.packetevents.api.protocol.ConnectionState")

        fun packetEventsApi(): Any {
            val packetEventsClass =
                packetEventsClass("kernitus.plugin.OldCombatMechanics.lib.packetevents.api.PacketEvents")
            return packetEventsClass.getMethod("getAPI").invoke(null)
                ?: error("PacketEvents API not available")
        }

        fun packetEventsPlayerManager(): Any {
            val api = packetEventsApi()
            val method = api.javaClass.getDeclaredMethod("getPlayerManager")
            method.isAccessible = true
            return method.invoke(api)
                ?: error("PacketEvents PlayerManager not available")
        }

        fun packetEventsProtocolManager(): Any {
            val api = packetEventsApi()
            val method = api.javaClass.getDeclaredMethod("getProtocolManager")
            method.isAccessible = true
            return method.invoke(api)
                ?: error("PacketEvents ProtocolManager not available")
        }

        suspend fun requirePacketEventsUser(player: Player): Any {
            val playerManager = packetEventsPlayerManager()
            val getUserMethod = playerManager.javaClass.getDeclaredMethod("getUser", Any::class.java)
            getUserMethod.isAccessible = true
            repeat(10) {
                val user = runSync { getUserMethod.invoke(playerManager, player) }
                if (user != null) return user
                delayTicks(1)
            }
            val getChannelMethod = playerManager.javaClass.getDeclaredMethod("getChannel", Any::class.java)
            getChannelMethod.isAccessible = true
            val channel =
                runSync { getChannelMethod.invoke(playerManager, player) }
                    ?: error("PacketEvents channel missing for ${player.name}")
            val protocolManager = packetEventsProtocolManager()
            val setChannel = protocolManager.javaClass.getMethod("setChannel", UUID::class.java, Any::class.java)
            runSync { setChannel.invoke(protocolManager, player.uniqueId, channel) }

            val connectionStateClass = packetEventsConnectionStateClass()

            @Suppress("UNCHECKED_CAST")
            val connectionStateEnum = connectionStateClass as Class<out Enum<*>>
            val playState = java.lang.Enum.valueOf(connectionStateEnum, "PLAY")

            val profileClass = packetEventsUserProfileClass()
            val profile =
                profileClass
                    .getConstructor(UUID::class.java, String::class.java)
                    .newInstance(player.uniqueId, player.name)

            val clientVersionClass = packetEventsClientVersionClass()

            @Suppress("UNCHECKED_CAST")
            val clientVersionEnum = clientVersionClass as Class<out Enum<*>>
            val defaultVersion = java.lang.Enum.valueOf(clientVersionEnum, "V_1_21_11")

            val userClass = packetEventsUserClass()
            val user =
                userClass
                    .getConstructor(Any::class.java, connectionStateClass, clientVersionClass, profileClass)
                    .newInstance(channel, playState, defaultVersion, profile)

            val setUser = protocolManager.javaClass.getMethod("setUser", Any::class.java, userClass)
            runSync { setUser.invoke(protocolManager, channel, user) }
            return user
        }

        fun packetEventsClientVersion(versionName: String): Any {
            val versionClass = packetEventsClientVersionClass()

            @Suppress("UNCHECKED_CAST")
            val enumClass = versionClass as Class<out Enum<*>>
            return java.lang.Enum.valueOf(enumClass, versionName)
        }

        fun unknownPacketEventsClientVersionName(): String? {
            val versionClass = packetEventsClientVersionClass()

            @Suppress("UNCHECKED_CAST")
            val enumClass = versionClass as Class<out Enum<*>>
            val names = enumClass.enumConstants.map { it.name }
            return when {
                names.contains("UNKNOWN") -> "UNKNOWN"
                names.contains("HIGHER_THAN_SUPPORTED_VERSIONS") -> "HIGHER_THAN_SUPPORTED_VERSIONS"
                else -> null
            }
        }

        suspend fun withPacketEventsClientVersion(
            player: Player,
            versionName: String,
            block: suspend () -> Unit,
        ) {
            val user = requirePacketEventsUser(player)
            val versionClass = packetEventsClientVersionClass()
            val getVersion = user.javaClass.getDeclaredMethod("getClientVersion")
            val setVersion = user.javaClass.getDeclaredMethod("setClientVersion", versionClass)
            getVersion.isAccessible = true
            setVersion.isAccessible = true
            val original = runSync { getVersion.invoke(user) }
            val target = packetEventsClientVersion(versionName)
            runSync { setVersion.invoke(user, target) }
            try {
                block()
            } finally {
                if (original != null) {
                    runSync { setVersion.invoke(user, original) }
                }
            }
        }

        fun nmsItemStack(stack: ItemStack?): Any? {
            if (stack == null) return null
            var handle: Any? = null
            try {
                var type: Class<*>? = stack.javaClass
                while (type != null && type != Any::class.java) {
                    val field =
                        try {
                            type.getDeclaredField("handle")
                        } catch (_: NoSuchFieldException) {
                            type = type.superclass
                            continue
                        }
                    field.isAccessible = true
                    handle = runCatching { field.get(stack) }.getOrNull()
                    break
                }
            } catch (_: Throwable) {
                handle = null
            }
            if (handle != null) return handle
            return try {
                val craftItemStack = Class.forName("org.bukkit.craftbukkit.inventory.CraftItemStack")
                val asNmsCopy = craftItemStack.getMethod("asNMSCopy", ItemStack::class.java)
                asNmsCopy.invoke(null, stack)
            } catch (t: Throwable) {
                throw IllegalStateException(
                    "Failed to obtain NMS ItemStack (${t::class.java.simpleName}: ${t.message})",
                    t,
                )
            }
        }

        fun craftMirrorStack(type: Material): ItemStack {
            val craftItemStack = Class.forName("org.bukkit.craftbukkit.inventory.CraftItemStack")
            val nmsItemStackClass = Class.forName("net.minecraft.world.item.ItemStack")
            val asNmsCopy = craftItemStack.getMethod("asNMSCopy", ItemStack::class.java)
            val asCraftMirror = craftItemStack.getMethod("asCraftMirror", nmsItemStackClass)
            val nms = asNmsCopy.invoke(null, ItemStack(type))
            return asCraftMirror.invoke(null, nms) as ItemStack
        }

        fun consumablePatchEntry(stack: ItemStack?): Optional<*>? {
            val nmsStack = nmsItemStack(stack) ?: return null
            return try {
                val patch = nmsStack.javaClass.getMethod("getComponentsPatch").invoke(nmsStack) ?: return null
                val dataComponentType = Class.forName("net.minecraft.core.component.DataComponentType")
                val dataComponents = Class.forName("net.minecraft.core.component.DataComponents")
                val consumableType = dataComponents.getField("CONSUMABLE").get(null)
                val getMethod = patch.javaClass.getMethod("get", dataComponentType)
                getMethod.invoke(patch, consumableType) as? Optional<*>
            } catch (t: Throwable) {
                throw IllegalStateException(
                    "Failed to inspect data component patch (${t::class.java.simpleName}: ${t.message})",
                    t,
                )
            }
        }

        fun hasConsumableRemoval(stack: ItemStack?): Boolean {
            val entry = consumablePatchEntry(stack) ?: return false
            return !entry.isPresent
        }

        fun nmsConsumableType(): Any = Class.forName("net.minecraft.core.component.DataComponents").getField("CONSUMABLE").get(null)

        fun nmsConsumableComponent(): Any {
            val nmsConsumable = Class.forName("net.minecraft.world.item.component.Consumable")
            val builder = nmsConsumable.getMethod("builder").invoke(null)
            val nmsUseAnim = Class.forName("net.minecraft.world.item.ItemUseAnimation")
            val blockAnim = nmsUseAnim.getField("BLOCK").get(null)
            val withSeconds = builder.javaClass.getMethod("consumeSeconds", Float::class.javaPrimitiveType).invoke(builder, 1.6f)
            val withAnim = withSeconds.javaClass.getMethod("animation", nmsUseAnim).invoke(withSeconds, blockAnim)
            return withAnim.javaClass.getMethod("build").invoke(withAnim)
        }

        fun hasConsumableComponent(stack: ItemStack?): Boolean {
            val nmsStack = nmsItemStack(stack) ?: return false
            return try {
                val dataComponentType = Class.forName("net.minecraft.core.component.DataComponentType")
                val consumableType = nmsConsumableType()
                val hasMethod = nmsStack.javaClass.getMethod("has", dataComponentType)
                val result = hasMethod.invoke(nmsStack, consumableType)
                result is Boolean && result
            } catch (_: Throwable) {
                false
            }
        }

        fun applyConsumableComponent(stack: ItemStack?) {
            val nmsStack = nmsItemStack(stack) ?: return
            try {
                val dataComponentType = Class.forName("net.minecraft.core.component.DataComponentType")
                val consumableType = nmsConsumableType()
                val consumableComponent = nmsConsumableComponent()
                val setMethod =
                    nmsStack.javaClass.methods.firstOrNull { m ->
                        m.name == "set" &&
                            m.parameterCount == 2 &&
                            m.parameterTypes[0] == dataComponentType
                    } ?: error("NMS ItemStack#set(DataComponentType, value) not found")
                setMethod.invoke(nmsStack, consumableType, consumableComponent)
            } catch (t: Throwable) {
                throw IllegalStateException(
                    "Failed to apply NMS consumable component (${t::class.java.simpleName}: ${t.message})",
                    t,
                )
            }
        }

        fun assertNoConsumableRemoval(
            stack: ItemStack?,
            label: String,
        ) {
            val entry = consumablePatchEntry(stack)
            if (entry != null && !entry.isPresent) {
                error("$label gained !minecraft:consumable")
            }
        }

        fun setModeset(
            player: Player,
            modeset: String?,
        ) {
            val data = getPlayerData(player.uniqueId)
            val worldId = player.world.uid
            if (modeset == null) {
                data.modesetByWorld.remove(worldId)
            } else {
                data.setModesetForWorld(worldId, modeset)
            }
            setPlayerData(player.uniqueId, data)
        }

        fun syntheticPlayerDeathEvent(
            player: Player,
            drops: MutableList<ItemStack>,
        ): PlayerDeathEvent {
            for (ctor in PlayerDeathEvent::class.java.constructors) {
                val args = arrayOfNulls<Any>(ctor.parameterCount)
                var supported = true
                for ((index, paramType) in ctor.parameterTypes.withIndex()) {
                    val value: Any? =
                        when {
                            Player::class.java.isAssignableFrom(paramType) -> {
                                player
                            }

                            MutableList::class.java.isAssignableFrom(paramType) || List::class.java.isAssignableFrom(paramType) -> {
                                drops
                            }

                            paramType == Int::class.javaPrimitiveType || paramType == Int::class.java -> {
                                0
                            }

                            paramType == Boolean::class.javaPrimitiveType || paramType == Boolean::class.java -> {
                                false
                            }

                            paramType == String::class.java -> {
                                ""
                            }

                            else -> {
                                if (paramType.isPrimitive) {
                                    supported = false
                                    null
                                } else {
                                    null
                                }
                            }
                        }
                    if (!supported) break
                    args[index] = value
                }
                if (!supported) continue
                val instance = runCatching { ctor.newInstance(*args) }.getOrNull() ?: continue
                if (instance is PlayerDeathEvent) {
                    return instance
                }
            }
            error("Failed to create PlayerDeathEvent reflectively")
        }

        fun snapshotSection(path: String): Any? {
            val section = ocm.config.getConfigurationSection(path)
            return section?.getValues(false) ?: ocm.config.get(path)
        }

        fun restoreSection(
            path: String,
            value: Any?,
        ) {
            ocm.config.set(path, null)
            when (value) {
                null -> {
                    Unit
                }

                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    ocm.config.createSection(path, value as Map<String, Any?>)
                }

                else -> {
                    ocm.config.set(path, value)
                }
            }
        }

        suspend fun withWorldModesets(
            worldModesets: List<String>,
            block: suspend () -> Unit,
        ) {
            val originalWorlds = runSync { snapshotSection("worlds") }
            try {
                runSync {
                    ocm.config.set("worlds.world", worldModesets)
                    ocm.saveConfig()
                    Config.reload()
                }
                block()
            } finally {
                runSync {
                    restoreSection("worlds", originalWorlds)
                    ocm.saveConfig()
                    Config.reload()
                }
            }
        }

        suspend fun withSwordBlockingDisabled(block: suspend () -> Unit) {
            val originalAlways = runSync { snapshotSection("always_enabled_modules") }
            val originalDisabled = runSync { snapshotSection("disabled_modules") }
            val originalModesets = runSync { snapshotSection("modesets") }
            try {
                runSync {
                    val always =
                        ocm
                            .config
                            .getStringList("always_enabled_modules")
                            .filterNot { it.equals("sword-blocking", ignoreCase = true) }
                    ocm.config.set("always_enabled_modules", always)

                    val disabled =
                        ocm
                            .config
                            .getStringList("disabled_modules")
                            .filterNot { it.equals("sword-blocking", ignoreCase = true) }
                            .toMutableList()
                    disabled.add("sword-blocking")
                    ocm.config.set("disabled_modules", disabled)

                    val modesetsSection =
                        ocm.config.getConfigurationSection("modesets")
                            ?: error("modesets missing")
                    for (key in modesetsSection.getKeys(false)) {
                        val modules =
                            modesetsSection
                                .getStringList(key)
                                .filterNot { it.equals("sword-blocking", ignoreCase = true) }
                        ocm.config.set("modesets.$key", modules)
                    }

                    ocm.saveConfig()
                    Config.reload()
                }
                block()
            } finally {
                runSync {
                    restoreSection("always_enabled_modules", originalAlways)
                    restoreSection("disabled_modules", originalDisabled)
                    restoreSection("modesets", originalModesets)
                    ocm.saveConfig()
                    Config.reload()
                }
            }
        }

        lateinit var fake: FakePlayer
        lateinit var player: Player

        beforeSpec {
            runSync {
                val world = Bukkit.getWorld("world") ?: error("world missing")
                fake = FakePlayer(testPlugin)
                fake.spawn(Location(world, 0.0, 100.0, 0.0))
                player = Bukkit.getPlayer(fake.uuid) ?: error("player missing")
                player.gameMode = GameMode.SURVIVAL
                player.isInvulnerable = false
                player.inventory.clear()
                player.inventory.setItemInOffHand(ItemStack(Material.AIR))
                player.updateInventory()
            }
        }

        afterSpec {
            runSync {
                fake.removePlayer()
            }
        }

        beforeTest {
            runSync {
                setModeset(player, "old")
                player.inventory.clear()
                player.inventory.setItemInOffHand(ItemStack(Material.AIR))
                player.setItemOnCursor(ItemStack(Material.AIR))
                player.inventory.heldItemSlot = 0
                player.updateInventory()
            }
        }

        test("hotbar swap keeps food consumable") {
            if (!paperConsumablePathAvailable()) {
                println("Skipping: Paper consumable component path unavailable")
                return@test
            }

            runSync {
                player.inventory.setItem(0, ItemStack(Material.BREAD))
                player.inventory.setItem(1, ItemStack(Material.STONE))
                player.inventory.heldItemSlot = 0
            }

            val before = runSync { player.inventory.getItem(0) }
            assertNoConsumableRemoval(before, "hotbar food (before)")

            runSync {
                Bukkit.getPluginManager().callEvent(PlayerItemHeldEvent(player, 0, 1))
            }

            val after = runSync { player.inventory.getItem(0) }
            hasConsumableRemoval(after) shouldBe false
        }

        test("inventory click keeps slot and cursor food consumable") {
            if (!paperConsumablePathAvailable()) {
                println("Skipping: Paper consumable component path unavailable")
                return@test
            }

            val view =
                runSync { player.openInventory(player.inventory) }
                    ?: error("inventory view missing")
            try {
                runSync {
                    player.inventory.setItem(0, ItemStack(Material.BREAD))
                    player.setItemOnCursor(ItemStack(Material.CARROT))
                }

                val slotItem = runSync { player.inventory.getItem(0) }
                val cursorItem = runSync { player.itemOnCursor }
                assertNoConsumableRemoval(slotItem, "slot food (before)")
                assertNoConsumableRemoval(cursorItem, "cursor food (before)")

                val event =
                    runSync {
                        val click =
                            InventoryClickEvent(
                                view,
                                InventoryType.SlotType.CONTAINER,
                                0,
                                ClickType.LEFT,
                                InventoryAction.PICKUP_ALL,
                            )
                        click.currentItem = slotItem
                        click.cursor = cursorItem
                        click
                    }

                runSync { Bukkit.getPluginManager().callEvent(event) }
                delayTicks(1)

                val afterSlot = runSync { player.inventory.getItem(0) }
                val afterCursor = runSync { player.itemOnCursor }
                hasConsumableRemoval(afterSlot) shouldBe false
                hasConsumableRemoval(afterCursor) shouldBe false
            } finally {
                runSync { player.closeInventory() }
            }
        }

        test("inventory click does not alter swords when no consumable change is needed") {
            if (!paperConsumablePathAvailable()) {
                println("Skipping: Paper consumable component path unavailable")
                return@test
            }

            runSync {
                setModeset(player, "old")
                player.inventory.setItem(0, ItemStack(Material.DIAMOND_SWORD))
                player.inventory.setItem(1, ItemStack(Material.STONE))
                player.inventory.heldItemSlot = 1
                player.setItemOnCursor(ItemStack(Material.IRON_SWORD))
            }

            val view = runSync { player.openInventory(player.inventory) } ?: error("inventory view missing")
            try {
                val slotItem = runSync { craftMirrorStack(Material.DIAMOND_SWORD) }
                val cursorItem = runSync { craftMirrorStack(Material.IRON_SWORD) }
                applyConsumableComponent(slotItem)
                applyConsumableComponent(cursorItem)
                assertNoConsumableRemoval(slotItem, "slot sword (before)")
                assertNoConsumableRemoval(cursorItem, "cursor sword (before)")

                val event =
                    runSync {
                        val click =
                            InventoryClickEvent(
                                view,
                                InventoryType.SlotType.CONTAINER,
                                0,
                                ClickType.LEFT,
                                InventoryAction.PICKUP_ALL,
                            )
                        click.currentItem = slotItem
                        click.cursor = cursorItem
                        click
                    }

                runSync { Bukkit.getPluginManager().callEvent(event) }
                delayTicks(1)

                val afterSlot = runSync { player.inventory.getItem(0) }
                val afterCursor = runSync { player.itemOnCursor }
                hasConsumableRemoval(afterSlot) shouldBe false
                hasConsumableRemoval(afterCursor) shouldBe false
            } finally {
                runSync { player.closeInventory() }
            }
        }

        test("stale deferred click reapply does not taint newly selected main-hand sword") {
            if (!paperConsumablePathAvailable()) {
                println("Skipping: Paper consumable component path unavailable")
                return@test
            }

            runSync {
                setModeset(player, "old")
                player.gameMode = GameMode.SURVIVAL
                player.inventory.setItem(0, ItemStack(Material.DIAMOND_SWORD))
                player.inventory.setItem(1, ItemStack(Material.IRON_SWORD))
                player.inventory.heldItemSlot = 0
                player.updateInventory()
            }

            runSync {
                hasConsumableComponent(player.inventory.getItem(0)) shouldBe false
                hasConsumableComponent(player.inventory.getItem(1)) shouldBe false
            }

            val view = runSync { player.openInventory(player.inventory) } ?: error("inventory view missing")
            try {
                val click =
                    runSync {
                        InventoryClickEvent(
                            view,
                            InventoryType.SlotType.CONTAINER,
                            0,
                            ClickType.NUMBER_KEY,
                            InventoryAction.HOTBAR_SWAP,
                            0,
                        )
                    }

                runSync { Bukkit.getPluginManager().callEvent(click) }

                // Change selection directly before deferred next-tick reapply runs.
                runSync {
                    player.inventory.heldItemSlot = 1
                    player.updateInventory()
                }

                delayTicks(1)

                runSync {
                    player.inventory.heldItemSlot shouldBe 1
                    player.inventory.itemInMainHand.type shouldBe Material.IRON_SWORD
                    hasConsumableComponent(player.inventory.getItem(1)) shouldBe false
                }
            } finally {
                runSync { player.closeInventory() }
            }
        }

        test("inventory click does not strip consumable component when sword-blocking disabled for player modeset") {
            if (!paperConsumablePathAvailable()) {
                println("Skipping: Paper consumable component path unavailable")
                return@test
            }

            runSync {
                setModeset(player, "new")
            }

            runSync {
                swordBlocking.isEnabled(player) shouldBe false
            }

            val view = runSync { player.openInventory(player.inventory) } ?: error("inventory view missing")
            try {
                val slotItem = runSync { craftMirrorStack(Material.DIAMOND_SWORD) }
                val cursorItem = runSync { craftMirrorStack(Material.IRON_SWORD) }
                applyConsumableComponent(slotItem)
                applyConsumableComponent(cursorItem)
                assertNoConsumableRemoval(slotItem, "slot sword (before)")
                assertNoConsumableRemoval(cursorItem, "cursor sword (before)")

                val event =
                    runSync {
                        val click =
                            InventoryClickEvent(
                                view,
                                InventoryType.SlotType.CONTAINER,
                                0,
                                ClickType.LEFT,
                                InventoryAction.PICKUP_ALL,
                            )
                        click.currentItem = slotItem
                        click.cursor = cursorItem
                        click
                    }

                runSync { Bukkit.getPluginManager().callEvent(event) }

                // Assert the same objects we supplied to the event were not mutated.
                hasConsumableComponent(slotItem) shouldBe true
                hasConsumableComponent(cursorItem) shouldBe true
            } finally {
                runSync { player.closeInventory() }
            }
        }

        test("inventory click does not strip consumable component when sword-blocking disabled in world defaults") {
            if (!paperConsumablePathAvailable()) {
                println("Skipping: Paper consumable component path unavailable")
                return@test
            }

            withWorldModesets(listOf("new")) {
                runSync {
                    setModeset(player, null)
                }

                runSync {
                    swordBlocking.isEnabled(player) shouldBe false
                }

                val view = runSync { player.openInventory(player.inventory) } ?: error("inventory view missing")
                try {
                    val slotItem = runSync { craftMirrorStack(Material.DIAMOND_SWORD) }
                    val cursorItem = runSync { craftMirrorStack(Material.IRON_SWORD) }
                    applyConsumableComponent(slotItem)
                    applyConsumableComponent(cursorItem)
                    assertNoConsumableRemoval(slotItem, "slot sword (before)")
                    assertNoConsumableRemoval(cursorItem, "cursor sword (before)")

                    val event =
                        runSync {
                            val click =
                                InventoryClickEvent(
                                    view,
                                    InventoryType.SlotType.CONTAINER,
                                    0,
                                    ClickType.LEFT,
                                    InventoryAction.PICKUP_ALL,
                                )
                            click.currentItem = slotItem
                            click.cursor = cursorItem
                            click
                        }

                    runSync { Bukkit.getPluginManager().callEvent(event) }

                    // Assert the same objects we supplied to the event were not mutated.
                    hasConsumableComponent(slotItem) shouldBe true
                    hasConsumableComponent(cursorItem) shouldBe true
                } finally {
                    runSync { player.closeInventory() }
                }
            }
        }

        test("modeset change to disabled strips sword consumable component from hand") {
            if (!paperConsumablePathAvailable()) {
                println("Skipping: Paper consumable component path unavailable")
                return@test
            }

            runSync {
                setModeset(player, "old")
                player.gameMode = GameMode.SURVIVAL
                player.inventory.setItemInMainHand(craftMirrorStack(Material.DIAMOND_SWORD))
            }

            // Seed the component on the actual hand item.
            runSync {
                val main = player.inventory.itemInMainHand
                applyConsumableComponent(main)
                player.inventory.setItemInMainHand(main)
            }

            runSync {
                hasConsumableComponent(player.inventory.itemInMainHand) shouldBe true
            }

            // Change modeset so sword-blocking is disabled for this player.
            runSync {
                setModeset(player, "new")
                swordBlocking.isEnabled(player) shouldBe false
                // Simulate the plugin's modeset-change hook.
                ModuleLoader.getModules().forEach { it.onModesetChange(player) }
            }

            runSync {
                hasConsumableComponent(player.inventory.itemInMainHand) shouldBe false
            }
        }

        test("disabled_modules clears sword consumable component after reload") {
            if (!paperConsumablePathAvailable()) {
                println("Skipping: Paper consumable component path unavailable")
                return@test
            }

            runSync {
                setModeset(player, "old")
                player.gameMode = GameMode.SURVIVAL
                player.inventory.setItemInMainHand(craftMirrorStack(Material.DIAMOND_SWORD))
            }

            runSync {
                val main = player.inventory.itemInMainHand
                applyConsumableComponent(main)
                player.inventory.setItemInMainHand(main)
            }

            runSync {
                hasConsumableComponent(player.inventory.itemInMainHand) shouldBe true
            }

            withSwordBlockingDisabled {
                runSync {
                    swordBlocking.isEnabled(player) shouldBe false
                }

                runSync {
                    hasConsumableComponent(player.inventory.itemInMainHand) shouldBe false
                }
            }
        }

        test("disabled_modules prevents sword consumable component on right-click") {
            if (!paperConsumablePathAvailable()) {
                println("Skipping: Paper consumable component path unavailable")
                return@test
            }

            runSync {
                setModeset(player, "old")
                player.gameMode = GameMode.SURVIVAL
                player.inventory.setItemInMainHand(ItemStack(Material.DIAMOND_SWORD))
            }

            withSwordBlockingDisabled {
                runSync {
                    swordBlocking.isEnabled(player) shouldBe false
                }

                rightClickMainHand(player)
                delayTicks(1)

                runSync {
                    hasConsumableComponent(player.inventory.itemInMainHand) shouldBe false
                }
            }
        }

        test("reload toggle disables and re-enables sword consumable component") {
            if (!paperConsumablePathAvailable()) {
                println("Skipping: Paper consumable component path unavailable")
                return@test
            }

            runSync {
                setModeset(player, "old")
                player.gameMode = GameMode.SURVIVAL
                player.inventory.setItemInMainHand(ItemStack(Material.DIAMOND_SWORD))
            }

            rightClickMainHand(player)
            delayTicks(1)

            runSync {
                hasConsumableComponent(player.inventory.itemInMainHand) shouldBe true
            }

            withSwordBlockingDisabled {
                runSync {
                    hasConsumableComponent(player.inventory.itemInMainHand) shouldBe false
                }
            }

            delayTicks(1)
            rightClickMainHand(player)
            delayTicks(1)

            runSync {
                hasConsumableComponent(player.inventory.itemInMainHand) shouldBe true
            }
        }

        test("disabled_modules clears stored sword consumable components after reload") {
            if (!paperConsumablePathAvailable()) {
                println("Skipping: Paper consumable component path unavailable")
                return@test
            }

            runSync {
                setModeset(player, "old")
                player.gameMode = GameMode.SURVIVAL
                player.inventory.setItemInMainHand(ItemStack(Material.STONE))
            }

            runSync {
                val stored = craftMirrorStack(Material.IRON_SWORD)
                applyConsumableComponent(stored)
                player.inventory.setItem(2, stored)
            }

            runSync {
                hasConsumableComponent(player.inventory.getItem(2)) shouldBe true
            }

            withSwordBlockingDisabled {
                runSync {
                    hasConsumableComponent(player.inventory.getItem(2)) shouldBe false
                }
            }
        }

        test("disabled_modules keeps offhand unchanged on right-click") {
            if (!paperConsumablePathAvailable()) {
                println("Skipping: Paper consumable component path unavailable")
                return@test
            }

            runSync {
                setModeset(player, "old")
                player.gameMode = GameMode.SURVIVAL
                player.inventory.setItemInMainHand(ItemStack(Material.DIAMOND_SWORD))
                player.inventory.setItemInOffHand(ItemStack(Material.APPLE))
            }

            val offhandBefore = runSync { player.inventory.itemInOffHand }
            assertNoConsumableRemoval(offhandBefore, "offhand item (before disabled right-click)")

            withSwordBlockingDisabled {
                rightClickMainHand(player)
                delayTicks(1)

                runSync {
                    player.inventory.itemInOffHand.type shouldBe Material.APPLE
                    hasConsumableRemoval(player.inventory.itemInOffHand) shouldBe false
                }
            }
        }

        test("old client uses offhand shield instead of consumable animation") {
            if (!paperConsumablePathAvailable()) {
                println("Skipping: Paper consumable component path unavailable")
                return@test
            }

            runSync {
                setModeset(player, "old")
                player.gameMode = GameMode.SURVIVAL
                player.inventory.setItemInMainHand(ItemStack(Material.DIAMOND_SWORD))
                player.inventory.setItemInOffHand(ItemStack(Material.AIR))
            }

            withPacketEventsClientVersion(player, "V_1_20_3") {
                rightClickMainHand(player)
                delayTicks(1)

                runSync {
                    player.inventory.itemInOffHand.type shouldBe Material.SHIELD
                    hasConsumableComponent(player.inventory.itemInMainHand) shouldBe false
                }
            }
        }

        test("unknown client version uses offhand shield fallback instead of consumable animation") {
            if (!paperConsumablePathAvailable()) {
                println("Skipping: Paper consumable component path unavailable")
                return@test
            }

            val unknownVersion =
                runCatching { unknownPacketEventsClientVersionName() }.getOrNull()
                    ?: run {
                        println("Skipping: PacketEvents unknown client version enum constant unavailable")
                        return@test
                    }

            runSync {
                setModeset(player, "old")
                player.gameMode = GameMode.SURVIVAL
                player.inventory.setItemInMainHand(ItemStack(Material.DIAMOND_SWORD))
                player.inventory.setItemInOffHand(ItemStack(Material.AIR))
            }

            withPacketEventsClientVersion(player, unknownVersion) {
                rightClickMainHand(player)
                delayTicks(1)

                runSync {
                    player.inventory.itemInOffHand.type shouldBe Material.SHIELD
                    hasConsumableComponent(player.inventory.itemInMainHand) shouldBe false
                }
            }
        }

        test("missing PacketEvents client-version resolver fails safe to offhand shield fallback") {
            if (!paperConsumablePathAvailable()) {
                println("Skipping: Paper consumable component path unavailable")
                return@test
            }

            runSync {
                setModeset(player, "old")
                player.gameMode = GameMode.SURVIVAL
                player.inventory.setItemInMainHand(ItemStack(Material.DIAMOND_SWORD))
                player.inventory.setItemInOffHand(ItemStack(Material.AIR))
            }

            val moduleClass = ModuleSwordBlocking::class.java
            val packetEventsGetClientVersionField = moduleClass.getDeclaredField("packetEventsGetClientVersion")
            val minClientVersionField = moduleClass.getDeclaredField("minClientVersion")
            packetEventsGetClientVersionField.isAccessible = true
            minClientVersionField.isAccessible = true

            val originalGetClientVersion = runSync { packetEventsGetClientVersionField.get(swordBlocking) }
            val originalMinClientVersion = runSync { minClientVersionField.get(swordBlocking) }

            try {
                runSync {
                    packetEventsGetClientVersionField.set(swordBlocking, null)
                    if (minClientVersionField.get(swordBlocking) == null) {
                        minClientVersionField.set(swordBlocking, packetEventsClientVersion("V_1_20_5"))
                    }
                }

                rightClickMainHand(player)
                delayTicks(1)

                runSync {
                    player.inventory.itemInOffHand.type shouldBe Material.SHIELD
                    hasConsumableComponent(player.inventory.itemInMainHand) shouldBe false
                }
            } finally {
                runSync {
                    packetEventsGetClientVersionField.set(swordBlocking, originalGetClientVersion)
                    minClientVersionField.set(swordBlocking, originalMinClientVersion)
                }
            }
        }

        test("middle-click in custom GUI does not mutate held sword components") {
            if (!paperConsumablePathAvailable()) {
                println("Skipping: Paper consumable component path unavailable")
                return@test
            }

            runSync {
                setModeset(player, "old")
                player.gameMode = GameMode.SURVIVAL
                player.inventory.setItemInMainHand(ItemStack(Material.DIAMOND_SWORD))
            }

            runSync {
                hasConsumableComponent(player.inventory.itemInMainHand) shouldBe false
            }

            val gui = runSync { Bukkit.createInventory(null, 9, "OCM Test GUI") }
            runSync {
                gui.setItem(0, ItemStack(Material.STONE))
            }

            val view = runSync { player.openInventory(gui) } ?: error("inventory view missing")
            try {
                val event =
                    runSync {
                        val click =
                            InventoryClickEvent(
                                view,
                                InventoryType.SlotType.CONTAINER,
                                0,
                                ClickType.MIDDLE,
                                InventoryAction.CLONE_STACK,
                            )
                        click.currentItem = gui.getItem(0)
                        click
                    }

                runSync { Bukkit.getPluginManager().callEvent(event) }
                delayTicks(1)

                runSync {
                    event.isCancelled shouldBe false
                    hasConsumableComponent(player.inventory.itemInMainHand) shouldBe false
                }
            } finally {
                runSync { player.closeInventory() }
            }
        }

        test("inventory drag in custom GUI does not rewrite top-inventory swords") {
            if (!paperConsumablePathAvailable()) {
                println("Skipping: Paper consumable component path unavailable")
                return@test
            }

            runSync {
                setModeset(player, "old")
                player.gameMode = GameMode.SURVIVAL
                player.inventory.setItemInMainHand(ItemStack(Material.STONE))
            }

            val gui = runSync { Bukkit.createInventory(null, 9, "OCM Drag GUI") }
            val topSword = runSync { craftMirrorStack(Material.DIAMOND_SWORD) }
            applyConsumableComponent(topSword)
            runSync {
                hasConsumableComponent(topSword) shouldBe true
                gui.setItem(0, topSword)
            }

            val view = runSync { player.openInventory(gui) } ?: error("inventory view missing")
            try {
                val drag =
                    runSync {
                        InventoryDragEvent(
                            view,
                            ItemStack(Material.CARROT),
                            ItemStack(Material.CARROT),
                            false,
                            mapOf(0 to ItemStack(Material.CARROT)),
                        )
                    }

                runSync { Bukkit.getPluginManager().callEvent(drag) }
                delayTicks(1)

                val afterTop = runSync { gui.getItem(0) }
                runSync {
                    drag.isCancelled shouldBe false
                    hasConsumableComponent(afterTop) shouldBe true
                }
            } finally {
                runSync { player.closeInventory() }
            }
        }

        test("stale deferred drag reapply does not taint newly selected main-hand sword") {
            if (!paperConsumablePathAvailable()) {
                println("Skipping: Paper consumable component path unavailable")
                return@test
            }

            runSync {
                setModeset(player, "old")
                player.gameMode = GameMode.SURVIVAL
                player.inventory.setItem(0, ItemStack(Material.DIAMOND_SWORD))
                player.inventory.setItem(1, ItemStack(Material.IRON_SWORD))
                player.inventory.heldItemSlot = 0
                player.updateInventory()
            }

            runSync {
                hasConsumableComponent(player.inventory.getItem(0)) shouldBe false
                hasConsumableComponent(player.inventory.getItem(1)) shouldBe false
            }

            val gui = runSync { Bukkit.createInventory(null, 9, "OCM Drag Stale Slot") }
            val view = runSync { player.openInventory(gui) } ?: error("inventory view missing")
            try {
                val drag =
                    runSync {
                        InventoryDragEvent(
                            view,
                            ItemStack(Material.CARROT),
                            ItemStack(Material.CARROT),
                            false,
                            mapOf(9 to ItemStack(Material.CARROT)),
                        )
                    }

                runSync { Bukkit.getPluginManager().callEvent(drag) }

                // Move to a different held slot before deferred next-tick reapply runs.
                runSync {
                    player.inventory.heldItemSlot = 1
                    player.updateInventory()
                }

                delayTicks(1)

                runSync {
                    player.inventory.heldItemSlot shouldBe 1
                    player.inventory.itemInMainHand.type shouldBe Material.IRON_SWORD
                    hasConsumableComponent(player.inventory.getItem(1)) shouldBe false
                }
            } finally {
                runSync { player.closeInventory() }
            }
        }

        test("stale deferred drag context does not mutate dragged bottom slot") {
            if (!paperConsumablePathAvailable()) {
                println("Skipping: Paper consumable component path unavailable")
                return@test
            }

            runSync {
                setModeset(player, "old")
                player.gameMode = GameMode.SURVIVAL
                player.inventory.setItem(0, ItemStack(Material.DIAMOND_SWORD))
                player.inventory.setItem(1, ItemStack(Material.IRON_SWORD))
                player.inventory.heldItemSlot = 0
                player.updateInventory()
            }

            val gui = runSync { Bukkit.createInventory(null, 9, "OCM Drag Cleanup Stale") }
            val view = runSync { player.openInventory(gui) } ?: error("inventory view missing")
            try {
                val draggedSword = runSync { craftMirrorStack(Material.DIAMOND_SWORD) }
                applyConsumableComponent(draggedSword)
                runSync {
                    hasConsumableComponent(draggedSword) shouldBe true
                    view.setItem(9, draggedSword)
                }

                val drag =
                    runSync {
                        InventoryDragEvent(
                            view,
                            ItemStack(Material.CARROT),
                            ItemStack(Material.CARROT),
                            false,
                            mapOf(9 to ItemStack(Material.CARROT)),
                        )
                    }

                runSync { Bukkit.getPluginManager().callEvent(drag) }

                // Move to a different held slot before deferred next-tick reapply runs.
                runSync {
                    player.inventory.heldItemSlot = 1
                    player.updateInventory()
                }

                delayTicks(1)

                val slotAfter = runSync { view.getItem(9) }
                runSync {
                    player.inventory.heldItemSlot shouldBe 1
                    hasConsumableComponent(player.inventory.itemInMainHand) shouldBe false
                    hasConsumableComponent(slotAfter) shouldBe true
                }
            } finally {
                runSync { player.closeInventory() }
            }
        }

        test("stale deferred drag reapply no-ops after inventory view change") {
            if (!paperConsumablePathAvailable()) {
                println("Skipping: Paper consumable component path unavailable")
                return@test
            }

            runSync {
                setModeset(player, "old")
                player.gameMode = GameMode.SURVIVAL
                player.inventory.setItem(0, ItemStack(Material.DIAMOND_SWORD))
                player.inventory.heldItemSlot = 0
                player.updateInventory()
            }

            runSync {
                hasConsumableComponent(player.inventory.itemInMainHand) shouldBe false
            }

            val firstGui = runSync { Bukkit.createInventory(null, 9, "OCM Drag First View") }
            val firstView = runSync { player.openInventory(firstGui) } ?: error("first inventory view missing")

            try {
                val drag =
                    runSync {
                        InventoryDragEvent(
                            firstView,
                            ItemStack(Material.CARROT),
                            ItemStack(Material.CARROT),
                            false,
                            mapOf(9 to ItemStack(Material.CARROT)),
                        )
                    }

                runSync { Bukkit.getPluginManager().callEvent(drag) }

                val secondGui = runSync { Bukkit.createInventory(null, 9, "OCM Drag Second View") }
                runSync {
                    player.openInventory(secondGui)
                }

                delayTicks(1)

                runSync {
                    player.openInventory.topInventory shouldBe secondGui
                    hasConsumableComponent(player.inventory.itemInMainHand) shouldBe false
                }
            } finally {
                runSync { player.closeInventory() }
            }
        }

        test("old client shield fallback restores offhand item on hotbar change") {
            if (!paperConsumablePathAvailable()) {
                println("Skipping: Paper consumable component path unavailable")
                return@test
            }

            runSync {
                setModeset(player, "old")
                player.gameMode = GameMode.SURVIVAL
                player.inventory.setItem(0, ItemStack(Material.DIAMOND_SWORD))
                player.inventory.setItem(1, ItemStack(Material.STONE))
                player.inventory.heldItemSlot = 0
                player.inventory.setItemInOffHand(ItemStack(Material.APPLE))
            }

            withPacketEventsClientVersion(player, "V_1_20_3") {
                rightClickMainHand(player)
                delayTicks(1)

                runSync {
                    player.inventory.itemInOffHand.type shouldBe Material.SHIELD
                }

                runSync {
                    player.inventory.heldItemSlot = 1
                    Bukkit.getPluginManager().callEvent(PlayerItemHeldEvent(player, 0, 1))
                }
                delayTicks(1)

                runSync {
                    player.inventory.itemInOffHand.type shouldBe Material.APPLE
                }
            }
        }

        test("legacy fallback does not cancel custom GUI shield-icon clicks") {
            if (!paperConsumablePathAvailable()) {
                println("Skipping: Paper consumable component path unavailable")
                return@test
            }

            runSync {
                setModeset(player, "old")
                player.gameMode = GameMode.SURVIVAL
                player.inventory.setItemInMainHand(ItemStack(Material.DIAMOND_SWORD))
                player.inventory.setItemInOffHand(ItemStack(Material.AIR))
            }

            withPacketEventsClientVersion(player, "V_1_20_3") {
                rightClickMainHand(player)
                delayTicks(1)

                runSync {
                    player.inventory.itemInOffHand.type shouldBe Material.SHIELD
                }

                val gui = runSync { Bukkit.createInventory(null, 9, "Shield GUI") }
                runSync {
                    gui.setItem(0, ItemStack(Material.SHIELD))
                }

                val view = runSync { player.openInventory(gui) } ?: error("inventory view missing")
                try {
                    val click =
                        runSync {
                            val event =
                                InventoryClickEvent(
                                    view,
                                    InventoryType.SlotType.CONTAINER,
                                    0,
                                    ClickType.LEFT,
                                    InventoryAction.PICKUP_ALL,
                                )
                            event.currentItem = gui.getItem(0)
                            event
                        }

                    runSync { Bukkit.getPluginManager().callEvent(click) }
                    delayTicks(1)

                    runSync {
                        click.isCancelled shouldBe false
                    }
                } finally {
                    runSync { player.closeInventory() }
                }
            }
        }

        test("legacy fallback does not cancel dropping unrelated shield items") {
            if (!paperConsumablePathAvailable()) {
                println("Skipping: Paper consumable component path unavailable")
                return@test
            }

            runSync {
                setModeset(player, "old")
                player.gameMode = GameMode.SURVIVAL
                player.inventory.setItemInMainHand(ItemStack(Material.DIAMOND_SWORD))
                player.inventory.setItemInOffHand(ItemStack(Material.AIR))
            }

            withPacketEventsClientVersion(player, "V_1_20_3") {
                rightClickMainHand(player)
                delayTicks(1)

                runSync {
                    player.inventory.itemInOffHand.type shouldBe Material.SHIELD
                }

                val dropped =
                    runSync {
                        player.world.dropItem(
                            player.location,
                            ItemStack(Material.SHIELD).apply {
                                itemMeta = itemMeta?.apply { setDisplayName("Unrelated Shield") }
                            },
                        )
                    }

                try {
                    val dropEvent = runSync { PlayerDropItemEvent(player, dropped) }
                    runSync { Bukkit.getPluginManager().callEvent(dropEvent) }

                    runSync {
                        dropEvent.isCancelled shouldBe false
                    }
                } finally {
                    runSync { dropped.remove() }
                }
            }
        }

        test("legacy fallback still blocks swapping temporary offhand shield") {
            if (!paperConsumablePathAvailable()) {
                println("Skipping: Paper consumable component path unavailable")
                return@test
            }

            runSync {
                setModeset(player, "old")
                player.gameMode = GameMode.SURVIVAL
                player.inventory.setItemInMainHand(ItemStack(Material.DIAMOND_SWORD))
                player.inventory.setItemInOffHand(ItemStack(Material.APPLE))
            }

            withPacketEventsClientVersion(player, "V_1_20_3") {
                rightClickMainHand(player)
                delayTicks(1)

                runSync {
                    player.inventory.itemInOffHand.type shouldBe Material.SHIELD
                }

                val swapEvent =
                    runSync {
                        PlayerSwapHandItemsEvent(
                            player,
                            player.inventory.itemInMainHand,
                            player.inventory.itemInOffHand,
                        )
                    }

                runSync { Bukkit.getPluginManager().callEvent(swapEvent) }

                runSync {
                    swapEvent.isCancelled shouldBe true
                }
            }
        }

        test("legacy death replaces only temporary shield drop and clears legacy state once") {
            if (!paperConsumablePathAvailable()) {
                println("Skipping: Paper consumable component path unavailable")
                return@test
            }

            runSync {
                setModeset(player, "old")
                player.gameMode = GameMode.SURVIVAL
                player.inventory.setItemInMainHand(ItemStack(Material.DIAMOND_SWORD))
                player.inventory.setItemInOffHand(ItemStack(Material.APPLE))
                player.updateInventory()
            }

            val storedItemsField = ModuleSwordBlocking::class.java.getDeclaredField("storedItems")
            val legacyStatesField = ModuleSwordBlocking::class.java.getDeclaredField("legacyStates")
            storedItemsField.isAccessible = true
            legacyStatesField.isAccessible = true

            withPacketEventsClientVersion(player, "V_1_20_3") {
                rightClickMainHand(player)
                delayTicks(1)

                @Suppress("UNCHECKED_CAST")
                val storedItems = runSync { storedItemsField.get(swordBlocking) as MutableMap<UUID, ItemStack> }

                runSync {
                    player.inventory.itemInOffHand.type shouldBe Material.SHIELD
                    storedItems.containsKey(player.uniqueId) shouldBe true
                    val legacyStates = legacyStatesField.get(swordBlocking) as Map<*, *>
                    legacyStates.containsKey(player.uniqueId) shouldBe true
                }

                val drops =
                    mutableListOf(
                        ItemStack(Material.SHIELD),
                        ItemStack(Material.SHIELD),
                    )
                val deathEvent = runSync { syntheticPlayerDeathEvent(player, drops) }

                runSync { Bukkit.getPluginManager().callEvent(deathEvent) }

                @Suppress("UNCHECKED_CAST")
                val dropsAfter = runSync { (deathEvent.drops as MutableList<ItemStack?>).toList() }

                runSync {
                    dropsAfter.any { it == null } shouldBe false
                    dropsAfter.count { it?.type == Material.APPLE } shouldBe 1
                    dropsAfter.count { it?.type == Material.SHIELD } shouldBe 1
                    storedItems.containsKey(player.uniqueId) shouldBe false
                    val legacyStates = legacyStatesField.get(swordBlocking) as Map<*, *>
                    legacyStates.containsKey(player.uniqueId) shouldBe false
                }
            }
        }

        test("legacy death with keepInventory does not rewrite drops and still clears state") {
            if (!paperConsumablePathAvailable()) {
                println("Skipping: Paper consumable component path unavailable")
                return@test
            }

            runSync {
                setModeset(player, "old")
                player.gameMode = GameMode.SURVIVAL
                player.inventory.setItemInMainHand(ItemStack(Material.DIAMOND_SWORD))
                player.inventory.setItemInOffHand(ItemStack(Material.APPLE))
                player.updateInventory()
            }

            val storedItemsField = ModuleSwordBlocking::class.java.getDeclaredField("storedItems")
            val legacyStatesField = ModuleSwordBlocking::class.java.getDeclaredField("legacyStates")
            storedItemsField.isAccessible = true
            legacyStatesField.isAccessible = true

            withPacketEventsClientVersion(player, "V_1_20_3") {
                rightClickMainHand(player)
                delayTicks(1)

                @Suppress("UNCHECKED_CAST")
                val storedItems = runSync { storedItemsField.get(swordBlocking) as MutableMap<UUID, ItemStack> }

                runSync {
                    player.inventory.itemInOffHand.type shouldBe Material.SHIELD
                    storedItems.containsKey(player.uniqueId) shouldBe true
                }

                val drops = mutableListOf(ItemStack(Material.SHIELD), ItemStack(Material.STONE))
                val deathEvent = runSync { syntheticPlayerDeathEvent(player, drops) }
                runSync {
                    deathEvent.keepInventory = true
                }

                runSync { Bukkit.getPluginManager().callEvent(deathEvent) }

                @Suppress("UNCHECKED_CAST")
                val dropsAfter = runSync { (deathEvent.drops as MutableList<ItemStack?>).toList() }

                runSync {
                    dropsAfter.map { it?.type } shouldBe listOf(Material.SHIELD, Material.STONE)
                    player.inventory.itemInOffHand.type shouldBe Material.APPLE
                    storedItems.containsKey(player.uniqueId) shouldBe false
                    val legacyStates = legacyStatesField.get(swordBlocking) as Map<*, *>
                    legacyStates.containsKey(player.uniqueId) shouldBe false
                }
            }
        }

        test("paper-animation swap restores stale stored offhand item before clearing legacy state") {
            if (!paperConsumablePathAvailable()) {
                println("Skipping: Paper consumable component path unavailable")
                return@test
            }

            runSync {
                setModeset(player, "old")
                player.gameMode = GameMode.SURVIVAL
                player.inventory.setItemInMainHand(ItemStack(Material.DIAMOND_SWORD))
                player.inventory.setItemInOffHand(ItemStack(Material.SHIELD))
                player.updateInventory()
            }

            val storedItemsField = ModuleSwordBlocking::class.java.getDeclaredField("storedItems")
            val legacyStatesField = ModuleSwordBlocking::class.java.getDeclaredField("legacyStates")
            storedItemsField.isAccessible = true
            legacyStatesField.isAccessible = true

            @Suppress("UNCHECKED_CAST")
            val storedItems = storedItemsField.get(swordBlocking) as MutableMap<UUID, ItemStack>

            withPacketEventsClientVersion(player, "V_1_21_11") {
                runSync {
                    storedItems[player.uniqueId] = ItemStack(Material.APPLE)
                }

                val swapEvent =
                    runSync {
                        PlayerSwapHandItemsEvent(
                            player,
                            player.inventory.itemInMainHand,
                            player.inventory.itemInOffHand,
                        )
                    }

                runSync { Bukkit.getPluginManager().callEvent(swapEvent) }

                runSync {
                    swapEvent.isCancelled shouldBe false
                    player.inventory.itemInOffHand.type shouldBe Material.APPLE
                    storedItems.containsKey(player.uniqueId) shouldBe false
                    val legacyStates = legacyStatesField.get(swordBlocking) as Map<*, *>
                    legacyStates.containsKey(player.uniqueId) shouldBe false
                }
            }
        }

        test("modeset change after disabled reload does not reapply sword consumable component") {
            if (!paperConsumablePathAvailable()) {
                println("Skipping: Paper consumable component path unavailable")
                return@test
            }

            runSync {
                setModeset(player, "old")
                player.gameMode = GameMode.SURVIVAL
                player.inventory.setItemInMainHand(craftMirrorStack(Material.DIAMOND_SWORD))
            }

            runSync {
                val main = player.inventory.itemInMainHand
                applyConsumableComponent(main)
                player.inventory.setItemInMainHand(main)
            }

            runSync {
                hasConsumableComponent(player.inventory.itemInMainHand) shouldBe true
            }

            withSwordBlockingDisabled {
                runSync {
                    setModeset(player, "new")
                    ModuleLoader.getModules().forEach { it.onModesetChange(player) }
                }

                runSync {
                    hasConsumableComponent(player.inventory.itemInMainHand) shouldBe false
                }

                runSync {
                    setModeset(player, "old")
                    ModuleLoader.getModules().forEach { it.onModesetChange(player) }
                }

                runSync {
                    swordBlocking.isEnabled(player) shouldBe false
                    hasConsumableComponent(player.inventory.itemInMainHand) shouldBe false
                }
            }
        }

        test("number-key hotbar swap keeps food consumable") {
            if (!paperConsumablePathAvailable()) {
                println("Skipping: Paper consumable component path unavailable")
                return@test
            }

            val view =
                runSync { player.openInventory(player.inventory) }
                    ?: error("inventory view missing")
            try {
                runSync {
                    player.inventory.setItem(0, ItemStack(Material.STONE))
                    player.inventory.setItem(2, ItemStack(Material.BREAD))
                }

                val hotbarItem = runSync { player.inventory.getItem(2) }
                assertNoConsumableRemoval(hotbarItem, "hotbar button food (before)")

                val event =
                    runSync {
                        val click =
                            InventoryClickEvent(
                                view,
                                InventoryType.SlotType.CONTAINER,
                                0,
                                ClickType.NUMBER_KEY,
                                InventoryAction.HOTBAR_SWAP,
                                2,
                            )
                        click.currentItem = player.inventory.getItem(0)
                        click
                    }

                runSync { Bukkit.getPluginManager().callEvent(event) }
                delayTicks(1)

                val after = runSync { player.inventory.getItem(2) }
                hasConsumableRemoval(after) shouldBe false
            } finally {
                runSync { player.closeInventory() }
            }
        }

        test("stale deferred swap reapply does not taint newly selected main-hand sword") {
            if (!paperConsumablePathAvailable()) {
                println("Skipping: Paper consumable component path unavailable")
                return@test
            }

            runSync {
                setModeset(player, "old")
                player.gameMode = GameMode.SURVIVAL
                val slot0Sword = craftMirrorStack(Material.DIAMOND_SWORD)
                applyConsumableComponent(slot0Sword)
                hasConsumableComponent(slot0Sword) shouldBe true
                player.inventory.setItem(0, slot0Sword)
                player.inventory.setItem(1, ItemStack(Material.IRON_SWORD))
                player.inventory.setItemInOffHand(ItemStack(Material.STICK))
                player.inventory.heldItemSlot = 0
                player.updateInventory()
            }

            runSync {
                hasConsumableComponent(player.inventory.getItem(1)) shouldBe false
            }

            val event =
                runSync {
                    PlayerSwapHandItemsEvent(player, player.inventory.itemInMainHand, player.inventory.itemInOffHand)
                }
            runSync { Bukkit.getPluginManager().callEvent(event) }
            runSync {
                val main = player.inventory.itemInMainHand
                val off = player.inventory.itemInOffHand
                player.inventory.setItemInMainHand(off)
                player.inventory.setItemInOffHand(main)
            }

            // Change held slot directly before deferred next-tick swap handling runs.
            runSync {
                player.inventory.heldItemSlot = 1
                player.updateInventory()
            }

            delayTicks(1)

            runSync {
                player.inventory.heldItemSlot shouldBe 1
                player.inventory.itemInMainHand.type shouldBe Material.IRON_SWORD
                hasConsumableComponent(player.inventory.getItem(1)) shouldBe false
                player.inventory.itemInOffHand.type shouldBe Material.DIAMOND_SWORD
                hasConsumableComponent(player.inventory.itemInOffHand) shouldBe false
            }
        }

        test("stale deferred swap reapply no-ops when inventory view changes, while offhand cleanup still runs") {
            if (!paperConsumablePathAvailable()) {
                println("Skipping: Paper consumable component path unavailable")
                return@test
            }

            runSync {
                setModeset(player, "old")
                player.gameMode = GameMode.SURVIVAL

                val mainSword = craftMirrorStack(Material.DIAMOND_SWORD)
                applyConsumableComponent(mainSword)
                hasConsumableComponent(mainSword) shouldBe true
                player.inventory.setItem(0, mainSword)

                val offhandSword = craftMirrorStack(Material.IRON_SWORD)
                applyConsumableComponent(offhandSword)
                hasConsumableComponent(offhandSword) shouldBe true
                player.inventory.setItemInOffHand(offhandSword)

                player.inventory.heldItemSlot = 0
                player.updateInventory()
            }

            val firstGui = runSync { Bukkit.createInventory(null, 9, "OCM Swap First View") }
            runSync {
                player.openInventory(firstGui)
            }

            val swapEvent =
                runSync {
                    PlayerSwapHandItemsEvent(player, player.inventory.itemInMainHand, player.inventory.itemInOffHand)
                }
            runSync { Bukkit.getPluginManager().callEvent(swapEvent) }
            runSync {
                val main = player.inventory.itemInMainHand
                val off = player.inventory.itemInOffHand
                player.inventory.setItemInMainHand(off)
                player.inventory.setItemInOffHand(main)
            }

            // Keep the same held slot but change the open view before deferred task runs.
            val secondGui = runSync { Bukkit.createInventory(null, 9, "OCM Swap Second View") }
            runSync {
                player.inventory.setItem(0, ItemStack(Material.GOLDEN_SWORD))
                player.openInventory(secondGui)
                player.updateInventory()
            }

            delayTicks(1)

            runSync {
                player.openInventory.topInventory shouldBe secondGui
                player.inventory.heldItemSlot shouldBe 0
                player.inventory.itemInMainHand.type shouldBe Material.GOLDEN_SWORD
                hasConsumableComponent(player.inventory.itemInMainHand) shouldBe false
                player.inventory.itemInOffHand.type shouldBe Material.DIAMOND_SWORD
                hasConsumableComponent(player.inventory.itemInOffHand) shouldBe false
            }

            runSync { player.closeInventory() }
        }

        test("swap hand keeps food consumable") {
            if (!paperConsumablePathAvailable()) {
                println("Skipping: Paper consumable component path unavailable")
                return@test
            }

            runSync {
                player.inventory.setItemInMainHand(ItemStack(Material.STONE))
                player.inventory.setItemInOffHand(ItemStack(Material.BREAD))
            }

            val offhandBefore = runSync { player.inventory.itemInOffHand }
            assertNoConsumableRemoval(offhandBefore, "offhand food (before)")

            val event =
                runSync {
                    PlayerSwapHandItemsEvent(player, player.inventory.itemInMainHand, player.inventory.itemInOffHand)
                }
            runSync { Bukkit.getPluginManager().callEvent(event) }
            runSync {
                val main = player.inventory.itemInMainHand
                val off = player.inventory.itemInOffHand
                player.inventory.setItemInMainHand(off)
                player.inventory.setItemInOffHand(main)
            }
            delayTicks(1)

            val mainAfter = runSync { player.inventory.itemInMainHand }
            val offAfter = runSync { player.inventory.itemInOffHand }
            val foodAfter = if (mainAfter.type == Material.BREAD) mainAfter else offAfter
            hasConsumableRemoval(foodAfter) shouldBe false
        }

        test("dropping food keeps consumable component") {
            if (!paperConsumablePathAvailable()) {
                println("Skipping: Paper consumable component path unavailable")
                return@test
            }

            val drop =
                runSync {
                    player.world.dropItem(player.location, ItemStack(Material.BREAD))
                }
            try {
                val before = runSync { drop.itemStack }
                assertNoConsumableRemoval(before, "dropped food (before)")

                runSync { Bukkit.getPluginManager().callEvent(PlayerDropItemEvent(player, drop)) }

                val after = runSync { drop.itemStack }
                hasConsumableRemoval(after) shouldBe false
            } finally {
                runSync { drop.remove() }
            }
        }

        test("world change keeps hand food consumable") {
            if (!paperConsumablePathAvailable()) {
                println("Skipping: Paper consumable component path unavailable")
                return@test
            }

            runSync {
                player.inventory.setItemInMainHand(ItemStack(Material.BREAD))
                player.inventory.setItemInOffHand(ItemStack(Material.CARROT))
            }

            val beforeMain = runSync { player.inventory.itemInMainHand }
            val beforeOff = runSync { player.inventory.itemInOffHand }
            assertNoConsumableRemoval(beforeMain, "main hand food (before world change)")
            assertNoConsumableRemoval(beforeOff, "offhand food (before world change)")

            runSync { Bukkit.getPluginManager().callEvent(PlayerChangedWorldEvent(player, player.world)) }

            val afterMain = runSync { player.inventory.itemInMainHand }
            val afterOff = runSync { player.inventory.itemInOffHand }
            hasConsumableRemoval(afterMain) shouldBe false
            hasConsumableRemoval(afterOff) shouldBe false
        }

        test("quit event keeps hand food consumable") {
            if (!paperConsumablePathAvailable()) {
                println("Skipping: Paper consumable component path unavailable")
                return@test
            }

            runSync {
                player.inventory.setItemInMainHand(ItemStack(Material.BREAD))
                player.inventory.setItemInOffHand(ItemStack(Material.CARROT))
            }

            val beforeMain = runSync { player.inventory.itemInMainHand }
            val beforeOff = runSync { player.inventory.itemInOffHand }
            assertNoConsumableRemoval(beforeMain, "main hand food (before quit)")
            assertNoConsumableRemoval(beforeOff, "offhand food (before quit)")

            runSync { Bukkit.getPluginManager().callEvent(PlayerQuitEvent(player, "test")) }

            val afterMain = runSync { player.inventory.itemInMainHand }
            val afterOff = runSync { player.inventory.itemInOffHand }
            hasConsumableRemoval(afterMain) shouldBe false
            hasConsumableRemoval(afterOff) shouldBe false
        }

        test("world change strips consumable component from hand and stored swords") {
            if (!paperConsumablePathAvailable()) {
                println("Skipping: Paper consumable component path unavailable")
                return@test
            }

            runSync {
                setModeset(player, "old")
                player.gameMode = GameMode.SURVIVAL
                val main = craftMirrorStack(Material.DIAMOND_SWORD)
                val off = craftMirrorStack(Material.IRON_SWORD)
                val stored = craftMirrorStack(Material.GOLDEN_SWORD)
                applyConsumableComponent(main)
                applyConsumableComponent(off)
                applyConsumableComponent(stored)
                player.inventory.setItemInMainHand(main)
                player.inventory.setItemInOffHand(off)
                player.inventory.setItem(2, stored)
                player.updateInventory()
            }

            withPacketEventsClientVersion(player, "V_1_21_11") {
                runSync {
                    hasConsumableComponent(player.inventory.itemInMainHand) shouldBe true
                    hasConsumableComponent(player.inventory.itemInOffHand) shouldBe true
                    hasConsumableComponent(player.inventory.getItem(2)) shouldBe true
                }

                runSync { Bukkit.getPluginManager().callEvent(PlayerChangedWorldEvent(player, player.world)) }

                runSync {
                    hasConsumableComponent(player.inventory.itemInMainHand) shouldBe false
                    hasConsumableComponent(player.inventory.itemInOffHand) shouldBe false
                    hasConsumableComponent(player.inventory.getItem(2)) shouldBe false
                }
            }
        }

        test("dropping sword strips consumable component from dropped stack") {
            if (!paperConsumablePathAvailable()) {
                println("Skipping: Paper consumable component path unavailable")
                return@test
            }

            val drop =
                runSync {
                    val droppedSword = craftMirrorStack(Material.DIAMOND_SWORD)
                    applyConsumableComponent(droppedSword)
                    player.world.dropItem(player.location, droppedSword)
                }
            try {
                withPacketEventsClientVersion(player, "V_1_21_11") {
                    runSync {
                        hasConsumableComponent(drop.itemStack) shouldBe true
                    }

                    runSync { Bukkit.getPluginManager().callEvent(PlayerDropItemEvent(player, drop)) }

                    runSync {
                        hasConsumableComponent(drop.itemStack) shouldBe false
                    }
                }
            } finally {
                runSync { drop.remove() }
            }
        }

        test("death event strips consumable component from sword drops") {
            if (!paperConsumablePathAvailable()) {
                println("Skipping: Paper consumable component path unavailable")
                return@test
            }

            val drops = mutableListOf(runSync { craftMirrorStack(Material.DIAMOND_SWORD) })
            runSync {
                applyConsumableComponent(drops[0])
                hasConsumableComponent(drops[0]) shouldBe true
            }

            withPacketEventsClientVersion(player, "V_1_21_11") {
                val deathEvent = runSync { syntheticPlayerDeathEvent(player, drops) }
                runSync { Bukkit.getPluginManager().callEvent(deathEvent) }

                runSync {
                    hasConsumableComponent(drops[0]) shouldBe false
                }
            }
        }

        test("held-slot transition strips previous sword and applies new sword consumable") {
            if (!paperConsumablePathAvailable()) {
                println("Skipping: Paper consumable component path unavailable")
                return@test
            }

            runSync {
                setModeset(player, "old")
                player.gameMode = GameMode.SURVIVAL
                val previous = craftMirrorStack(Material.DIAMOND_SWORD)
                val next = craftMirrorStack(Material.IRON_SWORD)
                applyConsumableComponent(previous)
                player.inventory.setItem(0, previous)
                player.inventory.setItem(1, next)
                player.inventory.heldItemSlot = 0
                player.updateInventory()
            }

            withPacketEventsClientVersion(player, "V_1_21_11") {
                runSync {
                    hasConsumableComponent(player.inventory.getItem(0)) shouldBe true
                    hasConsumableComponent(player.inventory.getItem(1)) shouldBe false
                }

                runSync { Bukkit.getPluginManager().callEvent(PlayerItemHeldEvent(player, 0, 1)) }

                runSync {
                    hasConsumableComponent(player.inventory.getItem(0)) shouldBe false
                    hasConsumableComponent(player.inventory.getItem(1)) shouldBe true
                }
            }
        }

        test("quit event strips consumable component from hand and stored swords") {
            if (!paperConsumablePathAvailable()) {
                println("Skipping: Paper consumable component path unavailable")
                return@test
            }

            runSync {
                setModeset(player, "old")
                player.gameMode = GameMode.SURVIVAL
                val main = craftMirrorStack(Material.DIAMOND_SWORD)
                val off = craftMirrorStack(Material.IRON_SWORD)
                val stored = craftMirrorStack(Material.GOLDEN_SWORD)
                applyConsumableComponent(main)
                applyConsumableComponent(off)
                applyConsumableComponent(stored)
                player.inventory.setItemInMainHand(main)
                player.inventory.setItemInOffHand(off)
                player.inventory.setItem(2, stored)
                player.updateInventory()
            }

            withPacketEventsClientVersion(player, "V_1_21_11") {
                runSync {
                    hasConsumableComponent(player.inventory.itemInMainHand) shouldBe true
                    hasConsumableComponent(player.inventory.itemInOffHand) shouldBe true
                    hasConsumableComponent(player.inventory.getItem(2)) shouldBe true
                }

                runSync { Bukkit.getPluginManager().callEvent(PlayerQuitEvent(player, "test")) }

                runSync {
                    hasConsumableComponent(player.inventory.itemInMainHand) shouldBe false
                    hasConsumableComponent(player.inventory.itemInOffHand) shouldBe false
                    hasConsumableComponent(player.inventory.getItem(2)) shouldBe false
                }
            }
        }

        test("join event strips consumable component from hand and stored swords") {
            if (!paperConsumablePathAvailable()) {
                println("Skipping: Paper consumable component path unavailable")
                return@test
            }

            runSync {
                setModeset(player, "old")
                player.gameMode = GameMode.SURVIVAL
                val main = craftMirrorStack(Material.DIAMOND_SWORD)
                val off = craftMirrorStack(Material.IRON_SWORD)
                val stored = craftMirrorStack(Material.GOLDEN_SWORD)
                applyConsumableComponent(main)
                applyConsumableComponent(off)
                applyConsumableComponent(stored)
                player.inventory.setItemInMainHand(main)
                player.inventory.setItemInOffHand(off)
                player.inventory.setItem(2, stored)
                player.updateInventory()
            }

            withPacketEventsClientVersion(player, "V_1_21_11") {
                runSync {
                    hasConsumableComponent(player.inventory.itemInMainHand) shouldBe true
                    hasConsumableComponent(player.inventory.itemInOffHand) shouldBe true
                    hasConsumableComponent(player.inventory.getItem(2)) shouldBe true
                }

                runSync { Bukkit.getPluginManager().callEvent(PlayerJoinEvent(player, "test")) }

                runSync {
                    hasConsumableComponent(player.inventory.itemInMainHand) shouldBe false
                    hasConsumableComponent(player.inventory.itemInOffHand) shouldBe false
                    hasConsumableComponent(player.inventory.getItem(2)) shouldBe false
                }
            }
        }

        test("modeset disable clears stale legacy fallback shield state") {
            if (!paperConsumablePathAvailable()) {
                println("Skipping: Paper consumable component path unavailable")
                return@test
            }

            runSync {
                setModeset(player, "old")
                player.gameMode = GameMode.SURVIVAL
                player.inventory.setItemInMainHand(ItemStack(Material.DIAMOND_SWORD))
                player.inventory.setItemInOffHand(ItemStack(Material.APPLE))
                player.updateInventory()
            }

            val storedItemsField = ModuleSwordBlocking::class.java.getDeclaredField("storedItems")
            val legacyStatesField = ModuleSwordBlocking::class.java.getDeclaredField("legacyStates")
            storedItemsField.isAccessible = true
            legacyStatesField.isAccessible = true

            withPacketEventsClientVersion(player, "V_1_20_3") {
                rightClickMainHand(player)
                delayTicks(1)

                @Suppress("UNCHECKED_CAST")
                val storedItems = runSync { storedItemsField.get(swordBlocking) as MutableMap<UUID, ItemStack> }

                runSync {
                    player.inventory.itemInOffHand.type shouldBe Material.SHIELD
                    storedItems.containsKey(player.uniqueId) shouldBe true
                }

                runSync {
                    setModeset(player, "new")
                    swordBlocking.isEnabled(player) shouldBe false
                    ModuleLoader.getModules().forEach { it.onModesetChange(player) }
                }

                runSync {
                    player.inventory.itemInOffHand.type shouldBe Material.APPLE
                    storedItems.containsKey(player.uniqueId) shouldBe false
                    val legacyStates = legacyStatesField.get(swordBlocking) as Map<*, *>
                    legacyStates.containsKey(player.uniqueId) shouldBe false
                }
            }
        }
    })
