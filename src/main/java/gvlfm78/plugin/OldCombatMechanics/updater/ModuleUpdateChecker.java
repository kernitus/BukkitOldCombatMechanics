package gvlfm78.plugin.OldCombatMechanics.updater;

import gvlfm78.plugin.OldCombatMechanics.OCMMain;
import gvlfm78.plugin.OldCombatMechanics.UpdateChecker;
import gvlfm78.plugin.OldCombatMechanics.module.Module;
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

    public static String getMode() {
        return INSTANCE.module().getString("mode").toLowerCase();
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
