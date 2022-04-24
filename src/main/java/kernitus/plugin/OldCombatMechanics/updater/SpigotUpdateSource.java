/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.updater;

import org.bukkit.ChatColor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SpigotUpdateSource implements UpdateSource {
    private SpigetUpdateChecker updater;

    public SpigotUpdateSource(){
        updater = new SpigetUpdateChecker();
    }

    @Override
    public List<String> getUpdateMessages(){
        if(updater.isUpdateAvailable()){
            return Arrays.asList(
                    ChatColor.BLUE + "An update for OldCombatMechanics to version " + updater.getLatestVersion() + " is available!",
                    ChatColor.BLUE + "Click here to download it: " + ChatColor.GRAY + updater.getUpdateURL());
        }

        return Collections.emptyList();
    }
}
