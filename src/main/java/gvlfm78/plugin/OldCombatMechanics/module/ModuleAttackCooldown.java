package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.Config;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Created by Rayzr522 on 6/25/16.
 */
public class ModuleAttackCooldown extends Module {

    public static ModuleAttackCooldown INSTANCE;

    public ModuleAttackCooldown(OCMMain plugin){
        super(plugin, "disable-attack-cooldown");

        INSTANCE = this;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLogin(PlayerJoinEvent e){
        checkAttackSpeed(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWorldChange(PlayerChangedWorldEvent e){
        checkAttackSpeed(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent e){
        Player player = e.getPlayer();
        AttributeInstance attribute = player.getAttribute(Attribute.GENERIC_ATTACK_SPEED);
        double baseValue = attribute.getBaseValue();
        if(baseValue != 4){ //If basevalue is not 1.9 default, set it back
            attribute.setBaseValue(4);
            player.saveData();
        }
    }

    public void checkAttackSpeed(Player player){
        World world = player.getWorld();

        //If module is disabled, set attack speed to 1.9 default
        double attackSpeed = Config.moduleEnabled("disable-attack-cooldown", world) ? module().getDouble("generic-attack-speed") : 4;

        AttributeInstance attribute = player.getAttribute(Attribute.GENERIC_ATTACK_SPEED);
        double baseValue = attribute.getBaseValue();

        if(baseValue != attackSpeed){
            attribute.setBaseValue(attackSpeed);
            player.saveData();
        }
    }
}
