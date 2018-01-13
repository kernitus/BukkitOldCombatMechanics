package gvlfm78.plugin.OldCombatMechanics.updater;

import gvlfm78.plugin.OldCombatMechanics.OCMMain;
import org.bukkit.ChatColor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SpigotUpdateSource implements UpdateSource {
    private SpigotUpdateChecker updater;

    public SpigotUpdateSource(OCMMain plugin) {
        updater = new SpigotUpdateChecker(plugin, 19510);
    }

    @Override
    public List<String> getUpdateMessages() {
        if (updater.getResult() == SpigotUpdateChecker.UpdateResult.UPDATE_AVAILABLE) {
            return Arrays.asList(
                    ChatColor.BLUE + "An update for OldCombatMechanics to version " + updater.getVersion() + " is available!",
                    ChatColor.BLUE + "Click here to download it: " + ChatColor.GRAY + "https://www.spigotmc.org/resources/oldcombatmechanics.19510/updates"
            );
        }

        return Collections.emptyList();
    }
}
