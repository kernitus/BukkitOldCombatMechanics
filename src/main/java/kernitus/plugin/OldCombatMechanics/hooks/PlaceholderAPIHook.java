package kernitus.plugin.OldCombatMechanics.hooks;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.hooks.api.Hook;
import kernitus.plugin.OldCombatMechanics.module.ModuleAttackCooldown;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;

public class PlaceholderAPIHook implements Hook {
    private PlaceholderExpansion expansion;

    @Override
    public void init(OCMMain plugin) {
        expansion = new PlaceholderExpansion() {
            @Override
            public boolean canRegister() {
                return true;
            }

            @Override
            public @NotNull String getIdentifier() {
                return "ocm";
            }

            @Override
            public @NotNull String getAuthor() {
                return String.join(", ", plugin.getDescription().getAuthors());
            }

            @Override
            public @NotNull String getVersion() {
                return plugin.getDescription().getVersion();
            }

            @Override
            public String onPlaceholderRequest(Player player, @NotNull String identifier) {
                if(player == null) {
                    return null;
                }
                if(identifier.equals("pvp_mode")) {
                    return ModuleAttackCooldown.PVPMode.getModeForPlayer(player).getName();
                }
                return null;

            }
        };

        expansion.register();
    }

    @Override
    public void deinit(OCMMain plugin) {
        if (expansion != null) {
            expansion.unregister();
        }
    }
}
