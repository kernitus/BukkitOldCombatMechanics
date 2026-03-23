/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module;

import com.cryptomorin.xseries.XAttribute;
import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.ConfigUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.Map;

/**
 * Disables the attack cooldown.
 */
public class ModuleAttackCooldown extends OCMModule {

    private static final double VANILLA_ATTACK_SPEED = 4.0;

    private double genericAttackSpeed = 40.0;
    private Map<Material, Double> heldItemAttackSpeeds = Collections.emptyMap();

    public ModuleAttackCooldown(OCMMain plugin) {
        super(plugin, "disable-attack-cooldown");
    }

    @Override
    public void reload() {
        genericAttackSpeed = module().getDouble("generic-attack-speed", 40.0);
        heldItemAttackSpeeds = Collections.emptyMap();

        if (module().isConfigurationSection("held-item-attack-speeds")) {
            heldItemAttackSpeeds = ConfigUtils.loadMaterialDoubleMap(module().getConfigurationSection("held-item-attack-speeds"));
        }

        Bukkit.getOnlinePlayers().forEach(this::adjustAttackSpeed);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerLogin(PlayerJoinEvent e) {
        adjustAttackSpeed(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onWorldChange(PlayerChangedWorldEvent e) {
        adjustAttackSpeed(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHotbarChange(PlayerItemHeldEvent e) {
        if (e.isCancelled()) {
            adjustAttackSpeed(e.getPlayer());
        } else {
            adjustAttackSpeed(e.getPlayer(), e.getPlayer().getInventory().getItem(e.getNewSlot()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSwapHandItems(PlayerSwapHandItemsEvent e) {
        if (e.isCancelled()) {
            adjustAttackSpeed(e.getPlayer());
        } else {
            adjustAttackSpeed(e.getPlayer(), e.getOffHandItem());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerQuit(PlayerQuitEvent e) {
        setAttackSpeed(e.getPlayer(), VANILLA_ATTACK_SPEED);
    }

    /**
     * Adjusts the attack speed to the default or configured value, depending on
     * whether the module is enabled.
     *
     * @param player the player to set the attack speed for
     */
    private void adjustAttackSpeed(Player player) {
        adjustAttackSpeed(player, player.getInventory().getItemInMainHand());
    }

    private void adjustAttackSpeed(Player player, ItemStack mainHand) {
        final double attackSpeed = isEnabled(player)
                ? getConfiguredAttackSpeed(mainHand)
                : VANILLA_ATTACK_SPEED;

        setAttackSpeed(player, attackSpeed);
    }

    @Override
    public void onModesetChange(Player player) {
        adjustAttackSpeed(player);
    }

    private double getConfiguredAttackSpeed(ItemStack itemStack) {
        if (itemStack == null) {
            return genericAttackSpeed;
        }

        return heldItemAttackSpeeds.getOrDefault(itemStack.getType(), genericAttackSpeed);
    }

    /**
     * Sets the attack speed to the given value.
     *
     * @param player      the player to set it for
     * @param attackSpeed the attack speed to set it to
     */
    public void setAttackSpeed(Player player, double attackSpeed) {
        final AttributeInstance attribute = player.getAttribute(XAttribute.ATTACK_SPEED.get());
        if (attribute == null)
            return;

        final double baseValue = attribute.getBaseValue();

        if (baseValue != attackSpeed) {
            debug(String.format("Setting attack speed to %.2f (was: %.2f)", attackSpeed, baseValue), player);

            attribute.setBaseValue(attackSpeed);
        }
    }
}
