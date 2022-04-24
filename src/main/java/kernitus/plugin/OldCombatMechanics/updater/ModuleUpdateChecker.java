/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.updater;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.UpdateChecker;
import kernitus.plugin.OldCombatMechanics.module.Module;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.File;

public class ModuleUpdateChecker extends Module {
    private static Module INSTANCE;
    private File pluginFile;

    public ModuleUpdateChecker(OCMMain plugin, File pluginFile){
        super(plugin, "update-checker");
        INSTANCE = this;
        this.pluginFile = pluginFile;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLogin(PlayerJoinEvent e){
        final Player p = e.getPlayer();
        if(p.hasPermission("OldCombatMechanics.notify")){
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
                UpdateChecker updateChecker = new UpdateChecker(plugin, pluginFile);

                // Checking for updates
                updateChecker.sendUpdateMessages(p);
            }, 20L);
        }
    }
}
