package gvlfm78.plugin.OldCombatMechanics.module;

import gvlfm78.plugin.OldCombatMechanics.OCMMain;
import gvlfm78.plugin.OldCombatMechanics.utilities.Config;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Objects;

/**
 * Created by Rayzr522 on 6/25/16.
 */
public class ModuleAttackCooldown extends Module {

    private static ModuleAttackCooldown INSTANCE;

    public ModuleAttackCooldown(OCMMain plugin){
        super(plugin, "disable-attack-cooldown");

        INSTANCE = this;
    }

    public static void applyAttackSpeed(Player player){
        INSTANCE.checkAttackSpeed(player);
    }

    public static PVPMode getPVPMode(Player player){
        Objects.requireNonNull(player, "player cannot be null!");

        return player.getAttribute(Attribute.GENERIC_ATTACK_SPEED).getBaseValue() == 4 ? PVPMode.NEW_PVP : PVPMode.OLD_PVP;
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

    private void checkAttackSpeed(Player player){
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

    public enum PVPMode {
        OLD_PVP("1.8"), NEW_PVP("1.9+");

        private String name;

        PVPMode(String name){
            this.name = name;
        }

        public String getName(){
            return name;
        }
    }
}
