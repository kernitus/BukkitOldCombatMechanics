/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.Messenger;
import org.bukkit.Bukkit;
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
public class ModuleAttackCooldown extends OCMModule {

    public ModuleAttackCooldown(OCMMain plugin) {
        super(plugin, "disable-attack-cooldown");
    }

    @Override
    public void reload() {
        Bukkit.getOnlinePlayers().forEach(this::adjustAttackSpeed);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLogin(PlayerJoinEvent e) {
        adjustAttackSpeed(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWorldChange(PlayerChangedWorldEvent e) {
        adjustAttackSpeed(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent e) {
        setAttackSpeed(e.getPlayer(), PVPMode.NEW_PVP.getBaseAttackSpeed());
    }

    /**
     * Adjusts the attack speed to the default or configured value, depending on whether the module is enabled.
     *
     * @param player the player to set the attack speed for
     */
    private void adjustAttackSpeed(Player player) {
        final double attackSpeed = isEnabled(player)
                ? module().getDouble("generic-attack-speed")
                : PVPMode.NEW_PVP.getBaseAttackSpeed();

        setAttackSpeed(player, attackSpeed);
    }

    @Override
    public void onModesetChange(Player player){
        adjustAttackSpeed(player);
    }

    /**
     * Sets the attack speed to the given value.
     *
     * @param player      the player to set it for
     * @param attackSpeed the attack speed to set it to
     */
    public static void setAttackSpeed(Player player, double attackSpeed) {
        final AttributeInstance attribute = player.getAttribute(Attribute.GENERIC_ATTACK_SPEED);
        if (attribute == null) return;

        final double baseValue = attribute.getBaseValue();

        if (baseValue != attackSpeed) {
            Messenger.debug(String.format("Setting attack speed for player %s to %.2f (was: %.2f)", player.getName(), attackSpeed, baseValue));

            attribute.setBaseValue(attackSpeed);
            player.saveData();
        }
    }

    public static void setAttackSpeed(Player player, PVPMode mode) {
        setAttackSpeed(player, mode.getBaseAttackSpeed());
    }

    /**
     * The different pvp modes for 1.8 or newer.
     */
    public enum PVPMode {
        // 40 is needed for no cooldown whatsoever
        OLD_PVP("1.8", 40),
        NEW_PVP("1.9+", 4);

        private final String name;
        private final double baseAttackSpeed;

        PVPMode(String name, double baseAttackSpeed) {
            this.name = name;
            this.baseAttackSpeed = baseAttackSpeed;
        }

        /**
         * Returns the human-readable name of the mode.
         *
         * @return the human-readable name
         */
        public String getName() {
            return name;
        }

        /**
         * The {@link Attribute#GENERIC_ATTACK_SPEED} base value.
         * <p>
         * The value might be an approximation, if the attribute does not exist in the PVP mode.
         *
         * @return the base value
         */
        public double getBaseAttackSpeed() {
            return baseAttackSpeed;
        }

        /**
         * Returns the PVP mode for the player, defaulting to {@link #OLD_PVP}.
         *
         * @param player the player to get it for
         * @return the PVP mode of the player
         */
        public static PVPMode getModeForPlayer(Player player) {
            Objects.requireNonNull(player, "Player cannot be null!");

            final double baseAttackSpeed = player.getAttribute(Attribute.GENERIC_ATTACK_SPEED).getBaseValue();

            return getByBaseAttackSpeed(baseAttackSpeed).orElse(PVPMode.OLD_PVP);
        }

        private static Optional<PVPMode> getByBaseAttackSpeed(double speed) {
            return Arrays.stream(values())
                    .filter(pvpMode -> pvpMode.getBaseAttackSpeed() == speed)
                    .findFirst();
        }
    }
}
