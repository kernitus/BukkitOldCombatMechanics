/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.Messenger;
import kernitus.plugin.OldCombatMechanics.utilities.damage.DamageUtils;
import kernitus.plugin.OldCombatMechanics.utilities.damage.NewWeaponDamage;
import kernitus.plugin.OldCombatMechanics.utilities.damage.OCMEntityDamageByEntityEvent;
import kernitus.plugin.OldCombatMechanics.utilities.damage.WeaponDamages;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Restores old tool damage.
 */
public class ModuleOldToolDamage extends OCMModule {

    private static final String[] WEAPONS = {"sword", "axe", "pickaxe", "spade", "shovel", "hoe"};
    private static final Class<?> TRIDENT_CLASS;
    private static final boolean HAS_TRIDENT;
    private boolean oldSharpness;
    private boolean tooltipEnabled;
    private String tooltipPrefix;
    private final TooltipListener tooltipListener;

    static {
        Class<?> tridentClass = null;
        boolean hasTrident = false;
        try {
            tridentClass = Class.forName("org.bukkit.entity.Trident");
            hasTrident = true;
        } catch (ClassNotFoundException ignored) {
            // Legacy servers (e.g. 1.9/1.12) do not have tridents.
        }
        TRIDENT_CLASS = tridentClass;
        HAS_TRIDENT = hasTrident;
    }

    public ModuleOldToolDamage(OCMMain plugin) {
        super(plugin, "old-tool-damage");
        tooltipListener = new TooltipListener();
        Bukkit.getPluginManager().registerEvents(tooltipListener, plugin);
        reload();
    }

    @Override
    public void reload() {
        oldSharpness = module().getBoolean("old-sharpness", true);
        tooltipEnabled = module().getBoolean("tooltip.enabled", false);
        tooltipPrefix = module().getString("tooltip.prefix", "OCM Damage:");
        if (tooltipPrefix == null || tooltipPrefix.trim().isEmpty()) {
            tooltipPrefix = "OCM Damage:";
        }

        // Update online players so config changes take effect immediately
        Bukkit.getOnlinePlayers().forEach(tooltipListener::applyToHeld);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamaged(OCMEntityDamageByEntityEvent event) {
        final Entity damager = event.getDamager();
        if (event.getCause() == EntityDamageEvent.DamageCause.THORNS) return;

        if (!isEnabled(damager, event.getDamagee())) return;

        final ItemStack weapon = event.getWeapon();
        final Material weaponMaterial = weapon.getType();
        final String weaponName = weaponMaterial.name();
        debug("Weapon material: " + weaponMaterial);

        if (!isWeapon(weaponMaterial)) return;

        final double newWeaponBaseDamage = WeaponDamages.getDamage(weaponMaterial);
        if (newWeaponBaseDamage <= 0) {
            debug("Unknown tool type: " + weaponMaterial, damager);
            return;
        }

        final double oldBaseDamage = event.getBaseDamage();
        final Float expectedBaseDamage = NewWeaponDamage.getDamageOrNull(weaponMaterial);
        if (damager instanceof org.bukkit.entity.HumanEntity) {
            boolean isMace = weaponName.equals("MACE");
            double adjustedBase = newWeaponBaseDamage;

            if (expectedBaseDamage != null) {
                final double diff = oldBaseDamage - expectedBaseDamage;
                // For mace we treat diff as the vanilla fall bonus and preserve it.
                if (isMace) {
                    adjustedBase += diff;
                } else {
                    // We check difference as calculation inaccuracies can make it not match
                    if (Math.abs(diff) > 0.0001) {
                        debug("Expected " + expectedBaseDamage + " got " + oldBaseDamage + " ignoring weapon...");
                        return;
                    }
                }
            } else {
                debug("No baseline damage for " + weaponMaterial + ", applying configured damage.", damager);
            }

            event.setBaseDamage(adjustedBase);
            Messenger.debug("Old tool damage: " + oldBaseDamage + " New: " + adjustedBase);
        } else if (damager instanceof org.bukkit.entity.LivingEntity) {
            if (expectedBaseDamage == null) {
                debug("No baseline damage for " + weaponMaterial + ", ignoring mob weapon.", damager);
                return;
            }

            // Mobs do not have a reliable baseline check like players, so we always apply the delta.
            // This means custom mob weapons are not detected and will still be shifted, which may
            // interact poorly with other plugins that modify mob damage in non-vanilla ways.
            final double delta = newWeaponBaseDamage - expectedBaseDamage;
            final double newBaseDamage = oldBaseDamage + delta;
            event.setBaseDamage(newBaseDamage);
            Messenger.debug("Old tool damage (mob): " + oldBaseDamage + " New: " + newBaseDamage);
        }


        // Set sharpness to 1.8 damage value
        final int sharpnessLevel = event.getSharpnessLevel();
        double newSharpnessDamage = oldSharpness ?
                DamageUtils.getOldSharpnessDamage(sharpnessLevel) :
                DamageUtils.getNewSharpnessDamage(sharpnessLevel);

        debug("Old sharpness damage: " + event.getSharpnessDamage() + " New: " + newSharpnessDamage, damager);
        event.setSharpnessDamage(newSharpnessDamage);

        // The mob enchantments damage remains the same and is linear, no need to recalculate it
    }

    private boolean isWeapon(Material material) {
        final String name = material.name();
        if (name.equals("TRIDENT") || name.equals("MACE")) return true;
        return Arrays.stream(WEAPONS).anyMatch(type -> isOfType(material, type));
    }

    private boolean isOfType(Material mat, String type) {
        return mat.toString().endsWith("_" + type.toUpperCase(Locale.ROOT));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTridentProjectile(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (!HAS_TRIDENT || !TRIDENT_CLASS.isInstance(event.getDamager())) return;
        if (!isEnabled(event.getDamager(), event.getEntity())) return;

        final double configured = WeaponDamages.getDamage("TRIDENT_THROWN");
        if (configured <= 0) return;

        event.setDamage(configured);
        debug("Applied custom thrown trident damage: " + configured, event.getDamager());
    }

    private boolean shouldApplyTooltip(Player player) {
        if (!tooltipEnabled) return false;
        return isEnabled(player);
    }

    private String formatDamage(double value) {
        if (value == (long) value) {
            return Long.toString((long) value);
        }
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private List<String> removeExistingTooltip(List<String> lore) {
        final String needle = tooltipPrefix;
        if (needle == null || needle.trim().isEmpty()) {
            return lore;
        }
        return lore.stream()
                .filter(line -> {
                    final String stripped = ChatColor.stripColor(line);
                    return stripped == null || !stripped.startsWith(needle);
                })
                .collect(Collectors.toList());
    }

    private void applyTooltip(Player player, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return;
        if (!isWeapon(item.getType())) {
            stripTooltip(item);
            return;
        }
        if (!shouldApplyTooltip(player)) {
            stripTooltip(item);
            return;
        }

        final double configured = WeaponDamages.getDamage(item.getType());
        if (configured <= 0) {
            stripTooltip(item);
            return;
        }

        final org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        final List<String> base = meta.getLore() == null ? new java.util.ArrayList<>() : removeExistingTooltip(meta.getLore());
        base.add(ChatColor.BLUE + tooltipPrefix + " " + formatDamage(configured));
        meta.setLore(base);
        item.setItemMeta(meta);
    }

    private void stripTooltip(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return;
        final org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta == null || meta.getLore() == null) return;
        final List<String> updated = removeExistingTooltip(meta.getLore());
        if (updated.size() == meta.getLore().size()) return;
        if (updated.isEmpty()) {
            meta.setLore(null);
        } else {
            meta.setLore(updated);
        }
        item.setItemMeta(meta);
    }

    /**
     * Always-on listener that keeps the tooltip line in sync with the held item, and strips it when the module is
     * disabled or the item leaves hand.
     */
    private class TooltipListener implements Listener {
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
            applyToHeld(event.getPlayer());
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onHotbar(org.bukkit.event.player.PlayerItemHeldEvent event) {
            cleanHand(event.getPlayer(), event.getPreviousSlot());
            applyToHeld(event.getPlayer());
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onSwap(org.bukkit.event.player.PlayerSwapHandItemsEvent event) {
            stripTooltip(event.getMainHandItem());
            stripTooltip(event.getOffHandItem());
            applyTooltip(event.getPlayer(), event.getOffHandItem()); // new main hand after swap
            stripTooltip(event.getMainHandItem()); // new offhand should stay clean
        }

        void applyToHeld(Player player) {
            final ItemStack item = player.getInventory().getItemInMainHand();
            applyTooltip(player, item);
        }

        private void cleanHand(Player player, int slot) {
            final ItemStack old = player.getInventory().getItem(slot);
            stripTooltip(old);
        }
    }
}
