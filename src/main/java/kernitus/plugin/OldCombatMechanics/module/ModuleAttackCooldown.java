/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Disables the attack cooldown.
 */
public class ModuleAttackCooldown extends OCMModule {

    private final double NEW_ATTACK_SPEED = 4;

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
        setAttackSpeed(e.getPlayer(), NEW_ATTACK_SPEED);
    }

    /**
     * Adjusts the attack speed to the default or configured value, depending on whether the module is enabled.
     *
     * @param player the player to set the attack speed for
     */
    private void adjustAttackSpeed(Player player) {
        final double attackSpeed = isEnabled(player)
                ? module().getDouble("generic-attack-speed")
                : NEW_ATTACK_SPEED;

        setAttackSpeed(player, attackSpeed);
    }

    @Override
    public void onModesetChange(Player player) {
        adjustAttackSpeed(player);
    }

    /**
     * Sets the attack speed to the given value.
     *
     * @param player      the player to set it for
     * @param attackSpeed the attack speed to set it to
     */
    public void setAttackSpeed(Player player, double attackSpeed) {
        final AttributeInstance attribute = player.getAttribute(Attribute.GENERIC_ATTACK_SPEED);
        if (attribute == null) return;

        final double baseValue = attribute.getBaseValue();

        final String modesetName = PlayerStorage.getPlayerData(player.getUniqueId()).getModesetForWorld(player.getWorld().getUID());
        debug(String.format("Setting attack speed to %.2f (was: %.2f) for %s in mode %s", attackSpeed, baseValue, player.getName(), modesetName));

        if (baseValue != attackSpeed) {
            debug(String.format("Setting attack speed to %.2f (was: %.2f)", attackSpeed, baseValue), player);

            attribute.setBaseValue(attackSpeed);
            player.saveData();
        }
    }
}
