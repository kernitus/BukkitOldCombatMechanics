/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

@file:Suppress("ktlint:standard:package-name")

package kernitus.plugin.OldCombatMechanics

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kernitus.plugin.OldCombatMechanics.api.OldCombatMechanicsAPI
import kernitus.plugin.OldCombatMechanics.api.PlayerModesetChangeEvent
import kernitus.plugin.OldCombatMechanics.commands.OCMCommandCompleter
import kernitus.plugin.OldCombatMechanics.module.ModuleDisableOffHand
import kernitus.plugin.OldCombatMechanics.module.ModuleSwordBlocking
import kernitus.plugin.OldCombatMechanics.utilities.Config
import kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage
import kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage.getPlayerData
import kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage.setPlayerData
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.java.JavaPlugin
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.Callable

@OptIn(ExperimentalKotest::class)
class ModesetRulesIntegrationTest :
    FunSpec({
        val testPlugin = JavaPlugin.getPlugin(OCMTestMain::class.java)
        val ocm = JavaPlugin.getPlugin(OCMMain::class.java)
        val api = Bukkit.getServicesManager().getRegistration(OldCombatMechanicsAPI::class.java)?.provider
            ?: error("OldCombatMechanicsAPI service not registered")
        val module =
            ModuleLoader
                .getModules()
                .filterIsInstance<ModuleDisableOffHand>()
                .firstOrNull() ?: error("ModuleDisableOffHand not registered")
        val swordBlockingModule =
            ModuleLoader
                .getModules()
                .filterIsInstance<ModuleSwordBlocking>()
                .firstOrNull() ?: error("ModuleSwordBlocking not registered")
        val internalModules =
            setOf(
                "modeset-listener",
                "attack-cooldown-tracker",
                "entity-damage-listener"
            )
        val optionalModules =
            setOf(
                "disable-attack-sounds",
                "disable-sword-sweep-particles"
            )

        lateinit var player: Player
        lateinit var fakePlayer: FakePlayer

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
                        }
                    ).get()
            }
        }

        fun setModeset(
            player: Player,
            modeset: String
        ) {
            val playerData = getPlayerData(player.uniqueId)
            playerData.setModesetForWorld(player.world.uid, modeset)
            setPlayerData(player.uniqueId, playerData)
        }

        fun snapshotSection(path: String): Any? {
            val section = ocm.config.getConfigurationSection(path)
            return section?.getValues(false) ?: ocm.config.get(path)
        }

        fun restoreSection(
            path: String,
            value: Any?
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

        fun applyConfigWithWorlds(
            always: List<String>,
            disabled: List<String>,
            modesets: Map<String, List<String>>,
            worlds: Map<String, List<String>>
        ) {
            ocm.config.set("always_enabled_modules", always)
            ocm.config.set("disabled_modules", disabled)
            ocm.config.set("modesets", null)
            ocm.config.createSection("modesets", modesets)
            ocm.config.set("worlds", null)
            ocm.config.createSection("worlds", worlds)
            ocm.saveConfig()
            Config.reload()
        }

        fun applyConfig(
            always: List<String>,
            disabled: List<String>,
            modesets: Map<String, List<String>>,
            worldModesets: List<String>
        ) {
            applyConfigWithWorlds(
                always = always,
                disabled = disabled,
                modesets = modesets,
                worlds = mapOf("world" to worldModesets)
            )
        }

        fun completeAlways(
            always: List<String>,
            disabled: List<String>,
            modesets: Map<String, List<String>>
        ): List<String> {
            val assigned = HashSet<String>()
            always.forEach { assigned.add(it.lowercase(Locale.ROOT)) }
            disabled.forEach { assigned.add(it.lowercase(Locale.ROOT)) }
            modesets.values.flatten().forEach { assigned.add(it.lowercase(Locale.ROOT)) }

            val filled = LinkedHashSet<String>()
            filled.addAll(always)
            ModuleLoader
                .getModules()
                .map { it.configName.lowercase(Locale.ROOT) }
                .sorted()
                .filterNot { assigned.contains(it) }
                .filterNot { internalModules.contains(it) }
                .forEach { filled.add(it) }
            optionalModules
                .filterNot { assigned.contains(it) }
                .forEach { filled.add(it) }
            return filled.toList()
        }

        fun applySimpleModesetWorlds(worlds: Map<String, List<String>>) {
            val modesets =
                mapOf(
                    "old" to listOf("disable-offhand"),
                    "new" to listOf("old-potion-effects")
                )
            applyConfigWithWorlds(
                always =
                    completeAlways(
                        always = emptyList(),
                        disabled = emptyList(),
                        modesets = modesets
                    ),
                disabled = emptyList(),
                modesets = modesets,
                worlds = worlds
            )
        }

        fun clearPendingSaveTask() {
            val field = PlayerStorage::class.java.getDeclaredField("saveTask")
            field.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val saveTask = field.get(null) as AtomicReference<Any?>
            val pending = saveTask.getAndSet(null)
            pending?.javaClass?.getMethod("cancel")?.invoke(pending)
        }

        suspend fun withConfig(block: suspend () -> Unit) {
            val originalAlways = ocm.config.get("always_enabled_modules")
            val originalDisabled = ocm.config.get("disabled_modules")
            val originalModesets = snapshotSection("modesets")
            val originalWorlds = snapshotSection("worlds")

            try {
                block()
            } finally {
                runSync {
                    ocm.config.set("always_enabled_modules", originalAlways)
                    ocm.config.set("disabled_modules", originalDisabled)
                    restoreSection("modesets", originalModesets)
                    restoreSection("worlds", originalWorlds)
                    ocm.saveConfig()
                    Config.reload()
                }
            }
        }

        beforeSpec {
            runSync {
                val world = checkNotNull(Bukkit.getServer().getWorld("world"))
                fakePlayer = FakePlayer(testPlugin)
                fakePlayer.spawn(Location(world, 0.0, 100.0, 0.0))
                player = checkNotNull(Bukkit.getPlayer(fakePlayer.uuid))
            }
        }

        afterSpec {
            runSync {
                fakePlayer.removePlayer()
            }
        }

        test("always-enabled modules apply regardless of modeset") {
            withConfig {
                runSync {
                    applyConfig(
                        always =
                            completeAlways(
                                always = listOf("disable-offhand"),
                                disabled = emptyList(),
                                modesets =
                                    mapOf(
                                        "old" to listOf("old-golden-apples"),
                                        "new" to listOf("old-potion-effects")
                                    )
                            ),
                        disabled = emptyList(),
                        modesets =
                            mapOf(
                                "old" to listOf("old-golden-apples"),
                                "new" to listOf("old-potion-effects")
                            ),
                        worldModesets = listOf("old", "new")
                    )

                    setModeset(player, "old")
                    module.isEnabled(player).shouldBeTrue()

                    setModeset(player, "new")
                    module.isEnabled(player).shouldBeTrue()
                }
            }
        }

        test("disabled modules never apply") {
            withConfig {
                runSync {
                    applyConfig(
                        always =
                            completeAlways(
                                always = emptyList(),
                                disabled = listOf("disable-offhand"),
                                modesets =
                                    mapOf(
                                        "old" to listOf("old-golden-apples"),
                                        "new" to listOf("old-potion-effects")
                                    )
                            ),
                        disabled = listOf("disable-offhand"),
                        modesets =
                            mapOf(
                                "old" to listOf("old-golden-apples"),
                                "new" to listOf("old-potion-effects")
                            ),
                        worldModesets = listOf("old", "new")
                    )

                    setModeset(player, "old")
                    module.isEnabled(player).shouldBeFalse()

                    setModeset(player, "new")
                    module.isEnabled(player).shouldBeFalse()
                }
            }
        }

        test("modeset membership controls module activation") {
            withConfig {
                runSync {
                    applyConfig(
                        always =
                            completeAlways(
                                always = emptyList(),
                                disabled = emptyList(),
                                modesets =
                                    mapOf(
                                        "old" to listOf("disable-offhand"),
                                        "new" to listOf("old-potion-effects")
                                    )
                            ),
                        disabled = emptyList(),
                        modesets =
                            mapOf(
                                "old" to listOf("disable-offhand"),
                                "new" to listOf("old-potion-effects")
                            ),
                        worldModesets = listOf("old", "new")
                    )

                    setModeset(player, "old")
                    module.isEnabled(player).shouldBeTrue()

                    setModeset(player, "new")
                    module.isEnabled(player).shouldBeFalse()
                }
            }
        }

        test("stale stored modeset is not trusted when current world only allows another modeset") {
            withConfig {
                runSync {
                    applyConfig(
                        always =
                            completeAlways(
                                always = emptyList(),
                                disabled = emptyList(),
                                modesets =
                                    mapOf(
                                        "old" to listOf("sword-blocking"),
                                        "new" to listOf("old-potion-effects")
                                    )
                            ),
                        disabled = emptyList(),
                        modesets =
                            mapOf(
                                "old" to listOf("sword-blocking"),
                                "new" to listOf("old-potion-effects")
                            ),
                        worldModesets = listOf("new")
                    )

                    setModeset(player, "old")
                    Bukkit.getPluginManager().callEvent(PlayerJoinEvent(player, ""))

                    getPlayerData(player.uniqueId).getModesetForWorld(player.world.uid) shouldBe "new"
                    swordBlockingModule.isEnabled(player).shouldBeFalse()
                }
            }
        }

        test("listed world modesets override default world modesets") {
            withConfig {
                runSync {
                    applySimpleModesetWorlds(
                        mapOf(
                            "__default__" to listOf("old"),
                            "world" to listOf("new", "old")
                        )
                    )

                    Config.getAllowedModesets(player.world.uid).toList().shouldContainExactly("new", "old")
                    Config.getDefaultModeset(player.world.uid) shouldBe Config.getModesets()["new"]
                }
            }
        }

        test("unlisted worlds use default world modesets") {
            withConfig {
                runSync {
                    applySimpleModesetWorlds(mapOf("__default__" to listOf("old")))

                    Config.getAllowedModesets(player.world.uid).toList().shouldContainExactly("old")
                    Config.getDefaultModeset(player.world.uid) shouldBe Config.getModesets()["old"]
                }
            }
        }

        test("empty listed world modesets remain unrestricted despite default world modesets") {
            withConfig {
                runSync {
                    applySimpleModesetWorlds(
                        mapOf(
                            "__default__" to listOf("old"),
                            "world" to emptyList()
                        )
                    )

                    val allowedModesets = Config.getAllowedModesets(player.world.uid)
                    allowedModesets.shouldContain("old")
                    allowedModesets.shouldContain("new")
                    Config.getDefaultModeset(player.world.uid) shouldBe null
                }
            }
        }

        test("mode command and tab completion use default world modesets") {
            withConfig {
                runSync {
                    applySimpleModesetWorlds(mapOf("__default__" to listOf("old")))
                    player.addAttachment(ocm, "oldcombatmechanics.commands", true)
                    player.addAttachment(ocm, "oldcombatmechanics.mode", true)
                    player.addAttachment(ocm, "oldcombatmechanics.mode.own", true)

                    val command = checkNotNull(Bukkit.getPluginCommand("oldcombatmechanics"))
                    val completions = OCMCommandCompleter().onTabComplete(player, command, "ocm", arrayOf("mode", ""))
                    completions.shouldContainExactly("old")

                    setModeset(player, "old")
                    Bukkit.dispatchCommand(player, "ocm mode new")
                    getPlayerData(player.uniqueId).getModesetForWorld(player.world.uid) shouldBe "old"
                }
            }
        }

        test("modeset API stores valid modesets and normalises names") {
            withConfig {
                runSync {
                    applySimpleModesetWorlds(mapOf("world" to listOf("old", "new")))
                    val events = mutableListOf<PlayerModesetChangeEvent>()
                    val listener = object : Listener {
                        @EventHandler
                        fun onModesetChange(event: PlayerModesetChangeEvent) {
                            if (event.player.uniqueId == player.uniqueId) {
                                events += event
                            }
                        }
                    }
                    Bukkit.getPluginManager().registerEvents(listener, testPlugin)

                    try {
                        setModeset(player, "old")
                        api.getModesetNames().toList().shouldContainExactly("old", "new")
                        api.getAllowedModesets(player.world).toList().shouldContainExactly("old", "new")

                        api.setModesetForPlayer(player, "new")
                        api.getModesetForPlayer(player) shouldBe "new"
                        getPlayerData(player.uniqueId).getModesetForWorld(player.world.uid) shouldBe "new"
                        events.single().previousModeset shouldBe "old"
                        events.single().newModeset shouldBe "new"
                        events.single().reason shouldBe PlayerModesetChangeEvent.Reason.API

                        events.clear()
                        api.setModesetForPlayer(player, "new")
                        events shouldBe emptyList()

                        api.setModesetForPlayer(player, "OLD")
                        api.getModesetForPlayer(player) shouldBe "old"
                        getPlayerData(player.uniqueId).getModesetForWorld(player.world.uid) shouldBe "old"
                        events.single().previousModeset shouldBe "new"
                        events.single().newModeset shouldBe "old"
                        events.single().reason shouldBe PlayerModesetChangeEvent.Reason.API
                    } finally {
                        HandlerList.unregisterAll(listener)
                    }
                }
            }
        }

        test("mode command emits command-reason modeset change events") {
            withConfig {
                runSync {
                    applySimpleModesetWorlds(mapOf("world" to listOf("old", "new")))
                    player.addAttachment(ocm, "oldcombatmechanics.commands", true)
                    player.addAttachment(ocm, "oldcombatmechanics.mode", true)
                    player.addAttachment(ocm, "oldcombatmechanics.mode.own", true)
                    setModeset(player, "old")
                    val events = mutableListOf<PlayerModesetChangeEvent>()
                    val listener = object : Listener {
                        @EventHandler
                        fun onModesetChange(event: PlayerModesetChangeEvent) {
                            if (event.player.uniqueId == player.uniqueId) {
                                events += event
                            }
                        }
                    }
                    Bukkit.getPluginManager().registerEvents(listener, testPlugin)

                    try {
                        Bukkit.dispatchCommand(player, "ocm mode new") shouldBe true
                        getPlayerData(player.uniqueId).getModesetForWorld(player.world.uid) shouldBe "new"
                        events.single().previousModeset shouldBe "old"
                        events.single().newModeset shouldBe "new"
                        events.single().reason shouldBe PlayerModesetChangeEvent.Reason.COMMAND

                        events.clear()
                        Bukkit.dispatchCommand(player, "ocm mode new") shouldBe true
                        events shouldBe emptyList()
                    } finally {
                        HandlerList.unregisterAll(listener)
                    }
                }
            }
        }

        test("modeset API rejects modesets disallowed in the player world") {
            withConfig {
                runSync {
                    applySimpleModesetWorlds(mapOf("world" to listOf("old")))
                    setModeset(player, "old")

                    shouldThrow<IllegalArgumentException> {
                        api.setModesetForPlayer(player, "new")
                    }

                    api.getModesetForPlayer(player) shouldBe "old"
                    getPlayerData(player.uniqueId).getModesetForWorld(player.world.uid) shouldBe "old"
                }
            }
        }

        test("modeset API no-op set does not schedule a player data save") {
            withConfig {
                runSync {
                    applySimpleModesetWorlds(mapOf("world" to listOf("old", "new")))
                    setModeset(player, "old")
                    clearPendingSaveTask()

                    api.setModesetForPlayer(player, "old")

                    val field = PlayerStorage::class.java.getDeclaredField("saveTask")
                    field.isAccessible = true
                    @Suppress("UNCHECKED_CAST")
                    val saveTask = field.get(null) as AtomicReference<Any?>
                    saveTask.get() shouldBe null
                    api.getModesetForPlayer(player) shouldBe "old"
                }
            }
        }

        test("modules in disabled and another list fail reload") {
            withConfig {
                runSync {
                    shouldThrow<IllegalStateException> {
                        applyConfig(
                            always =
                                completeAlways(
                                    always = listOf("disable-offhand"),
                                    disabled = listOf("disable-offhand"),
                                    modesets =
                                        mapOf(
                                            "old" to listOf("old-potion-effects"),
                                            "new" to listOf("old-golden-apples")
                                        )
                                ),
                            disabled = listOf("disable-offhand"),
                            modesets =
                                mapOf(
                                    "old" to listOf("old-potion-effects"),
                                    "new" to listOf("old-golden-apples")
                                ),
                            worldModesets = listOf("old", "new")
                        )
                    }
                }
            }
        }

        test("modules missing from all lists fail reload") {
            withConfig {
                runSync {
                    val moduleNames =
                        (
                            ModuleLoader
                                .getModules()
                                .map { it.configName }
                                .filterNot { internalModules.contains(it) } + optionalModules
                        ).distinct()
                            .sorted()
                    val missing = moduleNames.firstOrNull() ?: error("No modules registered")
                    val always = moduleNames.filterNot { it == missing }

                    shouldThrow<IllegalStateException> {
                        applyConfig(
                            always = always,
                            disabled = emptyList(),
                            modesets = mapOf("old" to emptyList()),
                            worldModesets = listOf("old")
                        )
                    }
                }
            }
        }
    })
