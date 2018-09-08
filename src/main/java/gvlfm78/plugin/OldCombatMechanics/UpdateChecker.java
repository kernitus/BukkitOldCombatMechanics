package kernitus.plugin.OldCombatMechanics;

import kernitus.plugin.OldCombatMechanics.updater.BukkitUpdateSource;
import kernitus.plugin.OldCombatMechanics.updater.ModuleUpdateChecker;
import kernitus.plugin.OldCombatMechanics.updater.SpigotUpdateSource;
import kernitus.plugin.OldCombatMechanics.updater.UpdateSource;
import kernitus.plugin.OldCombatMechanics.utilities.Messenger;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

public class UpdateChecker {
    private UpdateSource updateSource;

    public UpdateChecker(OCMMain plugin, File pluginFile){
        switch(ModuleUpdateChecker.getMode()){
            case "spigot":
                this.updateSource = new SpigotUpdateSource();
                break;
            case "bukkit":
                this.updateSource = new BukkitUpdateSource(plugin, pluginFile);
                break;
            case "auto":
                if(Bukkit.getVersion().toLowerCase(Locale.ROOT).contains("spigot")){
                    this.updateSource = new SpigotUpdateSource();
                } else {
                    this.updateSource = new BukkitUpdateSource(plugin, pluginFile);
                }
        }
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