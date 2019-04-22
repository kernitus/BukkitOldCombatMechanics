package gvlfm78.plugin.OldCombatMechanics.hooks;

import gvlfm78.plugin.OldCombatMechanics.OCMMain;
import gvlfm78.plugin.OldCombatMechanics.hooks.api.Hook;
import gvlfm78.plugin.OldCombatMechanics.module.ModuleAttackCooldown.PVPMode;
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
