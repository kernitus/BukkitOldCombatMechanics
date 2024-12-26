/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics

import kernitus.plugin.OldCombatMechanics.updater.SpigetUpdateChecker
import kernitus.plugin.OldCombatMechanics.utilities.Config
import kernitus.plugin.OldCombatMechanics.utilities.Messenger
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector
import org.bukkit.ChatColor
import org.bukkit.entity.Player

class UpdateChecker(private val plugin: OCMMain) {
    private val updater = SpigetUpdateChecker()

    // We don't really want to auto update if the config is not going to be upgraded automatically
    private val autoDownload =
        Config.moduleSettingEnabled("update-checker", "auto-update") &&
                (Reflector.versionIsNewerOrEqualTo(1, 18, 1) ||
                        Config.getConfig().getBoolean("force-below-1-18-1-config-upgrade", false)
                        )


    fun performUpdate(player: Player? = null) {
        if (player != null) update { message: String -> player.sendMessage(message) }
        else update { message: String -> Messenger.info(message) }
    }

    private fun update(target: (String) -> Unit) {
        val messages: MutableList<String> = ArrayList()
        if (updater.isUpdateAvailable) {
            messages.add(ChatColor.BLUE.toString() + "An update for OldCombatMechanics to version " + updater.latestVersion + " is available!")
            if (!autoDownload) {
                messages.add(ChatColor.BLUE.toString() + "Click here to download it: " + ChatColor.GRAY + updater.updateURL)
            } else {
                messages.add(ChatColor.BLUE.toString() + "Downloading update: " + ChatColor.GRAY + updater.updateURL)
                try {
                    if (updater.downloadLatestVersion(plugin.server.updateFolderFile, plugin.file.name)) messages.add(
                        ChatColor.GREEN.toString() + "Update downloaded. Restart or reload server to enable new version."
                    )
                    else throw RuntimeException()
                } catch (e: Exception) {
                    messages.add(ChatColor.RED.toString() + "Error occurred while downloading update! Check console for more details")
                    e.printStackTrace()
                }
            }
        }
        messages.forEach(target)
    }
}