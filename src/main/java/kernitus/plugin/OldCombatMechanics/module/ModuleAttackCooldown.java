package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.Config;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * Disables the attack cooldown.
 */
public class ModuleAttackCooldown extends Module {

    public ModuleAttackCooldown(OCMMain plugin){
        super(plugin, "disable-attack-cooldown");
    }

    @Override
    public void reload(){
        Bukkit.getOnlinePlayers().forEach(this::adjustAttackSpeed);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLogin(PlayerJoinEvent e){
        adjustAttackSpeed(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWorldChange(PlayerChangedWorldEvent e){
        adjustAttackSpeed(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent e){
        Player player = e.getPlayer();

        // TODO: Why is it reset here? To make uninstalling the plugin easier?
        setAttackSpeed(player, PVPMode.NEW_PVP.getBaseAttackSpeed());
    }

    /**
     * Adjusts the attack speed to the default or configured value, depending on whether the module is enabled.
     *
     * @param player the player to set it for
     */
    private void adjustAttackSpeed(Player player){
        World world = player.getWorld();

        double attackSpeed = Config.moduleEnabled("disable-attack-cooldown", world)
                ? module().getDouble("generic-attack-speed")
                : PVPMode.NEW_PVP.getBaseAttackSpeed();

        setAttackSpeed(player, attackSpeed);
    }

    /**
     * Sets the attack speed to the given value.
     *
     * @param player      the player to set it for
     * @param attackSpeed the attack speed to set it to
     */
    private void setAttackSpeed(Player player, double attackSpeed){
        AttributeInstance attribute = player.getAttribute(Attribute.GENERIC_ATTACK_SPEED);
        if(attribute == null){
            return;
        }

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
