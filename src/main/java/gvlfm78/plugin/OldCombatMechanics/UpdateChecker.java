package gvlfm78.plugin.OldCombatMechanics;

import gvlfm78.plugin.OldCombatMechanics.updater.SpigotUpdateSource;
import gvlfm78.plugin.OldCombatMechanics.updater.UpdateSource;
import gvlfm78.plugin.OldCombatMechanics.utilities.Messenger;
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