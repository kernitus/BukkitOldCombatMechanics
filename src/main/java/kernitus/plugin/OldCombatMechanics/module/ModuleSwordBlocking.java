/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockCanBuildEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class ModuleSwordBlocking extends OCMModule {

    private static final ItemStack SHIELD = new ItemStack(Material.SHIELD);
    // Not using WeakHashMaps here, for extra reliability
    private final Map<UUID, ItemStack> storedItems = new HashMap<>();
    private final Map<UUID, Collection<BukkitTask>> correspondingTasks = new HashMap<>();
    private int restoreDelay;

    // Only used <1.13, where BlockCanBuildEvent.getPlayer() is not available
    private Map<Location, UUID> lastInteractedBlocks;

    public ModuleSwordBlocking(OCMMain plugin) {
        super(plugin, "sword-blocking");

        if(!Reflector.versionIsNewerOrEqualTo(1,13,0)){
            lastInteractedBlocks = new WeakHashMap<>();
        }
    }

    @Override
    public void reload() {
        restoreDelay = module().getInt("restoreDelay", 40);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockCanBuildEvent e) {
        if(e.isBuildable()) return;

        Player player;

        // If <1.13 get player who last interacted with block
        if(lastInteractedBlocks != null) {
            final Location blockLocation = e.getBlock().getLocation();
            final UUID uuid = lastInteractedBlocks.remove(blockLocation);
            player = Bukkit.getServer().getPlayer(uuid);
        }
        else player = e.getPlayer();

        if (player == null || !isEnabled(player)) return;

        doShieldBlock(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRightClick(PlayerInteractEvent e) {
        final Action action = e.getAction();
        final Player player = e.getPlayer();

        if (!isEnabled(player)) return;

        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) return;
        // If they clicked on an interactive block, the 2nd event with the offhand won't fire
        // This is also the case if the main hand item was used, e.g. a bow
        // TODO right-clicking on a mob also only fires one hand
        if (action == Action.RIGHT_CLICK_BLOCK && e.getHand() == EquipmentSlot.HAND) return;
        if (e.isBlockInHand()){
            if(lastInteractedBlocks != null)
                lastInteractedBlocks.put(e.getClickedBlock().getLocation(), player.getUniqueId());
            return; // Handle failed block place in separate listener
        }

        doShieldBlock(player);
    }

    private void doShieldBlock(Player player) {
        final PlayerInventory inventory = player.getInventory();

        final ItemStack mainHandItem = inventory.getItemInMainHand();
        final ItemStack offHandItem = inventory.getItemInOffHand();

        if(!isHoldingSword(mainHandItem.getType())) return;

        if (module().getBoolean("use-permission") &&
                !player.hasPermission("oldcombatmechanics.swordblock")) return;

        final UUID id = player.getUniqueId();

        if (!isPlayerBlocking(player)) {
            if (hasShield(inventory)) return;
            debug("Storing " + offHandItem, player);
            storedItems.put(id, offHandItem);

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

        e.getDrops().replaceAll(item ->
                item.getType() == Material.SHIELD ?
                        storedItems.remove(id) : item
        );

        // Handle keepInventory = true
        restore(p);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent e) {
        if (areItemsStored(e.getPlayer().getUniqueId()))
            e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player) {
            final Player player = (Player) e.getWhoClicked();

            if (areItemsStored(player.getUniqueId())) {
                final ItemStack cursor = e.getCursor();
                final ItemStack current = e.getCurrentItem();
                if (cursor != null && cursor.getType() == Material.SHIELD ||
                        current != null && current.getType() == Material.SHIELD) {
                    e.setCancelled(true);
                    restore(player);
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

        // If they are still blocking with the shield, postpone restoring
        if (!areItemsStored(id)) return;

        plugin.getLogger().info("Restoring items for player " + p.getName() + ": Current offhand: " +
                p.getInventory().getItemInOffHand() + ", Stored item: " + storedItems.get(id));

        if (isPlayerBlocking(p)) scheduleRestore(p);
        else p.getInventory().setItemInOffHand(storedItems.remove(id));
    }

    private void tryCancelTask(UUID id) {
        Optional.ofNullable(correspondingTasks.remove(id))
                .ifPresent(tasks -> tasks.forEach(BukkitTask::cancel));
    }

    private void scheduleRestore(Player p) {
        final UUID id = p.getUniqueId();
        tryCancelTask(id);

        final BukkitTask removeItem = Bukkit.getScheduler()
                .runTaskLater(plugin, () -> restore(p), restoreDelay);

        final BukkitTask checkBlocking = Bukkit.getScheduler()
                .runTaskTimer(plugin, () -> {
                if (!isPlayerBlocking(p))
                    restore(p);
            }, 10L, 2L);

        final List<BukkitTask> tasks = new ArrayList<>(2);
        tasks.add(removeItem);
        tasks.add(checkBlocking);
        correspondingTasks.put(p.getUniqueId(), tasks);
    }

    private boolean areItemsStored(UUID uuid) {
        return storedItems.containsKey(uuid);
    }

    /**
     * Checks whether player is blocking or they have just begun to and shield is not fully up yet.
     */
    private boolean isPlayerBlocking(Player player) {
        return player.isBlocking() ||
                (Reflector.versionIsNewerOrEqualTo(1, 11, 0) && player.isHandRaised()
                        && hasShield(player.getInventory())
                );
    }

    private boolean hasShield(PlayerInventory inventory) {
        return inventory.getItemInOffHand().getType() == Material.SHIELD;
    }

    private boolean isHoldingSword(Material mat) {
        return mat.toString().endsWith("_SWORD");
    }
}
