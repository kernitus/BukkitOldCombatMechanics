/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.Config;
import kernitus.plugin.OldCombatMechanics.utilities.ConfigUtils;
import kernitus.plugin.OldCombatMechanics.utilities.RunnableSeries;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class ModuleSwordBlocking extends Module {

    private static final ItemStack SHIELD = new ItemStack(Material.SHIELD);
    // Not using WeakHashMaps here for reliability
    private final Map<UUID, ItemStack> storedOffhandItems = new HashMap<>();
    private final Map<UUID, RunnableSeries> correspondingTasks = new HashMap<>();
    private int restoreDelay;
    private boolean blacklist;
    private List<Material> noBlockingItems = new ArrayList<>();

    public ModuleSwordBlocking(OCMMain plugin) {
        super(plugin, "sword-blocking");
    }

    @Override
    public void reload() {
        restoreDelay = module().getInt("restoreDelay", 40);
        blacklist = module().getBoolean("blacklist");
        noBlockingItems = ConfigUtils.loadMaterialList(module(), "noBlockingItems");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRightClick(PlayerInteractEvent e) {
        if (e.getItem() == null) return;

        final Action action = e.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        final Block block = e.getClickedBlock();
        if (action == Action.RIGHT_CLICK_BLOCK && block != null &&
                Config.getInteractiveBlocks().contains(block.getType()))
            return;

        final Player p = e.getPlayer();
        final World world = p.getWorld();

        if (!isEnabled(world)) return;

        if (module().getBoolean("use-permission") &&
                !p.hasPermission("oldcombatmechanics.swordblock")) return;

        final UUID id = p.getUniqueId();

        if (!p.isBlocking()) {
            final ItemStack item = e.getItem();

            if (!isHoldingSword(item.getType()) || hasShield(p)) return;

            final PlayerInventory inv = p.getInventory();

            final boolean isANoBlockingItem = noBlockingItems.contains(inv.getItemInOffHand().getType());

            if (blacklist && isANoBlockingItem || !blacklist && !isANoBlockingItem) return;

            storedOffhandItems.put(id, inv.getItemInOffHand());

            inv.setItemInOffHand(SHIELD);
        }

        scheduleRestore(p);
    }

    @EventHandler
    public void onHotBarChange(PlayerItemHeldEvent e) {
        restore(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWorldChange(PlayerChangedWorldEvent e) {
        restore(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLogout(PlayerQuitEvent e) {
        restore(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent e) {
        if (!isBlocking(e.getEntity().getUniqueId())) return;

        final Player p = e.getEntity();
        final UUID id = p.getUniqueId();

        e.getDrops().replaceAll(item -> {
            if (item.getType().equals(Material.SHIELD))
                item = storedOffhandItems.remove(id);

            return item;
        });

        // Handle keepInventory = true
        restore(p);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent e) {
        final Player p = e.getPlayer();
        if (isBlocking(p.getUniqueId()))
            e.setCancelled(true);

    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player) {
            final Player p = (Player) e.getWhoClicked();

            if (isBlocking(p.getUniqueId())) {
                final ItemStack cursor = e.getCursor();
                final ItemStack current = e.getCurrentItem();
                if (cursor != null && cursor.getType() == Material.SHIELD ||
                        current != null && current.getType() == Material.SHIELD) {
                    e.setCancelled(true);
                    restore(p);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemDrop(PlayerDropItemEvent e) {
        final Item is = e.getItemDrop();
        final Player p = e.getPlayer();

        if (isBlocking(p.getUniqueId()) && is.getItemStack().getType() == Material.SHIELD) {
            e.setCancelled(true);
            restore(p);
        }
    }

    private void restore(Player p) {
        final UUID id = p.getUniqueId();

        tryCancelTask(id);

        if (!isBlocking(id)) return;

        if (p.isBlocking()) //They are still blocking with the shield so postpone restoring
            scheduleRestore(p);
        else {
            p.getInventory().setItemInOffHand(storedOffhandItems.get(id));
            storedOffhandItems.remove(id);
        }
    }

    private void tryCancelTask(UUID id) {
        Optional.ofNullable(correspondingTasks.remove(id))
                .ifPresent(RunnableSeries::cancelAll);
    }

    private void scheduleRestore(Player p) {
        final UUID id = p.getUniqueId();
        tryCancelTask(id);

        BukkitRunnable removeItem = new BukkitRunnable() {
            @Override
            public void run() {
                restore(p);
            }
        };
        removeItem.runTaskLater(plugin, restoreDelay);

        BukkitRunnable checkBlocking = new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isBlocking()) {
                    restore(p);
                }
            }
        };
        checkBlocking.runTaskTimer(plugin, 10L, 2L);

        correspondingTasks.put(p.getUniqueId(), new RunnableSeries(removeItem, checkBlocking));
    }

    private boolean isBlocking(UUID uuid) {
        return storedOffhandItems.containsKey(uuid);
    }

    private boolean hasShield(Player p) {
        return p.getInventory().getItemInOffHand().getType() == Material.SHIELD;
    }

    private boolean isHoldingSword(Material mat) {
        return mat.toString().endsWith("_SWORD");
    }
}
