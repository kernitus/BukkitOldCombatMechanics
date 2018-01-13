package kernitus.plugin.OldCombatMechanics.updater;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import net.gravitydevelopment.updater.Updater;
import org.bukkit.ChatColor;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class BukkitUpdateSource implements UpdateSource {
    private Updater updater;
    private String currentVersion;

    public BukkitUpdateSource(OCMMain plugin, File pluginFile) {
        this.updater = new Updater(plugin, 98233, pluginFile, Updater.UpdateType.NO_DOWNLOAD, false);
        this.currentVersion = plugin.getDescription().getVersion();
    }

    @Override
    public List<String> getUpdateMessages() {
        if (updater.getResult() == Updater.UpdateResult.UPDATE_AVAILABLE) {
            //Updater knows local and remote versions are different, but not if it's an update
            String remoteVersion = updater.getLatestName().replaceAll("[A-Za-z\\s]", "");
            if (VersionChecker.shouldUpdate(currentVersion, remoteVersion)) {
                return Arrays.asList(
                        ChatColor.BLUE + "An update for OldCombatMechanics to version " + remoteVersion + " is available!",
                        ChatColor.BLUE + "Click here to download it: " + ChatColor.GRAY + updater.getLatestFileLink()
                );
            }
        }

        return Collections.emptyList();
    }
}
