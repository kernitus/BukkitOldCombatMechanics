package kernitus.plugin.OldCombatMechanics.hooks;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.hooks.api.Hook;
import kernitus.plugin.OldCombatMechanics.module.ModuleAttackCooldown.PVPMode;
import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.PlaceholderHook;
import org.bukkit.entity.Player;

public class PlaceholderAPIHook implements Hook {
    @Override
    public void init(OCMMain plugin){
        PlaceholderAPI.registerPlaceholderHook("ocm", new PlaceholderHook() {
            @Override
            public String onPlaceholderRequest(Player player, String identifier){
                if(identifier.equals("pvp_mode")){
                    return PVPMode.getModeForPlayer(player).getName();
                }
                return null;
            }
        });
    }
}
