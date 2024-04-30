/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics;

import kernitus.plugin.OldCombatMechanics.updater.SpigetUpdateChecker;
import kernitus.plugin.OldCombatMechanics.utilities.Config;
import kernitus.plugin.OldCombatMechanics.utilities.Messenger;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class UpdateChecker {
    private final SpigetUpdateChecker updater;
    private final boolean autoDownload;
    private final OCMMain plugin;

    public UpdateChecker(OCMMain plugin) {
        updater = new SpigetUpdateChecker();
        this.plugin = plugin;
        // We don't really want to auto update if the config is not going to be upgraded automatically
        autoDownload = Config.moduleSettingEnabled("update-checker", "auto-update") &&
                (Reflector.versionIsNewerOrEqualTo(1, 18, 1) ||
                        Config.getConfig().getBoolean("force-below-1-18-1-config-upgrade", false)
                );
    }


    public void performUpdate() {
        performUpdate(null);
    }

    public void performUpdate(@Nullable Player player) {
        if (player != null)
            update(player::sendMessage);
        else
            update(Messenger::info);
    }

    private void update(Consumer<String> target) {
        final List<String> messages = new ArrayList<>();
        if (updater.isUpdateAvailable()) {
            messages.add(ChatColor.BLUE + "An update for OldCombatMechanics to version " + updater.getLatestVersion() + " is available!");
            if (!autoDownload) {
                messages.add(ChatColor.BLUE + "Click here to download it: " + ChatColor.GRAY + updater.getUpdateURL());
            } else {
                messages.add(ChatColor.BLUE + "Downloading update: " + ChatColor.GRAY + updater.getUpdateURL());
                try {
                    if (updater.downloadLatestVersion(plugin.getServer().getUpdateFolderFile(), plugin.getFile().getName()))
                        messages.add(ChatColor.GREEN + "Update downloaded. Restart or reload server to enable new version.");
                    else throw new RuntimeException();
                } catch (Exception e) {
                    messages.add(ChatColor.RED + "Error occurred while downloading update! Check console for more details");
                    e.printStackTrace();
                }
            }
        }

        messages.forEach(target);
    }
}