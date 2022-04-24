/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics;

import kernitus.plugin.OldCombatMechanics.updater.SpigotUpdateSource;
import kernitus.plugin.OldCombatMechanics.updater.UpdateSource;
import kernitus.plugin.OldCombatMechanics.utilities.Messenger;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.Objects;
import java.util.function.Consumer;

public class UpdateChecker {
    private UpdateSource updateSource;

    public UpdateChecker(OCMMain plugin, File pluginFile){
        this.updateSource = new SpigotUpdateSource();
    }

    public void sendUpdateMessages(CommandSender sender){
        if(sender instanceof Player){
            sendUpdateMessages(((Player) sender)::sendMessage);
        } else {
            sendUpdateMessages(Messenger::info);
        }
    }

    private void sendUpdateMessages(Consumer<String> target){//Sends messages to a player
        updateSource.getUpdateMessages().stream()
                .filter(Objects::nonNull)
                .filter(message -> !message.isEmpty())
                .forEach(target);
    }
}