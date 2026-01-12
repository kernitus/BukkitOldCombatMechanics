/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector;
import kernitus.plugin.OldCombatMechanics.paper.PaperSwordBlocking;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
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
    private final Map<UUID, LegacySwordBlockState> legacyStates = new HashMap<>();
    private BukkitTask legacyTask;
    private long tickCounter;
    private int restoreDelay;
    private boolean paperSupported;
    private Object paperAdapter;
    private java.lang.reflect.Method paperApply;
    private java.lang.reflect.Method paperClear;
    private static ModuleSwordBlocking INSTANCE;

    // Only used <1.13, where BlockCanBuildEvent.getPlayer() is not available
    private Map<Location, UUID> lastInteractedBlocks;

    public ModuleSwordBlocking(OCMMain plugin) {
        super(plugin, "sword-blocking");
        INSTANCE = this;

        if (!Reflector.versionIsNewerOrEqualTo(1, 13, 0)) {
            lastInteractedBlocks = new WeakHashMap<>();
        }

        initialisePaperAdapter();
        Bukkit.getPluginManager().registerEvents(new ConsumableCleaner(), plugin);
    }

    @Override
    public void reload() {
        restoreDelay = module().getInt("restoreDelay", 40);
    }

    private void initialisePaperAdapter() {
        if (!Reflector.versionIsNewerOrEqualTo(1, 21, 2)) {
            paperSupported = false;
            paperAdapter = null;
            return;
        }
        try {
            // Paper-only optimisation: use Paper's item data components to give swords a BLOCK use animation
            // (and the BLOCKING component where available). We keep this behind reflection so the plugin can
            // still compile against Spigot, and we cache the reflective handles once during initialisation to
            // keep the hot path allocation-free.
            final Class<?> adapterClass = Class.forName("kernitus.plugin.OldCombatMechanics.paper.PaperSwordBlocking");
            paperAdapter = adapterClass.getConstructor().newInstance();
            paperApply = adapterClass.getMethod("applyComponents", ItemStack.class);
            paperClear = adapterClass.getMethod("clearComponents", ItemStack.class);
            paperSupported = true;
        } catch (Throwable t) {
            paperSupported = false;
            paperAdapter = null;
            paperApply = null;
            paperClear = null;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockCanBuildEvent e) {
        if (e.isBuildable()) return;

        Player player;

        // If <1.13 get player who last interacted with block
        if (lastInteractedBlocks != null) {
            final Location blockLocation = e.getBlock().getLocation();
            final UUID uuid = lastInteractedBlocks.remove(blockLocation);
            player = Bukkit.getServer().getPlayer(uuid);
        } else player = e.getPlayer();

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
        if (e.isBlockInHand()) {
            if (lastInteractedBlocks != null) {
                final Block clickedBlock = e.getClickedBlock();
                if (clickedBlock != null)
                    lastInteractedBlocks.put(clickedBlock.getLocation(), player.getUniqueId());
            }
            return; // Handle failed block place in separate listener
        }

        doShieldBlock(player);
    }

    private void doShieldBlock(Player player) {
        final PlayerInventory inventory = player.getInventory();

        final ItemStack mainHandItem = inventory.getItemInMainHand();
        final ItemStack offHandItem = inventory.getItemInOffHand();

        if (!isHoldingSword(mainHandItem.getType())) return;

        if (module().getBoolean("use-permission") &&
                !player.hasPermission("oldcombatmechanics.swordblock")) return;

        if (paperSupported && paperAdapter != null) {
            // Modern Paper path: we can provide a sword blocking animation via components, without swapping an
            // offhand shield. This avoids the legacy polling/restore tasks and avoids interfering with offhand
            // gameplay items (totems, food, etc.).
            applyConsumableComponent(player, mainHandItem);
            return;
        }

        final UUID id = player.getUniqueId();

        if (!isPlayerBlocking(player)) {
            if (hasShield(inventory)) return;
            debug("Storing " + offHandItem, player);
            storedItems.put(id, offHandItem);

            inventory.setItemInOffHand(SHIELD);
            // Force an inventory update to avoid ghost items
            player.updateInventory();
        }
        // Legacy path: per-player state is required because we temporarily equip a shield. We restore the
        // original offhand item once the player stops blocking or after a configurable delay.
        scheduleLegacyRestore(player);

        applyConsumableComponent(player, mainHandItem);
    }

    @EventHandler
    public void onHotBarChange(PlayerItemHeldEvent e) {
        restore(e.getPlayer(), true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWorldChange(PlayerChangedWorldEvent e) {
        restore(e.getPlayer(), true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLogout(PlayerQuitEvent e) {
        restore(e.getPlayer(), true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent e) {
        final Player p = e.getEntity();
        final UUID id = p.getUniqueId();
        if (!areItemsStored(id)) return;

        //TODO what if they legitimately had a shield?
        e.getDrops().replaceAll(item ->
                item.getType() == Material.SHIELD ?
                        storedItems.remove(id) : item
        );

        // Handle keepInventory = true
        restore(p, true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent e) {
        if (areItemsStored(e.getPlayer().getUniqueId()))
            e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) {
            return;
        }
        final Player player = (Player) e.getWhoClicked();
        if (areItemsStored(player.getUniqueId())) {
            final ItemStack cursor = e.getCursor();
            final ItemStack current = e.getCurrentItem();
            if (cursor != null && cursor.getType() == Material.SHIELD ||
                    current != null && current.getType() == Material.SHIELD) {
                e.setCancelled(true);
                restore(player, true);
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

    private void restore(Player player) {
        restore(player, false);
    }

    private void restore(Player p, boolean force) {
        final UUID id = p.getUniqueId();

        if (!areItemsStored(id)) return;

        // Paper path does not store/restore offhand shields.
        if (paperSupported && paperAdapter != null) return;

        // If they are still blocking with the shield, postpone restoring
        if (!force && isPlayerBlocking(p)) {
            scheduleLegacyRestore(p);
            return;
        }

        p.getInventory().setItemInOffHand(storedItems.remove(id));
        legacyStates.remove(id);
        stopLegacyTaskIfIdle();
    }

    private void scheduleLegacyRestore(Player p) {
        final UUID id = p.getUniqueId();
        final LegacySwordBlockState state = legacyStates.computeIfAbsent(id, ignored -> new LegacySwordBlockState());
        state.restoreAtTick = tickCounter + Math.max(0, restoreDelay);
        state.nextBlockingCheckTick = tickCounter + 10L;
        ensureLegacyTaskRunning();
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

    public static ModuleSwordBlocking getInstance() {
        return INSTANCE;
    }

    /**
     * Paper path: compute 1.8-style blocking reduction for sword blocking without offhand shield.
     *
     * @param event          underlying damage event
     * @param incomingDamage current damage value before blocking is applied (attack side only)
     * @return reduction amount to subtract from damage, or 0 if not blocking/unsupported.
     */
    public double applyPaperBlockingReduction(org.bukkit.event.entity.EntityDamageByEntityEvent event, double incomingDamage) {
        if (!paperSupported || paperAdapter == null) return 0;
        if (!(event.getEntity() instanceof Player)) return 0;
        final Player player = (Player) event.getEntity();
        if (!isEnabled(event.getDamager(), player)) return 0;
        if (!isHoldingSword(player.getInventory().getItemInMainHand().getType())) return 0;
        if (!player.isBlocking() && !(Reflector.versionIsNewerOrEqualTo(1, 11, 0) && player.isHandRaised())) return 0;

        final int amount = plugin.getConfig().getInt("shield-damage-reduction.generalDamageReductionAmount", 1);
        final int percent = plugin.getConfig().getInt("shield-damage-reduction.generalDamageReductionPercentage", 50);
        double reduction = (incomingDamage - amount) * (percent / 100.0);
        if (reduction < 0) reduction = 0;
        if (reduction > incomingDamage) reduction = incomingDamage;
        return reduction;
    }

    /* ---------- Paper consumable component (animation-only) ---------- */

    private void applyConsumableComponent(Player player, ItemStack item) {
        if (!paperSupported || paperAdapter == null) return;
        if (item == null || item.getType() == Material.AIR || !isHoldingSword(item.getType())) return;
        if (!isEnabled(player)) return;
        try {
            paperApply.invoke(paperAdapter, item);
        } catch (Throwable ignored) {
        }
    }

    private void stripConsumable(ItemStack item) {
        if (!paperSupported || paperAdapter == null || item == null) return;
        try {
            paperClear.invoke(paperAdapter, item);
        } catch (Throwable ignored) {
        }
    }

    private class ConsumableCleaner implements Listener {
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onHeld(PlayerItemHeldEvent event) {
            stripConsumable(event.getPlayer().getInventory().getItem(event.getPreviousSlot()));
            applyConsumableComponent(event.getPlayer(), event.getPlayer().getInventory().getItem(event.getNewSlot()));
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onSwap(PlayerSwapHandItemsEvent event) {
            stripConsumable(event.getMainHandItem());
            stripConsumable(event.getOffHandItem());
            applyConsumableComponent(event.getPlayer(), event.getOffHandItem());
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onDrop(PlayerDropItemEvent event) {
            stripConsumable(event.getItemDrop().getItemStack());
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onDeath(PlayerDeathEvent event) {
            event.getDrops().forEach(ModuleSwordBlocking.this::stripConsumable);
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onQuit(PlayerQuitEvent event) {
            stripConsumable(event.getPlayer().getInventory().getItemInMainHand());
            stripConsumable(event.getPlayer().getInventory().getItemInOffHand());
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onWorldChange(PlayerChangedWorldEvent event) {
            stripConsumable(event.getPlayer().getInventory().getItemInMainHand());
            stripConsumable(event.getPlayer().getInventory().getItemInOffHand());
            applyConsumableComponent(event.getPlayer(), event.getPlayer().getInventory().getItemInMainHand());
        }
    }

    private void ensureLegacyTaskRunning() {
        if (legacyTask != null) return;
        tickCounter = 0;
        // Performance: previously, sword blocking could schedule per-player repeating tasks. When many players
        // block at once this scales poorly (scheduler overhead + allocations). Instead, keep per-player state in
        // a map and run one shared tick task while there is any active legacy blocking state.
        legacyTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            tickCounter++;
            if (legacyStates.isEmpty()) {
                stopLegacyTaskIfIdle();
                return;
            }

            final Iterator<Map.Entry<UUID, LegacySwordBlockState>> it = legacyStates.entrySet().iterator();
            while (it.hasNext()) {
                final Map.Entry<UUID, LegacySwordBlockState> entry = it.next();
                final UUID uuid = entry.getKey();
                if (!storedItems.containsKey(uuid)) {
                    it.remove();
                    continue;
                }

                final Player player = Bukkit.getPlayer(uuid);
                if (player == null) {
                    // Cannot restore cleanly; drop state and stored item reference.
                    storedItems.remove(uuid);
                    it.remove();
                    continue;
                }

                final LegacySwordBlockState state = entry.getValue();

                // Mirror previous behaviour: after 10 ticks, poll every 2 ticks for stop-blocking.
                if (tickCounter >= state.nextBlockingCheckTick) {
                    if (!isPlayerBlocking(player)) {
                        restore(player, false);
                        it.remove();
                        continue;
                    }
                    state.nextBlockingCheckTick += 2L;
                }

                // Restore-delay timeout: attempt restore; if still blocking, restore() reschedules.
                if (tickCounter >= state.restoreAtTick) {
                    restore(player, false);
                    if (!storedItems.containsKey(uuid)) {
                        it.remove();
                    }
                }
            }

            stopLegacyTaskIfIdle();
        }, 1L, 1L);
    }

    private void stopLegacyTaskIfIdle() {
        if (legacyTask == null) return;
        if (!legacyStates.isEmpty()) return;
        legacyTask.cancel();
        legacyTask = null;
    }

    private static final class LegacySwordBlockState {
        private long restoreAtTick;
        private long nextBlockingCheckTick;
    }
}
