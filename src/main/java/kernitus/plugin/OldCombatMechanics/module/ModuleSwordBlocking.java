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
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockCanBuildEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.*;

public class ModuleSwordBlocking extends OCMModule {

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
    private java.lang.reflect.Method paperHasConsumable;
    private java.lang.reflect.Method paperIsBlockingSword;
    private Method startUsingItemMethod;
    private boolean startUsingItemMethodResolved;
    private Method craftPlayerGetHandleMethod;
    private final Map<Class<?>, Method> nmsStartUsingItemCache = new HashMap<>();
    private Object minClientVersion;
    private Method packetEventsGetAPI;
    private Method packetEventsGetPlayerManager;
    private Method packetEventsGetUser;
    private Method packetEventsGetClientVersion;
    private Method packetEventsUserGetClientVersion;
    private Method packetEventsIsOlderThan;
    private Object legacyShieldMarkerKey;
    private Object legacyShieldMarkerByteType;
    private Method itemMetaGetPersistentDataContainer;
    private Method persistentDataContainerSet;
    private Method persistentDataContainerHas;
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
        initialisePacketEventsClientVersion();
        initialiseLegacyShieldMarker();
        Bukkit.getPluginManager().registerEvents(new ConsumableLifecycleHandler(), plugin);
    }

    @Override
    public void reload() {
        restoreDelay = module().getInt("restoreDelay", 40);
        if (!paperSupported || paperAdapter == null) return;
        if (isEnabled()) return;

        final Runnable cleanup = () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                onModesetChange(player);
            }
        };

        if (Bukkit.isPrimaryThread()) {
            cleanup.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, cleanup);
        }
    }

    @Override
    public void onModesetChange(Player player) {
        if (player == null) return;

        if (!isEnabled(player)) {
            restore(player, true);
        }

        // Paper component path: when sword-blocking becomes disabled for a player, strip the consumable component
        // from their items so swords do not remain tainted after mode/world changes.
        sweepConsumableState(player, true);
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
            paperHasConsumable = adapterClass.getMethod("hasConsumableComponent", ItemStack.class);
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
            paperHasConsumable = null;
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

    private void initialisePacketEventsClientVersion() {
        try {
            final ClassLoader loader = plugin.getClass().getClassLoader();
            final Class<?> packetEventsClass = Class.forName("kernitus.plugin.OldCombatMechanics.lib.packetevents.api.PacketEvents", true, loader);
            final Class<?> packetEventsApiClass = Class.forName("kernitus.plugin.OldCombatMechanics.lib.packetevents.api.PacketEventsAPI", true, loader);
            final Class<?> playerManagerClass = Class.forName("kernitus.plugin.OldCombatMechanics.lib.packetevents.api.manager.player.PlayerManager", true, loader);
            final Class<?> clientVersionClass = Class.forName("kernitus.plugin.OldCombatMechanics.lib.packetevents.api.protocol.player.ClientVersion", true, loader);
            final Class<?> userClass = Class.forName("kernitus.plugin.OldCombatMechanics.lib.packetevents.api.protocol.player.User", true, loader);
            packetEventsGetAPI = packetEventsClass.getMethod("getAPI");
            packetEventsGetPlayerManager = packetEventsApiClass.getMethod("getPlayerManager");
            packetEventsGetUser = playerManagerClass.getMethod("getUser", Object.class);
            packetEventsGetClientVersion = playerManagerClass.getMethod("getClientVersion", Object.class);
            packetEventsUserGetClientVersion = userClass.getMethod("getClientVersion");
            packetEventsIsOlderThan = clientVersionClass.getMethod("isOlderThan", clientVersionClass);
            minClientVersion = Enum.valueOf((Class<? extends Enum>) clientVersionClass, "V_1_20_5");
        } catch (Throwable ignored) {
            minClientVersion = null;
            packetEventsGetAPI = null;
            packetEventsGetPlayerManager = null;
            packetEventsGetUser = null;
            packetEventsGetClientVersion = null;
            packetEventsUserGetClientVersion = null;
            packetEventsIsOlderThan = null;
        }
    }

    private void initialiseLegacyShieldMarker() {
        try {
            final Class<?> namespacedKeyClass = Class.forName("org.bukkit.NamespacedKey");
            final Class<?> pluginClass = Class.forName("org.bukkit.plugin.Plugin");
            final Class<?> itemMetaClass = Class.forName("org.bukkit.inventory.meta.ItemMeta");
            final Class<?> persistentDataContainerClass = Class.forName("org.bukkit.persistence.PersistentDataContainer");
            final Class<?> persistentDataTypeClass = Class.forName("org.bukkit.persistence.PersistentDataType");

            legacyShieldMarkerKey = namespacedKeyClass
                    .getConstructor(pluginClass, String.class)
                    .newInstance(plugin, "temporary_legacy_shield");
            legacyShieldMarkerByteType = persistentDataTypeClass.getField("BYTE").get(null);
            itemMetaGetPersistentDataContainer = itemMetaClass.getMethod("getPersistentDataContainer");
            persistentDataContainerSet = persistentDataContainerClass.getMethod(
                    "set",
                    namespacedKeyClass,
                    persistentDataTypeClass,
                    Object.class
            );
            persistentDataContainerHas = persistentDataContainerClass.getMethod(
                    "has",
                    namespacedKeyClass,
                    persistentDataTypeClass
            );
        } catch (Throwable ignored) {
            legacyShieldMarkerKey = null;
            legacyShieldMarkerByteType = null;
            itemMetaGetPersistentDataContainer = null;
            persistentDataContainerSet = null;
            persistentDataContainerHas = null;
        }
    }

    private boolean supportsPaperAnimation(Player player) {
        if (!paperSupported || paperAdapter == null) return false;
        if (player == null) return false;
        if (minClientVersion == null) return false;
        try {
            if (packetEventsGetAPI == null || packetEventsGetPlayerManager == null || packetEventsGetClientVersion == null || packetEventsIsOlderThan == null) {
                return false;
            }
            final Object api = packetEventsGetAPI.invoke(null);
            if (api == null) return false;
            final Object playerManager = packetEventsGetPlayerManager.invoke(api);
            if (playerManager == null) return false;
            Object clientVersion = packetEventsGetClientVersion.invoke(playerManager, player);
            if (clientVersion == null && packetEventsGetUser != null && packetEventsUserGetClientVersion != null) {
                final Object user = packetEventsGetUser.invoke(playerManager, player);
                if (user != null) {
                    clientVersion = packetEventsUserGetClientVersion.invoke(user);
                }
            }
            if (clientVersion == null) return true;
            if (isUnknownClientVersion(clientVersion)) {
                // During very early login or synthetic test players, PacketEvents may not have a User yet.
                // Keep animation support in that case to avoid regressing normal modern-client behaviour.
                if (packetEventsGetUser != null) {
                    final Object user = packetEventsGetUser.invoke(playerManager, player);
                    if (user == null) return true;
                }
                return false;
            }
            final Object older = packetEventsIsOlderThan.invoke(clientVersion, minClientVersion);
            return !(older instanceof Boolean && (Boolean) older);
        } catch (Throwable ignored) {
            return false;
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

        if (supportsPaperAnimation(player)) {
            // Modern Paper path: we can provide a sword blocking animation via components, without swapping an
            // offhand shield. This avoids the legacy polling/restore tasks and avoids interfering with offhand
            // gameplay items (totems, food, etc.).
            // Set first, then re-read and patch the inventory-backed stack (CraftItemStack) so NMS components
            // are applied to the real server-side item.
            inventory.setItemInMainHand(mainHandItem);
            final ItemStack invMain = inventory.getItemInMainHand();
            if (applyConsumableComponent(player, invMain)) {
                inventory.setItemInMainHand(invMain);
            }
            startUsingMainHandIfSupported(player);
            return;
        }

        if (stripConsumable(mainHandItem)) {
            inventory.setItemInMainHand(mainHandItem);
        }

        final UUID id = player.getUniqueId();

        if (!isPlayerBlocking(player)) {
            if (hasShield(inventory)) return;
            debug("Storing " + offHandItem, player);
            storedItems.put(id, offHandItem);

            inventory.setItemInOffHand(createTemporaryLegacyShield());
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
    public void onPlayerJoin(PlayerJoinEvent e) {
        restore(e.getPlayer(), true);
        stripConsumableState(e.getPlayer(), true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLogout(PlayerQuitEvent e) {
        restore(e.getPlayer(), true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent e) {
        final Player p = e.getEntity();
        final UUID id = p.getUniqueId();
        final boolean hadMarkedTemporaryOffhand = hasTemporaryLegacyShieldMarker(p.getInventory().getItemInOffHand());
        final ItemStack storedOffhand = storedItems.remove(id);
        final LegacySwordBlockState removedLegacyState = legacyStates.remove(id);
        if (storedOffhand == null && removedLegacyState == null) return;

        stopLegacyTaskIfIdle();

        if (storedOffhand == null) {
            return;
        }

        if (e.getKeepInventory()) {
            p.getInventory().setItemInOffHand(storedOffhand);
            return;
        }

        final List<ItemStack> drops = e.getDrops();
        int temporaryShieldDropIndex = -1;
        for (int i = 0; i < drops.size(); i++) {
            if (isTemporaryLegacyShieldDrop(drops.get(i))) {
                temporaryShieldDropIndex = i;
                break;
            }
        }

        // Synthetic/manual death events may construct shield drops without preserving item metadata.
        // If we know the player had our marked temporary shield in offhand, allow a single shield rewrite.
        if (temporaryShieldDropIndex < 0 && hadMarkedTemporaryOffhand && canMarkTemporaryLegacyShield()) {
            temporaryShieldDropIndex = firstShieldDropIndex(drops);
        }

        if (temporaryShieldDropIndex >= 0) {
            if (storedOffhand.getType() == Material.AIR) {
                drops.remove(temporaryShieldDropIndex);
            } else {
                drops.set(temporaryShieldDropIndex, storedOffhand);
            }
            return;
        }

        if (storedOffhand.getType() != Material.AIR) {
            drops.add(storedOffhand);
        }
    }

    private int firstShieldDropIndex(List<ItemStack> drops) {
        for (int i = 0; i < drops.size(); i++) {
            final ItemStack item = drops.get(i);
            if (item != null && item.getType() == Material.SHIELD) {
                return i;
            }
        }
        return -1;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent e) {
        final UUID uuid = e.getPlayer().getUniqueId();
        if (!areItemsStored(uuid)) return;

        if (supportsPaperAnimation(e.getPlayer())) {
            restore(e.getPlayer(), true);
            return;
        }

        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) {
            return;
        }
        final Player player = (Player) e.getWhoClicked();
        if (!areItemsStored(player.getUniqueId())) {
            return;
        }

        if (isSwapOffhandClick(e) || isTemporaryOffhandShieldClick(e)) {
            e.setCancelled(true);
            restore(player, true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemDrop(PlayerDropItemEvent e) {
        final Item is = e.getItemDrop();
        final Player p = e.getPlayer();

        if (areItemsStored(p.getUniqueId()) && is.getItemStack().getType() == Material.SHIELD) {
            // Do not cancel here: this event can represent unrelated shield drops from inventory/hotbar.
            // We only need to end legacy temporary-shield state immediately.
            restore(p, true);
        }
    }

    private boolean isTemporaryOffhandShieldClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null) return false;
        if (event.getClickedInventory().getType() != InventoryType.PLAYER) return false;
        if (event.getSlot() != 40) return false;

        final ItemStack current = event.getCurrentItem();
        return current != null && current.getType() == Material.SHIELD;
    }

    private boolean isSwapOffhandClick(InventoryClickEvent event) {
        try {
            return event.getClick() == ClickType.SWAP_OFFHAND;
        } catch (NoSuchFieldError ignored) {
            return false;
        }
    }

    private ItemStack createTemporaryLegacyShield() {
        final ItemStack shield = new ItemStack(Material.SHIELD);
        markTemporaryLegacyShield(shield);
        return shield;
    }

    private void markTemporaryLegacyShield(ItemStack item) {
        if (!canMarkTemporaryLegacyShield()) return;
        if (item == null || item.getType() != Material.SHIELD) return;
        try {
            final ItemMeta meta = item.getItemMeta();
            if (meta == null) return;
            final Object persistentDataContainer = itemMetaGetPersistentDataContainer.invoke(meta);
            if (persistentDataContainer == null) return;
            persistentDataContainerSet.invoke(
                    persistentDataContainer,
                    legacyShieldMarkerKey,
                    legacyShieldMarkerByteType,
                    Byte.valueOf((byte) 1)
            );
            item.setItemMeta(meta);
        } catch (Throwable ignored) {
        }
    }

    private boolean canMarkTemporaryLegacyShield() {
        return legacyShieldMarkerKey != null
                && legacyShieldMarkerByteType != null
                && itemMetaGetPersistentDataContainer != null
                && persistentDataContainerSet != null
                && persistentDataContainerHas != null;
    }

    private boolean isTemporaryLegacyShieldDrop(ItemStack item) {
        if (item == null || item.getType() != Material.SHIELD) return false;
        if (!canMarkTemporaryLegacyShield()) {
            // Safety-first fallback: without marker support we cannot reliably distinguish temporary shields from
            // legitimate plain shields, so avoid rewriting any shield drops.
            return false;
        }
        return hasTemporaryLegacyShieldMarker(item);
    }

    private boolean hasTemporaryLegacyShieldMarker(ItemStack item) {
        if (!canMarkTemporaryLegacyShield()) return false;
        try {
            final ItemMeta meta = item.getItemMeta();
            if (meta == null) return false;
            final Object persistentDataContainer = itemMetaGetPersistentDataContainer.invoke(meta);
            if (persistentDataContainer == null) return false;
            final Object marked = persistentDataContainerHas.invoke(
                    persistentDataContainer,
                    legacyShieldMarkerKey,
                    legacyShieldMarkerByteType
            );
            return marked instanceof Boolean && (Boolean) marked;
        } catch (Throwable ignored) {
            return false;
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

        // This method only runs when a legacy offhand item has been stored, so restore unconditionally.
        // Do not gate this by generic Paper support: older clients on Paper still use the legacy shield fallback.

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
        if (!hasShield(player.getInventory())) return false;

        return player.isBlocking() ||
                (Reflector.versionIsNewerOrEqualTo(1, 11, 0) && player.isHandRaised());
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

    private boolean applyConsumableComponent(Player player, ItemStack item) {
        if (!supportsPaperAnimation(player) || paperApply == null) return false;
        if (item == null || item.getType() == Material.AIR || !isHoldingSword(item.getType())) return false;
        if (!isEnabled(player)) return false;
        if (hasConsumableComponent(item)) return false;
        try {
            paperApply.invoke(paperAdapter, item);
            return true;
        } catch (Throwable ignored) {
            return false;
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

    private boolean stripConsumable(ItemStack item) {
        if (!paperSupported || paperAdapter == null || paperClear == null || item == null) return false;
        if (item.getType() == Material.AIR || !isHoldingSword(item.getType())) return false;
        if (!hasConsumableComponent(item)) return false;
        try {
            paperClear.invoke(paperAdapter, item);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean hasConsumableComponent(ItemStack item) {
        if (!paperSupported || paperAdapter == null || paperHasConsumable == null || item == null) return false;
        try {
            final Object result = paperHasConsumable.invoke(paperAdapter, item);
            return result instanceof Boolean && (Boolean) result;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean shouldHandleConsumable(Player player) {
        return paperSupported && paperAdapter != null && player != null && isEnabled(player);
    }

    private void sweepConsumableState(Player player, boolean includeStorage) {
        if (!paperSupported || paperAdapter == null || player == null) return;

        final PlayerInventory inv = player.getInventory();
        final boolean enabled = isEnabled(player);
        final boolean supportsAnimation = supportsPaperAnimation(player);

        // Offhand should never carry the component.
        final ItemStack off = inv.getItemInOffHand();
        if (stripConsumable(off)) {
            inv.setItemInOffHand(off);
        }

        final ItemStack main = inv.getItemInMainHand();
        if (supportsAnimation && enabled) {
            if (applyConsumableComponent(player, main)) {
                inv.setItemInMainHand(main);
            }
            if (!includeStorage) {
                return;
            }

            // Keep only the held slot eligible for the component.
            final int held = inv.getHeldItemSlot();
            final ItemStack[] storage = inv.getStorageContents();
            for (int i = 0; i < storage.length; i++) {
                if (i == held) continue;
                final ItemStack item = storage[i];
                if (stripConsumable(item)) {
                    inv.setItem(i, item);
                }
            }
            return;
        }

        if (stripConsumable(main)) {
            inv.setItemInMainHand(main);
        }

        if (!includeStorage) {
            return;
        }

        final ItemStack[] storage = inv.getStorageContents();
        for (int i = 0; i < storage.length; i++) {
            final ItemStack item = storage[i];
            if (stripConsumable(item)) {
                inv.setItem(i, item);
            }
        }
    }

    private void stripConsumableState(Player player, boolean includeStorage) {
        if (!paperSupported || paperAdapter == null || player == null) return;

        final PlayerInventory inv = player.getInventory();

        final ItemStack main = inv.getItemInMainHand();
        if (stripConsumable(main)) {
            inv.setItemInMainHand(main);
        }

        final ItemStack off = inv.getItemInOffHand();
        if (stripConsumable(off)) {
            inv.setItemInOffHand(off);
        }

        if (!includeStorage) {
            return;
        }

        final ItemStack[] storage = inv.getStorageContents();
        for (int i = 0; i < storage.length; i++) {
            final ItemStack item = storage[i];
            if (stripConsumable(item)) {
                inv.setItem(i, item);
            }
        }
    }

    private boolean isUnknownClientVersion(Object clientVersion) {
        if (!(clientVersion instanceof Enum)) return false;
        final String name = ((Enum<?>) clientVersion).name();
        return "UNKNOWN".equals(name) || "HIGHER_THAN_SUPPORTED_VERSIONS".equals(name);
    }

    private class ConsumableLifecycleHandler implements Listener {
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onHeld(PlayerItemHeldEvent event) {
            if (!shouldHandleConsumable(event.getPlayer()) || !supportsPaperAnimation(event.getPlayer())) return;
            final PlayerInventory inv = event.getPlayer().getInventory();
            final ItemStack prev = inv.getItem(event.getPreviousSlot());
            if (stripConsumable(prev)) {
                inv.setItem(event.getPreviousSlot(), prev);
            }

            final ItemStack next = inv.getItem(event.getNewSlot());
            if (applyConsumableComponent(event.getPlayer(), next)) {
                inv.setItem(event.getNewSlot(), next);
            }
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onSwap(PlayerSwapHandItemsEvent event) {
            final Player player = event.getPlayer();
            if (!shouldHandleConsumable(player) || !supportsPaperAnimation(player)) return;
            final int heldSlotAtEvent = player.getInventory().getHeldItemSlot();
            final org.bukkit.inventory.InventoryView viewAtEvent = player.getOpenInventory();
            final org.bukkit.inventory.Inventory eventTop = viewAtEvent == null ? null : viewAtEvent.getTopInventory();
            final org.bukkit.inventory.Inventory eventBottom = viewAtEvent == null ? null : viewAtEvent.getBottomInventory();
            // Apply/strip against the actual inventory after the swap has taken place.
            Bukkit.getScheduler().runTask(plugin, () -> {
                final PlayerInventory inv = player.getInventory();
                final ItemStack main = inv.getItemInMainHand();
                final ItemStack off = inv.getItemInOffHand();
                final boolean mainStripped = stripConsumable(main);
                final boolean offStripped = stripConsumable(off);
                final org.bukkit.inventory.InventoryView currentView = player.getOpenInventory();
                final boolean viewMatches = currentView != null
                        && currentView.getTopInventory() == eventTop
                        && currentView.getBottomInventory() == eventBottom;
                final boolean mainApplied = shouldHandleConsumable(player)
                        && supportsPaperAnimation(player)
                        && inv.getHeldItemSlot() == heldSlotAtEvent
                        && viewMatches
                        && applyConsumableComponent(player, main);
                if (mainStripped || mainApplied) {
                    inv.setItemInMainHand(main);
                }
                if (offStripped) {
                    inv.setItemInOffHand(off);
                }
            });
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onDrop(PlayerDropItemEvent event) {
            if (!shouldHandleConsumable(event.getPlayer()) || !supportsPaperAnimation(event.getPlayer())) return;
            stripConsumable(event.getItemDrop().getItemStack());
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onDeath(PlayerDeathEvent event) {
            if (!shouldHandleConsumable(event.getEntity()) || !supportsPaperAnimation(event.getEntity())) return;
            event.getDrops().forEach(ModuleSwordBlocking.this::stripConsumable);
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onQuit(PlayerQuitEvent event) {
            stripConsumableState(event.getPlayer(), true);
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onWorldChange(PlayerChangedWorldEvent event) {
            stripConsumableState(event.getPlayer(), true);
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
