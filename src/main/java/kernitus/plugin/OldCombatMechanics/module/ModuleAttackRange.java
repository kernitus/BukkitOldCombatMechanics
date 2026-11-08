/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.Messenger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Arrays;
import java.util.Locale;

/**
 * Applies the 1.8-style attack range (reach + hitbox margin) to melee weapons when Paper exposes
 * the required AttackRange data-component API.
 * Gracefully disables itself on Spigot or other servers where the component API is absent.
 */
public class ModuleAttackRange extends OCMModule implements Listener {

    private static final String[] WEAPONS = {"sword", "axe", "pickaxe", "spade", "shovel", "hoe", "trident", "mace"};

    private boolean supported;
    private float minRange;
    private float maxRange;
    private float minCreative;
    private float maxCreative;
    private float hitboxMargin;
    private float mobFactor;

    private PaperAttackRangeAdapter paperAdapter;

    public ModuleAttackRange(OCMMain plugin) {
        super(plugin, "attack-range");
        initialiseReflection();
        registerCleanerListener(plugin);
        reload();
    }

    private void initialiseReflection() {
        try {
            paperAdapter = new PaperAttackRangeAdapter();
            supported = true;
        } catch (Throwable t) {
            supported = false;
            Messenger.warn("Attack range data-component API not available; module disabled. Required Paper data-component classes or methods were not detected. (" + t.getClass().getSimpleName() + ": " + t.getMessage() + ")");
        }
    }

    /**
     * Reports whether the runtime exposes the Paper AttackRange data-component API this module needs.
     * Kept public so integration tests can use the same capability signal as production code.
     */
    public static boolean isAttackRangeApiAvailable() {
        try {
            new PaperAttackRangeAdapter();
            return true;
        } catch (LinkageError | RuntimeException unavailable) {
            return false;
        }
    }

    @Override
    public void reload() {
        if (!supported) return;

        minRange = (float) module().getDouble("min-range", 0.0);
        maxRange = (float) module().getDouble("max-range", 3.0);
        minCreative = (float) module().getDouble("min-creative-range", 0.0);
        maxCreative = (float) module().getDouble("max-creative-range", 4.0);
        hitboxMargin = (float) module().getDouble("hitbox-margin", 0.1);
        mobFactor = (float) module().getDouble("mob-factor", 1.0);

        // Apply to currently online players so config changes take effect immediately
        Bukkit.getOnlinePlayers().forEach(this::reconcileInventory);
    }

    public void onJoin(PlayerJoinEvent event) {
        reconcileInventory(event.getPlayer());
    }

    @Override
    public void onModesetChange(Player player) {
        reconcileInventory(player);
    }

    private void reconcileInventory(Player player) {
        if (!supported) return;
        final int held = player.getInventory().getHeldItemSlot();
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            if (slot != held) clearComponentOverride(player.getInventory().getItem(slot));
        }
        applyToHeld(player);
    }

    public void onHotbar(PlayerItemHeldEvent event) {
        // Clear the old override, then apply or clear the new one.
        cleanHand(event.getPlayer(), event.getPreviousSlot());
        applyToItem(event.getPlayer(), event.getPlayer().getInventory().getItem(event.getNewSlot()));
    }

    public void onSwap(PlayerSwapHandItemsEvent event) {
        normaliseSwapEvent(event);
        reconcileSwapInventory(event.getPlayer());
    }

    private void normaliseSwapEvent(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!supported) return;

        ItemStack postSwapMainHand = event.getMainHandItem();
        ItemStack postSwapOffHand = event.getOffHandItem();
        clearComponentOverride(postSwapOffHand);
        applyToItem(player, postSwapMainHand);

        // Persist adjusted stacks into event payload for synthetic/manual swap flows.
        event.setMainHandItem(postSwapMainHand);
        event.setOffHandItem(postSwapOffHand);
    }

    private void reconcileSwapInventory(Player player) {
        if (!supported) return;

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;

            ItemStack mainHand = player.getInventory().getItemInMainHand();
            ItemStack offHand = player.getInventory().getItemInOffHand();

            clearComponentOverride(offHand);
            applyToItem(player, mainHand);
        });
    }

    private void applyToHeld(Player player) {
        if (!supported) return;
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) return;
        applyToItem(player, item);
    }

    private void applyToItem(Player player, ItemStack item) {
        if (!supported) return;
        if (item == null || item.getType() == Material.AIR || !isWeapon(item.getType())) {
            clearComponentOverride(item);
            return;
        }
        if (!isEnabled(player)) {
            clearComponentOverride(item);
            return;
        }
        applyAttackRange(item);
    }

    private void cleanHand(Player player, int slot) {
        ItemStack old = player.getInventory().getItem(slot);
        clearComponentOverride(old);
    }

    private boolean isWeapon(Material material) {
        final String name = material.name().toLowerCase(Locale.ROOT);
        return Arrays.stream(WEAPONS).anyMatch(name::endsWith);
    }

    private void applyAttackRange(ItemStack item) {
        paperAdapter.apply(item, minRange, maxRange, minCreative, maxCreative, hitboxMargin, mobFactor);
    }

    private void clearComponentOverride(ItemStack item) {
        if (!supported || paperAdapter == null || item == null) return;
        paperAdapter.clearOverride(item);
    }

    private void registerCleanerListener(Plugin plugin) {
        Bukkit.getPluginManager().registerEvents(new CleanerListener(), plugin);
    }

    /**
     * Always-on listener that clears the module override when an item leaves the hand or is dropped,
     * restoring the item-type prototype where the runtime API supports it.
     */
    private class CleanerListener implements Listener {
        @EventHandler(priority = EventPriority.MONITOR)
        public void onJoin(PlayerJoinEvent event) {
            ModuleAttackRange.this.onJoin(event);
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onHeldChange(PlayerItemHeldEvent event) {
            ModuleAttackRange.this.onHotbar(event);
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onSwap(PlayerSwapHandItemsEvent event) {
            ModuleAttackRange.this.onSwap(event);
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onDrop(PlayerDropItemEvent event) {
            clearComponentOverride(event.getItemDrop().getItemStack());
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onDeath(PlayerDeathEvent event) {
            event.getDrops().forEach(ModuleAttackRange.this::clearComponentOverride);
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onQuit(PlayerQuitEvent event) {
            clearComponentOverride(event.getPlayer().getInventory().getItemInMainHand());
            clearComponentOverride(event.getPlayer().getInventory().getItemInOffHand());
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onWorldChange(PlayerChangedWorldEvent event) {
            reconcileInventory(event.getPlayer());
        }
    }

}
