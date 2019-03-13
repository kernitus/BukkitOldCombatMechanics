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

    @Override
    public void reload(){
        Bukkit.getOnlinePlayers().forEach(this::adjustAttackSpeed);
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

    /**
     * The different pvp modes for 1.8 or newer.
     */
    public enum PVPMode {
        // 16 is enough to disable it
        OLD_PVP("1.8", 16),
        NEW_PVP("1.9+", 4);

        private String name;
        private double baseAttackSpeed;

        PVPMode(String name, double baseAttackSpeed){
            this.name = name;
            this.baseAttackSpeed = baseAttackSpeed;
        }

        /**
         * Returns the human readable name of the mode.
         *
         * @return the human readable name
         */
        public String getName(){
            return name;
        }

        /**
         * The {@link Attribute#GENERIC_ATTACK_SPEED} base value.
         * <p>
         * The value might be an approximation, if the attribute does not exist in the PVP mode.
         *
         * @return the base value
         */
        public double getBaseAttackSpeed(){
            return baseAttackSpeed;
        }

        /**
         * Returns the PVP mode for the player, defaulting to {@link #OLD_PVP}.
         *
         * @param player the player to get it for
         * @return the PVP mode of the player
         */
        public static PVPMode getModeForPlayer(Player player){
            Objects.requireNonNull(player, "player cannot be null!");

            double baseAttackSpeed = player.getAttribute(Attribute.GENERIC_ATTACK_SPEED).getBaseValue();

            return getByBaseAttackSpeed(baseAttackSpeed)
                    .orElse(PVPMode.OLD_PVP);
        }

        private static Optional<PVPMode> getByBaseAttackSpeed(double speed){
            return Arrays.stream(values())
                    .filter(pvpMode -> pvpMode.getBaseAttackSpeed() == speed)
                    .findFirst();
        }
    }
}
