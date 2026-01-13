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
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
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
    private java.lang.reflect.Method paperIsBlockingSword;
    private Method startUsingItemMethod;
    private boolean startUsingItemMethodResolved;
    private Method craftPlayerGetHandleMethod;
    private final Map<Class<?>, Method> nmsStartUsingItemCache = new HashMap<>();
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
        try {
            // Paper-only optimisation: use Paper's item data components to give swords a BLOCK use animation
            // (and the BLOCKING component where available). We keep this behind reflection so the plugin can
            // still compile against Spigot, and we cache the reflective handles once during initialisation to
            // keep the hot path allocation-free.
            final Class<?> adapterClass = Class.forName("kernitus.plugin.OldCombatMechanics.paper.PaperSwordBlocking");
            paperAdapter = adapterClass.getConstructor().newInstance();
            paperApply = adapterClass.getMethod("applyComponents", ItemStack.class);
            paperClear = adapterClass.getMethod("clearComponents", ItemStack.class);
            paperIsBlockingSword = adapterClass.getMethod("isBlockingSword", Player.class);
            paperSupported = true;
            if (isPaperDataComponentApiPresent()) {
                plugin.getLogger().info("Paper sword blocking components enabled (no offhand shield swap).");
            }
        } catch (Throwable t) {
            paperSupported = false;
            paperAdapter = null;
            paperApply = null;
            paperClear = null;
            paperIsBlockingSword = null;
            // Feature-gated warning: only warn when the Paper data component API is present, otherwise this is a
            // normal Spigot/non-Paper environment where the Paper path is not expected to work.
            if (isPaperDataComponentApiPresent()) {
                final Throwable root = (t instanceof java.lang.reflect.InvocationTargetException && ((java.lang.reflect.InvocationTargetException) t).getTargetException() != null)
                        ? ((java.lang.reflect.InvocationTargetException) t).getTargetException()
                        : t;
                plugin.getLogger().warning("Paper sword blocking components unavailable; falling back to legacy offhand shield swap. (" +
                        root.getClass().getSimpleName() + (root.getMessage() == null ? "" : (": " + root.getMessage())) + ")");
            }
        }
    }

    private boolean isPaperDataComponentApiPresent() {
        try {
            Class.forName("io.papermc.paper.datacomponent.DataComponentTypes");
            return true;
        } catch (Throwable ignored) {
            return false;
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
            // Set first, then re-read and patch the inventory-backed stack (CraftItemStack) so NMS components
            // are applied to the real server-side item.
            inventory.setItemInMainHand(mainHandItem);
            final ItemStack invMain = inventory.getItemInMainHand();
            applyConsumableComponent(player, invMain);
            inventory.setItemInMainHand(invMain);
            startUsingMainHandIfSupported(player);
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
            // Best-effort: ask the server to start using the offhand item so blocking becomes visible immediately
            // (and works for fake players / synthetic events). If the API is not present, we fall back silently.
            startUsingItemIfSupported(player, EquipmentSlot.OFF_HAND);
            startUsingItemNmsIfSupported(player, true);
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
        restore(p, force, false);
    }

    private void restore(Player p, boolean force, boolean fromLegacyTask) {
        final UUID id = p.getUniqueId();

        if (!areItemsStored(id)) return;

        // Paper path does not store/restore offhand shields.
        if (paperSupported && paperAdapter != null) return;

        // If they are still blocking with the shield, postpone restoring
        if (!force && isPlayerBlocking(p)) {
            if (!fromLegacyTask) {
                scheduleLegacyRestore(p);
            } else {
                // When running inside the legacy tick task, do not touch the map structure while iterating.
                // Just extend the restore deadline.
                final LegacySwordBlockState state = legacyStates.get(id);
                if (state != null) {
                    state.restoreAtTick = tickCounter + Math.max(0, restoreDelay);
                }
            }
            return;
        }

        p.getInventory().setItemInOffHand(storedItems.remove(id));
        if (!fromLegacyTask) {
            legacyStates.remove(id);
            stopLegacyTaskIfIdle();
        }
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
        if (!isPaperSwordBlocking(player)) return 0;

        final int amount = plugin.getConfig().getInt("shield-damage-reduction.generalDamageReductionAmount", 1);
        final int percent = plugin.getConfig().getInt("shield-damage-reduction.generalDamageReductionPercentage", 50);
        double reduction = (incomingDamage - amount) * (percent / 100.0);
        if (reduction < 0) reduction = 0;
        if (reduction > incomingDamage) reduction = incomingDamage;
        return reduction;
    }

    public boolean isPaperSwordBlocking(Player player) {
        if (!paperSupported || paperAdapter == null) return false;
        if (player == null) return false;
        try {
            if (paperIsBlockingSword != null) {
                final Object result = paperIsBlockingSword.invoke(paperAdapter, player);
                if (result instanceof Boolean) {
                    return (Boolean) result;
                }
            }
        } catch (Throwable ignored) {
        }
        // Fallback: may work on some combinations, but is not reliable for consumable-based sword blocking.
        return player.isBlocking() || (Reflector.versionIsNewerOrEqualTo(1, 11, 0) && player.isHandRaised());
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

    private void startUsingMainHandIfSupported(Player player) {
        startUsingItemIfSupported(player, EquipmentSlot.HAND);
    }

    private void startUsingItemIfSupported(Player player, EquipmentSlot slot) {
        // Feature-gated: Paper exposes LivingEntity#startUsingItem(EquipmentSlot). Spigot does not.
        // Without this, some server/client combinations do not transition into the "hand raised" state, even if the
        // item has a BLOCK use animation (or a shield is injected on legacy path).
        if (player == null || slot == null) return;

        if (!startUsingItemMethodResolved) {
            startUsingItemMethodResolved = true;
            try {
                final Class<?> livingEntityClass = Class.forName("org.bukkit.entity.LivingEntity");
                startUsingItemMethod = livingEntityClass.getMethod("startUsingItem", EquipmentSlot.class);
            } catch (Throwable ignored) {
                startUsingItemMethod = null;
            }
        }

        final java.lang.reflect.Method m = startUsingItemMethod;
        if (m == null) return;
        try {
            m.invoke(player, slot);
        } catch (Throwable ignored) {
        }
    }

    private void startUsingItemNmsIfSupported(Player player, boolean offhand) {
        // Ultra-legacy fallback for environments without LivingEntity#startUsingItem(EquipmentSlot).
        // We reflect into NMS to call LivingEntity#startUsingItem(InteractionHand/EnumHand).
        if (player == null) return;
        try {
            final Class<?> craftPlayerClass = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer");
            if (!craftPlayerClass.isInstance(player)) return;

            Method getHandle = craftPlayerGetHandleMethod;
            if (getHandle == null) {
                getHandle = Reflector.getMethod(craftPlayerClass, "getHandle");
                if (getHandle == null) return;
                craftPlayerGetHandleMethod = getHandle;
            }
            final Object handle = getHandle.invoke(player);
            if (handle == null) return;

            final Class<?> handleClass = handle.getClass();
            Method startUsing = nmsStartUsingItemCache.get(handleClass);
            if (startUsing == null) {
                startUsing = resolveNmsStartUsingItem(handleClass);
                nmsStartUsingItemCache.put(handleClass, startUsing);
            }
            if (startUsing == null) return;

            final Class<?> handType = startUsing.getParameterTypes()[0];
            if (!handType.isEnum()) return;
            final Object hand = enumConstantByName(handType, offhand ? "OFF_HAND" : "MAIN_HAND");
            if (hand == null) return;

            startUsing.invoke(handle, hand);
        } catch (Throwable ignored) {
        }
    }

    private java.lang.reflect.Method resolveNmsStartUsingItem(Class<?> handleClass) {
        // Prefer Mojang-named method where available.
        final Method direct = Reflector.getMethod(handleClass, "startUsingItem", 1);
        if (direct != null && direct.getReturnType() == void.class && direct.getParameterTypes()[0].isEnum()) {
            final Class<?> hand = direct.getParameterTypes()[0];
            if (enumHasConstant(hand, "MAIN_HAND") && enumHasConstant(hand, "OFF_HAND")) {
                return direct;
            }
        }

        // Heuristic fallback: any void method taking an enum hand with MAIN_HAND/OFF_HAND constants.
        Method best = null;
        int bestScore = -1;
        Class<?> current = handleClass;
        while (current != null && current != Object.class) {
            for (Method m : current.getDeclaredMethods()) {
                if (m.getParameterCount() != 1) continue;
                if (m.getReturnType() != void.class) continue;
                final Class<?> param = m.getParameterTypes()[0];
                if (!param.isEnum()) continue;
                if (!enumHasConstant(param, "MAIN_HAND") || !enumHasConstant(param, "OFF_HAND")) continue;

                int score = 0;
                if (m.getName().equals("startUsingItem")) score += 100;
                if (m.getName().equals("c")) score += 50;
                if (m.getName().equals("a")) score += 40;
                final String owner = m.getDeclaringClass().getSimpleName();
                if (owner.contains("Living")) score += 20;
                if (owner.contains("Entity")) score += 10;
                if (score > bestScore) {
                    bestScore = score;
                    best = m;
                }
            }
            current = current.getSuperclass();
        }

        if (best != null) {
            best.setAccessible(true);
        }
        return best;
    }

    private boolean enumHasConstant(Class<?> enumClass, String name) {
        return enumConstantByName(enumClass, name) != null;
    }

    private Object enumConstantByName(Class<?> enumClass, String name) {
        try {
            for (Object constant : enumClass.getEnumConstants()) {
                if (constant instanceof Enum && ((Enum<?>) constant).name().equals(name)) {
                    return constant;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private void stripConsumable(ItemStack item) {
        if (!paperSupported || paperAdapter == null || item == null) return;
        try {
            paperClear.invoke(paperAdapter, item);
        } catch (Throwable ignored) {
        }
    }

    private class ConsumableCleaner implements Listener {
        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
        public void onInventoryClickPre(InventoryClickEvent event) {
            if (!paperSupported || paperAdapter == null) return;
            if (!(event.getWhoClicked() instanceof Player)) return;

            // Inventory moves (shift-click, number-key swaps, etc.) can move a sword out of the main hand slot.
            // We strip the consumable component from items involved in the operation so swords do not keep the
            // component when they are stored elsewhere. We then re-apply it to the actual main-hand item after
            // the click has been processed.
            final ItemStack current = event.getCurrentItem();
            stripConsumable(current);
            if (current != null) event.setCurrentItem(current);

            final ItemStack cursor = event.getCursor();
            stripConsumable(cursor);
            if (cursor != null) event.setCursor(cursor);

            // Number-key hotbar swap: the hotbar item is also involved even if it isn't the clicked slot.
            final int hotbarButton = event.getHotbarButton();
            if (hotbarButton >= 0 && hotbarButton <= 8) {
                final Player p = (Player) event.getWhoClicked();
                final ItemStack hotbar = p.getInventory().getItem(hotbarButton);
                stripConsumable(hotbar);
                p.getInventory().setItem(hotbarButton, hotbar);
            }
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onInventoryClickPost(InventoryClickEvent event) {
            if (!paperSupported || paperAdapter == null) return;
            if (!(event.getWhoClicked() instanceof Player)) return;
            final Player p = (Player) event.getWhoClicked();

            // Ensure the (possibly new) main-hand item has the component after the click resolves.
            Bukkit.getScheduler().runTask(plugin, () -> {
                final PlayerInventory inv = p.getInventory();
                final ItemStack main = inv.getItemInMainHand();
                applyConsumableComponent(p, main);
                inv.setItemInMainHand(main);
            });
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onInventoryDrag(InventoryDragEvent event) {
            if (!paperSupported || paperAdapter == null) return;
            if (!(event.getWhoClicked() instanceof Player)) return;
            final Player p = (Player) event.getWhoClicked();

            // Dragging can place items into multiple slots; strip only the slots affected by this event (no sweep),
            // then re-apply to the actual main-hand item.
            Bukkit.getScheduler().runTask(plugin, () -> {
                final org.bukkit.inventory.InventoryView view = p.getOpenInventory();
                for (Integer rawSlot : event.getRawSlots()) {
                    final ItemStack item = view.getItem(rawSlot);
                    stripConsumable(item);
                    view.setItem(rawSlot, item);
                }

                final PlayerInventory inv = p.getInventory();
                final ItemStack main = inv.getItemInMainHand();
                applyConsumableComponent(p, main);
                inv.setItemInMainHand(main);
            });
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onHeld(PlayerItemHeldEvent event) {
            final PlayerInventory inv = event.getPlayer().getInventory();
            final ItemStack prev = inv.getItem(event.getPreviousSlot());
            stripConsumable(prev);
            inv.setItem(event.getPreviousSlot(), prev);

            final ItemStack next = inv.getItem(event.getNewSlot());
            applyConsumableComponent(event.getPlayer(), next);
            inv.setItem(event.getNewSlot(), next);
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onSwap(PlayerSwapHandItemsEvent event) {
            // Apply/strip against the actual inventory after the swap has taken place.
            Bukkit.getScheduler().runTask(plugin, () -> {
                final PlayerInventory inv = event.getPlayer().getInventory();
                final ItemStack main = inv.getItemInMainHand();
                final ItemStack off = inv.getItemInOffHand();
                stripConsumable(main);
                stripConsumable(off);
                applyConsumableComponent(event.getPlayer(), main);
                inv.setItemInMainHand(main);
                inv.setItemInOffHand(off);
            });
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
            final PlayerInventory inv = event.getPlayer().getInventory();
            final ItemStack main = inv.getItemInMainHand();
            final ItemStack off = inv.getItemInOffHand();
            stripConsumable(main);
            stripConsumable(off);
            inv.setItemInMainHand(main);
            inv.setItemInOffHand(off);
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onWorldChange(PlayerChangedWorldEvent event) {
            final PlayerInventory inv = event.getPlayer().getInventory();
            final ItemStack main = inv.getItemInMainHand();
            final ItemStack off = inv.getItemInOffHand();
            stripConsumable(main);
            stripConsumable(off);
            applyConsumableComponent(event.getPlayer(), main);
            inv.setItemInMainHand(main);
            inv.setItemInOffHand(off);
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

            // Iterate over a snapshot to avoid ConcurrentModificationException if legacyStates is mutated by other
            // events during this tick (quit/world change, additional right-clicks, etc.).
            final List<UUID> uuids = new ArrayList<>(legacyStates.keySet());
            for (UUID uuid : uuids) {
                final LegacySwordBlockState state = legacyStates.get(uuid);
                if (state == null) continue;
                if (!storedItems.containsKey(uuid)) {
                    continue;
                }

                final Player player = Bukkit.getPlayer(uuid);
                if (player == null) {
                    // Cannot restore cleanly; drop state and stored item reference.
                    storedItems.remove(uuid);
                    legacyStates.remove(uuid);
                    continue;
                }

                // Mirror previous behaviour: after 10 ticks, poll every 2 ticks for stop-blocking.
                if (tickCounter >= state.nextBlockingCheckTick) {
                    if (!isPlayerBlocking(player)) {
                        restore(player, false, true);
                        legacyStates.remove(uuid);
                        continue;
                    }
                    state.nextBlockingCheckTick += 2L;
                }

                // Restore-delay timeout: attempt restore; if still blocking, restore() reschedules.
                if (tickCounter >= state.restoreAtTick) {
                    restore(player, false, true);
                    if (!storedItems.containsKey(uuid)) legacyStates.remove(uuid);
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
