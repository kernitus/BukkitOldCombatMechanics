/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.commands

import kernitus.plugin.OldCombatMechanics.ModuleLoader
import kernitus.plugin.OldCombatMechanics.OCMMain
import kernitus.plugin.OldCombatMechanics.module.OCMModule
import kernitus.plugin.OldCombatMechanics.utilities.Config
import kernitus.plugin.OldCombatMechanics.utilities.Messenger
import kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.function.Consumer

class OCMCommandHandler(private val plugin: OCMMain) : CommandExecutor {
    enum class Subcommand {
        reload, mode
    }

    private fun help(plugin: OCMMain, sender: CommandSender) {
        val description = plugin.description

        Messenger.sendNoPrefix(sender, ChatColor.DARK_GRAY.toString() + Messenger.HORIZONTAL_BAR)
        Messenger.sendNoPrefix(
            sender, "&6&lOldCombatMechanics&e by &ckernitus&e and &cRayzr522&e version &6${description.version}"
        )

        if (checkPermissions(sender, Subcommand.reload)) Messenger.sendNoPrefix(
            sender, "&eYou can use &c/ocm reload&e to reload the config file"
        )
        if (checkPermissions(sender, Subcommand.mode)) Messenger.sendNoPrefix(
            sender, Config.getConfig().getString(
                "mode-messages.message-usage"
            ) ?: "&4ERROR: &rmode-messages.message-usage string missing"
        )

        Messenger.sendNoPrefix(sender, ChatColor.DARK_GRAY.toString() + Messenger.HORIZONTAL_BAR)
    }

    private fun reload(sender: CommandSender) {
        Config.reload()
        Messenger.sendNoPrefix(sender, "&6&lOldCombatMechanics&e config file reloaded")
    }

    private fun mode(sender: CommandSender, args: Array<String>) {
        if (args.size < 2) {
            if (sender is Player) {
                val playerData = PlayerStorage.getPlayerData(sender.uniqueId)
                var modeName = playerData.getModesetForWorld(sender.world.uid)
                if (modeName.isNullOrEmpty()) modeName = "unknown"

                Messenger.send(
                    sender,
                    Config.getConfig().getString("mode-messages.mode-status")
                        ?: "&4ERROR: &rmode-messages.mode-status string missing",
                    modeName
                )
            }
            Messenger.send(
                sender,
                Config.getConfig().getString("mode-messages.message-usage")
                    ?: "&4ERROR: &rmode-messages.message-usage string missing"
            )
            return
        }

        val modesetName = args[1].lowercase()

        if (!Config.modesets.containsKey(modesetName)) {
            Messenger.send(
                sender,
                Config.getConfig().getString("mode-messages.invalid-modeset")
                    ?: "&4ERROR: &rmode-messages.invalid-modeset string missing"
            )
            return
        }

        var player: Player? = null
        if (args.size < 3) {
            if (sender is Player) {
                if (sender.hasPermission("oldcombatmechanics.mode.own")) player = sender
            } else {
                Messenger.send(
                    sender,
                    Config.getConfig().getString("mode-messages.invalid-player")
                        ?: "&4ERROR: &rmode-messages.invalid-player string missing"
                )
                return
            }
        } else if (sender.hasPermission("oldcombatmechanics.mode.others")) player = Bukkit.getPlayer(args[2])

        if (player == null) {
            Messenger.send(
                sender,
                Config.getConfig().getString("mode-messages.invalid-player")
                    ?: "&4ERROR: &rmode-messages.invalid-player string missing"
            )
            return
        }

        val worldId = player.world.uid
        val worldModesets = Config.worlds[worldId]

        // If modesets null it means not configured, so all are allowed
        if (worldModesets != null && !worldModesets.contains(modesetName)) { // Modeset not allowed in current world
            Messenger.send(
                sender,
                Config.getConfig().getString("mode-messages.invalid-modeset")
                    ?: "&4ERROR: &rmode-messages.invalid-modeset string missing"
            )
            return
        }

        val playerData = PlayerStorage.getPlayerData(player.uniqueId)
        playerData.setModesetForWorld(worldId, modesetName)
        PlayerStorage.setPlayerData(player.uniqueId, playerData)
        PlayerStorage.scheduleSave()

        Messenger.send(
            sender,
            Config.getConfig().getString("mode-messages.mode-set")
                ?: "&4ERROR: &rmode-messages.mode-set string missing",
            modesetName
        )

        // Re-apply things like attack speed and collision team
        val playerCopy: Player = player
        ModuleLoader.modules.forEach(Consumer { module: OCMModule -> module.onModesetChange(playerCopy) })
    }

    /*
    private void test(OCMMain plugin, CommandSender sender) {
        final Location location = sender instanceof Player ?
                ((Player) sender).getLocation() :
                sender.getServer().getWorlds().get(0).getSpawnLocation();

        new InGameTester(plugin).performTests(sender, location);
    }
     */
    override fun onCommand(sender: CommandSender, cmd: Command, label: String, args: Array<String>): Boolean {
        if (args.isEmpty()) {
            help(plugin, sender)
        } else {
            try {
                try {
                    val subcommand = Subcommand.valueOf(
                        args[0].lowercase()
                    )
                    if (checkPermissions(sender, subcommand, true)) {
                        when (subcommand) {
                            Subcommand.reload -> reload(sender)
                            Subcommand.mode -> mode(sender, args)
                        }
                    }
                } catch (e: IllegalArgumentException) {
                    throw CommandNotRecognisedException()
                }
            } catch (e: CommandNotRecognisedException) {
                Messenger.send(sender, "Subcommand not recognised!")
            }
        }
        return true
    }

    private class CommandNotRecognisedException : IllegalArgumentException()

    companion object {
        private const val NO_PERMISSION = "&cYou need the permission '%s' to do that!"

        @JvmOverloads
        fun checkPermissions(sender: CommandSender, subcommand: Subcommand, sendMessage: Boolean = false): Boolean {
            val hasPermission = sender.hasPermission("oldcombatmechanics.$subcommand")
            if (sendMessage && !hasPermission) Messenger.sendNoPrefix(
                sender, NO_PERMISSION, "oldcombatmechanics.$subcommand"
            )
            return hasPermission
        }
    }
}
