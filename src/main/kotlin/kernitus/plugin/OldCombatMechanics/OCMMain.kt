/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics

import com.comphenix.protocol.ProtocolLibrary
import com.comphenix.protocol.ProtocolManager
import kernitus.plugin.OldCombatMechanics.ModuleLoader.addModule
import kernitus.plugin.OldCombatMechanics.ModuleLoader.initialise
import kernitus.plugin.OldCombatMechanics.commands.OCMCommandCompleter
import kernitus.plugin.OldCombatMechanics.commands.OCMCommandHandler
import kernitus.plugin.OldCombatMechanics.hooks.PlaceholderAPIHook
import kernitus.plugin.OldCombatMechanics.hooks.api.Hook
import kernitus.plugin.OldCombatMechanics.module.*
import kernitus.plugin.OldCombatMechanics.updater.ModuleUpdateChecker
import kernitus.plugin.OldCombatMechanics.utilities.Config
import kernitus.plugin.OldCombatMechanics.utilities.Messenger
import kernitus.plugin.OldCombatMechanics.utilities.damage.AttackCooldownTracker
import kernitus.plugin.OldCombatMechanics.utilities.damage.EntityDamageByEntityListener
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector
import kernitus.plugin.OldCombatMechanics.utilities.storage.ModesetListener
import kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage
import org.bstats.bukkit.Metrics
import org.bstats.charts.SimpleBarChart
import org.bstats.charts.SimplePie
import org.bukkit.Bukkit
import org.bukkit.entity.HumanEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventException
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.RegisteredListener
import org.bukkit.plugin.java.JavaPlugin

class OCMMain : JavaPlugin() {
    private val logger = getLogger()
    private val configHandler = OCMConfigHandler(this)
    private val disableListeners: MutableList<Runnable> = ArrayList()
    private val enableListeners: MutableList<Runnable> = ArrayList()
    private val hooks: MutableList<Hook> = ArrayList()
    var protocolManager: ProtocolManager? = null
        private set
    var attackCooldownTracker: AttackCooldownTracker? = null
        private set

    companion object {
        lateinit var instance: OCMMain
            private set

        val version: String
            get() = instance.description.version
    }

    override fun onEnable() {
        instance = this

        // Setting up config.yml
        configHandler.setupConfigIfNotPresent()

        // Initialise persistent player storage
        PlayerStorage.initialise(this)

        // Initialise ModuleLoader utility
        initialise(this)

        // Initialise Config utility
        Config.initialise(this)

        // Initialise the Messenger utility
        Messenger.initialise(this)

        try {
            if (server.pluginManager.getPlugin("ProtocolLib")?.isEnabled == true)
                protocolManager = ProtocolLibrary.getProtocolManager()
        } catch (e: Exception) {
            Messenger.warn("No ProtocolLib detected, some features might be disabled")
        }

        // Register all the modules
        registerModules()

        // Register all hooks for integrating with other plugins
        registerHooks()

        // Initialise all the hooks
        hooks.forEach { it.init(this) }

        // Set up the command handler
        getCommand("OldCombatMechanics")!!.setExecutor(OCMCommandHandler(this))
        // Set up command tab completer
        getCommand("OldCombatMechanics")!!.tabCompleter = OCMCommandCompleter()

        Config.reload()

        // BStats Metrics
        val metrics = Metrics(this, 53)

        // Simple bar chart
        metrics.addCustomChart(
            SimpleBarChart(
                "enabled_modules"
            ) {
                ModuleLoader.modules
                    .filter { it.isEnabled() }
                    .associate { it.toString() to 1 }
            }
        )

        // Pie chart of enabled/disabled for each module
        ModuleLoader.modules.forEach { module: OCMModule ->
            metrics.addCustomChart(
                SimplePie(
                    module.moduleName + "_pie"
                ) { if (module.isEnabled()) "enabled" else "disabled" })
        }

        enableListeners.forEach { it.run() }

        // Properly handle Plugman load/unload.
        val joinListeners = PlayerJoinEvent.getHandlerList().registeredListeners
            .filter { it.plugin == this }

        Bukkit.getOnlinePlayers().forEach { player: Player ->
            val event = PlayerJoinEvent(player, "")
            // Trick all the modules into thinking the player just joined in case the plugin was loaded with Plugman.
            // This way attack speeds, item modifications, etc. will be applied immediately instead of after a re-log.
            joinListeners.forEach({ registeredListener: RegisteredListener ->
                try {
                    registeredListener.callEvent(event)
                } catch (e: EventException) {
                    e.printStackTrace()
                }
            })
        }

        // Logging to console the enabling of OCM
        val pdfFile = this.description
        logger.info(pdfFile.name + " v" + pdfFile.version + " has been enabled")

        if (Config.moduleEnabled("update-checker")) Bukkit.getScheduler().runTaskLaterAsynchronously(
            this,
            Runnable { UpdateChecker(this).performUpdate() }, 20L
        )

        metrics.addCustomChart(
            SimplePie(
                "auto_update_pie"
            ) {
                if (Config.moduleSettingEnabled(
                        "update-checker",
                        "auto-update"
                    )
                ) "enabled" else "disabled"
            })
    }

    override fun onDisable() {
        val pdfFile = this.description

        disableListeners.forEach { it.run() }

        // Properly handle Plugman load/unload.
        val quitListeners = PlayerQuitEvent.getHandlerList().registeredListeners
            .filter { it.plugin == this }

        // Trick all the modules into thinking the player just quit in case the plugin was unloaded with Plugman.
        // This way attack speeds, item modifications, etc. will be restored immediately instead of after a disconnect.
        Bukkit.getOnlinePlayers().forEach { player: Player ->
            val event = PlayerQuitEvent(player, "")
            quitListeners.forEach({ registeredListener: RegisteredListener ->
                try {
                    registeredListener.callEvent(event)
                } catch (e: EventException) {
                    e.printStackTrace()
                }
            })
        }

        PlayerStorage.instantSave()

        // Logging to console the disabling of OCM
        logger.info(pdfFile.name + " v" + pdfFile.version + " has been disabled")
    }

    private fun registerModules() {
        // Update Checker (also a module, so we can use the dynamic registering/unregistering)
        addModule(ModuleUpdateChecker(this))

        // Modeset listener, for when player joins or changes world
        addModule(ModesetListener(this))

        // Module listeners
        addModule(ModuleAttackCooldown(this))

        // If below 1.16, we need to keep track of player attack cooldown ourselves
        if (Reflector.getMethod(HumanEntity::class.java, "getAttackCooldown", 0) == null) {
            val tracker = AttackCooldownTracker(this)
            attackCooldownTracker = tracker
            addModule(tracker)
        }

        //Listeners registered later with same priority are called later

        //These four listen to OCMEntityDamageByEntityEvent:
        addModule(ModuleOldToolDamage(this))
        addModule(ModuleSwordSweep(this))
        addModule(ModuleOldPotionEffects(this))
        addModule(ModuleOldCriticalHits(this))

        //Next block are all on LOWEST priority, so will be called in the following order:
        // Damage order: base -> potion effects -> critical hit -> enchantments
        // Defence order: overdamage -> blocking -> armour -> resistance -> armour enchs -> absorption
        //EntityDamageByEntityListener calls OCMEntityDamageByEntityEvent, see modules above
        // For everything from base to overdamage
        addModule(EntityDamageByEntityListener(this))
        // ModuleSwordBlocking to calculate blocking
        addModule(ModuleShieldDamageReduction(this))
        // OldArmourStrength for armour -> resistance -> armour enchs -> absorption
        addModule(ModuleOldArmourStrength(this))

        addModule(ModuleSwordBlocking(this))
        addModule(ModuleOldArmourDurability(this))

        addModule(ModuleGoldenApple(this))
        addModule(ModuleFishingKnockback(this))
        addModule(ModulePlayerKnockback(this))
        addModule(ModulePlayerRegen(this))

        addModule(ModuleDisableCrafting(this))
        addModule(ModuleDisableOffHand(this))
        addModule(ModuleOldBrewingStand(this))
        addModule(ModuleProjectileKnockback(this))
        addModule(ModuleDisableEnderpearlCooldown(this))
        addModule(ModuleChorusFruit(this))

        addModule(ModuleOldBurnDelay(this))
        addModule(ModuleAttackFrequency(this))
        addModule(ModuleFishingRodVelocity(this))

        // These modules require ProtocolLib
        if (protocolManager != null) {
            addModule(ModuleAttackSounds(this))
            addModule(ModuleSwordSweepParticles(this))
        } else {
            Messenger.warn("No ProtocolLib detected, attack-sounds and sword-sweep-particles modules will be disabled")
        }
    }

    private fun registerHooks() {
        if (server.pluginManager.isPluginEnabled("PlaceholderAPI")) hooks.add(PlaceholderAPIHook())
    }

    fun upgradeConfig() = configHandler.upgradeConfig()

    fun doesConfigExist() = configHandler.doesConfigExist()

    public override fun getFile() = super.getFile()
}
