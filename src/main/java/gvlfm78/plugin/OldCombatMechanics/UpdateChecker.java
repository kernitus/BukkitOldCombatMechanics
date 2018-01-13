package gvlfm78.plugin.OldCombatMechanics;

import gvlfm78.plugin.OldCombatMechanics.updater.BukkitUpdateSource;
import gvlfm78.plugin.OldCombatMechanics.updater.ModuleUpdateChecker;
import gvlfm78.plugin.OldCombatMechanics.updater.SpigotUpdateSource;
import gvlfm78.plugin.OldCombatMechanics.updater.UpdateSource;
import gvlfm78.plugin.OldCombatMechanics.utilities.Messenger;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.Objects;
import java.util.function.Consumer;

public class UpdateChecker {
    private UpdateSource updateSource;

    public UpdateChecker(OCMMain plugin, File pluginFile) {
        switch (ModuleUpdateChecker.INSTANCE.module().getString("mode").toLowerCase()) {
            case "spigot":
                this.updateSource = new SpigotUpdateSource(plugin);
                break;
            case "bukkit":
                this.updateSource = new BukkitUpdateSource(plugin, pluginFile);
                break;
            case "auto":
                if (Bukkit.getVersion().toLowerCase().contains("spigot")) {
                    this.updateSource = new SpigotUpdateSource(plugin);
                } else {
                    this.updateSource = new BukkitUpdateSource(plugin, pluginFile);
                }
        }
    }

    public void sendUpdateMessages(CommandSender sender) {
        if (sender instanceof Player) {
            sendUpdateMessages(((Player) sender)::sendMessage);
        } else {
            sendUpdateMessages(Messenger::info);
        }
    }

    private void sendUpdateMessages(Consumer<String> target) {//Sends messages to a player
        updateSource.getUpdateMessages().stream()
                .filter(Objects::nonNull)
                .filter(message -> !message.isEmpty())
                .forEach(target);
    }
}