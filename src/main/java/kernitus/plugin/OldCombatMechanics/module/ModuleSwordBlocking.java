/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.ConfigUtils;
import kernitus.plugin.OldCombatMechanics.utilities.RunnableSeries;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class ModuleSwordBlocking extends OCMModule {

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
        final Action action = e.getAction();
        if(action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) return;
        // If they clicked on an interactive block, the 2nd event with the offhand won't fire
        // This is also the case if the main hand item was used, e.g. a bow
        if (action == Action.RIGHT_CLICK_BLOCK && e.getHand() == EquipmentSlot.HAND) return;

        final Player player = e.getPlayer();
        final PlayerInventory inventory = player.getInventory();
        // The offhand event won't have the sword as the item unless it's in the offhand
        final ItemStack mainHandItem = inventory.getItemInMainHand();
        final ItemStack offHandItem = inventory.getItemInOffHand();
        final boolean isHoldingSword = isHoldingSword(mainHandItem.getType()) || isHoldingSword(offHandItem.getType());

        final World world = player.getWorld();

        if (!isEnabled(world)) return;

        if (module().getBoolean("use-permission") &&
                !player.hasPermission("oldcombatmechanics.swordblock")) return;

        final UUID id = player.getUniqueId();

        if (!isPlayerBlocking(player)) {
            if (!isHoldingSword || hasShield(player)) return;

            final boolean isNoBlockingItem = noBlockingItems.contains(inventory.getItemInOffHand().getType());

            if (blacklist && isNoBlockingItem || !blacklist && !isNoBlockingItem) return;

            storedOffhandItems.put(id, inventory.getItemInOffHand());

            inventory.setItemInOffHand(SHIELD);
        }
        scheduleRestore(player);
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
        final Player p = e.getEntity();
        final UUID id = p.getUniqueId();
        if (!areItemsStored(id)) return;

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
        if (areItemsStored(p.getUniqueId()))
            e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player) {
            final Player p = (Player) e.getWhoClicked();

            if (areItemsStored(p.getUniqueId())) {
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

        if (areItemsStored(p.getUniqueId()) && is.getItemStack().getType() == Material.SHIELD) {
            e.setCancelled(true);
            restore(p);
        }
    }

    private void restore(Player p) {
        final UUID id = p.getUniqueId();

        tryCancelTask(id);

        // If they are still blocking with the shield postpone restoring
        if (!areItemsStored(id)) return;

        if (isPlayerBlocking(p))
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
                if (!isPlayerBlocking(p))
                    restore(p);
            }
        };
        checkBlocking.runTaskTimer(plugin, 10L, 2L);

        correspondingTasks.put(p.getUniqueId(), new RunnableSeries(removeItem, checkBlocking));
    }

    private boolean areItemsStored(UUID uuid) {
        return storedOffhandItems.containsKey(uuid);
    }

    /**
     * Checks whether player is blocking or they have just begun to and shield is not fully up yet.
     */
    private boolean isPlayerBlocking(Player player) {
        return player.isBlocking() ||
                (Reflector.versionIsNewerOrEqualAs(1,11,0) && player.isHandRaised()
                        && player.getInventory().getItemInOffHand().getType() == Material.SHIELD
                );
    }

    private boolean hasShield(Player p) {
        return p.getInventory().getItemInOffHand().getType() == Material.SHIELD;
    }

    private boolean isHoldingSword(Material mat) {
        return mat.toString().endsWith("_SWORD");
    }
}
