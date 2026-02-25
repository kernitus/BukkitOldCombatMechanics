/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.Messenger;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector;
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
import java.util.function.Predicate;
import java.lang.reflect.Method;

/**
 * Applies the 1.8-style attack range (reach + hitbox margin) to melee weapons on 1.21.11+ Paper.
 * Gracefully disables itself on Spigot or older versions where the AttackRange data component is absent.
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
        if (!Reflector.versionIsNewerOrEqualTo(1, 21, 11)) {
            supported = false;
            return;
        }
        try {
            paperAdapter = new PaperAttackRangeAdapter();
            supported = true;
        } catch (Throwable t) {
            supported = false;
            Messenger.warn("Attack range component API not available (Paper 1.21.11+ required); module disabled. (" + t.getClass().getSimpleName() + ": " + t.getMessage() + ")");
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
        Bukkit.getOnlinePlayers().forEach(this::applyToHeld);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        applyToHeld(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHotbar(PlayerItemHeldEvent event) {
        // strip old, then apply/strip new
        cleanHand(event.getPlayer(), event.getPreviousSlot());
        applyToHeld(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        normaliseSwapEvent(event);
        reconcileSwapInventory(event.getPlayer());
    }

    private void normaliseSwapEvent(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!supported) return;

        ItemStack postSwapMainHand = event.getOffHandItem();
        ItemStack postSwapOffHand = event.getMainHandItem();
        stripComponent(postSwapOffHand);
        applyToItem(player, postSwapMainHand);

        // Persist adjusted stacks into event payload for synthetic/manual swap flows.
        event.setOffHandItem(postSwapMainHand);
        event.setMainHandItem(postSwapOffHand);
    }

    private void reconcileSwapInventory(Player player) {
        if (!supported) return;

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;

            ItemStack mainHand = player.getInventory().getItemInMainHand();
            ItemStack offHand = player.getInventory().getItemInOffHand();

            stripComponent(mainHand);
            stripComponent(offHand);
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
        if (item == null || item.getType() == Material.AIR || !isWeapon(item.getType())) {
            stripComponent(item);
            return;
        }
        if (!isEnabled(player)) {
            stripComponent(item);
            return;
        }
        applyAttackRange(item);
    }

    private void cleanHand(Player player, int slot) {
        ItemStack old = player.getInventory().getItem(slot);
        stripComponent(old);
    }

    private boolean isWeapon(Material material) {
        final String name = material.name().toLowerCase(Locale.ROOT);
        return Arrays.stream(WEAPONS).anyMatch(name::endsWith);
    }

    private void applyAttackRange(ItemStack item) {
        paperAdapter.apply(item, minRange, maxRange, minCreative, maxCreative, hitboxMargin, mobFactor);
    }

    private void stripComponent(ItemStack item) {
        if (!supported || paperAdapter == null || item == null) return;
        paperAdapter.clear(item);
    }

    private void registerCleanerListener(Plugin plugin) {
        Bukkit.getPluginManager().registerEvents(new CleanerListener(), plugin);
    }

    /**
     * Always-on listener that strips the component when the item leaves hand or is dropped,
     * preventing lingering modified stacks even when the module is disabled.
     */
    private class CleanerListener implements Listener {
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onHeldChange(PlayerItemHeldEvent event) {
            cleanHand(event.getPlayer(), event.getPreviousSlot());
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onSwap(PlayerSwapHandItemsEvent event) {
            // Handled by the module listener; avoid clobbering its swap normalisation.
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onDrop(PlayerDropItemEvent event) {
            stripComponent(event.getItemDrop().getItemStack());
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onDeath(PlayerDeathEvent event) {
            event.getDrops().forEach(ModuleAttackRange.this::stripComponent);
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onQuit(PlayerQuitEvent event) {
            stripComponent(event.getPlayer().getInventory().getItemInMainHand());
            stripComponent(event.getPlayer().getInventory().getItemInOffHand());
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onWorldChange(PlayerChangedWorldEvent event) {
            stripComponent(event.getPlayer().getInventory().getItemInMainHand());
            stripComponent(event.getPlayer().getInventory().getItemInOffHand());
            applyToHeld(event.getPlayer());
        }
    }

    /**
     * Paper-only adapter to avoid reflection in hot paths.
     */
    private static class PaperAttackRangeAdapter {
        @SuppressWarnings("unchecked")
        private static final Predicate<Object> COPY_ALL_COMPONENTS = ignored -> true;

        private final Object attackRangeType;
        private final java.lang.reflect.Method attackRangeFactory;
        private final java.lang.reflect.Method minReachSetter;
        private final java.lang.reflect.Method maxReachSetter;
        private final java.lang.reflect.Method minCreativeSetter;
        private final java.lang.reflect.Method maxCreativeSetter;
        private final java.lang.reflect.Method hitboxSetter;
        private final java.lang.reflect.Method mobFactorSetter;
        private final java.lang.reflect.Method buildMethod;
        private final java.lang.reflect.Method itemSetData;
        private final java.lang.reflect.Method itemHasData;
        private final java.lang.reflect.Method itemUnsetData;
        private final java.lang.reflect.Method itemEnsureServerConversions;
        private final java.lang.reflect.Method itemCopyDataFrom;
        private boolean warned;

        PaperAttackRangeAdapter() throws Exception {
            Class<?> dct = Class.forName("io.papermc.paper.datacomponent.DataComponentTypes");
            Class<?> ar = Class.forName("io.papermc.paper.datacomponent.item.AttackRange");
            Class<?> builder = Class.forName("io.papermc.paper.datacomponent.item.AttackRange$Builder");
            attackRangeType = dct.getField("ATTACK_RANGE").get(null);
            attackRangeFactory = ar.getMethod("attackRange");
            minReachSetter = builder.getMethod("minReach", float.class);
            maxReachSetter = builder.getMethod("maxReach", float.class);
            minCreativeSetter = builder.getMethod("minCreativeReach", float.class);
            maxCreativeSetter = builder.getMethod("maxCreativeReach", float.class);
            hitboxSetter = builder.getMethod("hitboxMargin", float.class);
            mobFactorSetter = builder.getMethod("mobFactor", float.class);
            buildMethod = builder.getMethod("build");
            Class<?> dctClass = Class.forName("io.papermc.paper.datacomponent.DataComponentType");
            itemSetData = findSetDataMethod(dctClass, ar);
            itemHasData = ItemStack.class.getMethod("hasData", dctClass);
            itemUnsetData = ItemStack.class.getMethod("unsetData", dctClass);

            Method ensureMethod = null;
            Method copyMethod = null;
            try {
                ensureMethod = ItemStack.class.getMethod("ensureServerConversions");
                copyMethod = ItemStack.class.getMethod("copyDataFrom", ItemStack.class, Predicate.class);
            } catch (NoSuchMethodException ignored) {
                // Older/newer API shape; keep as best-effort no-op.
            }
            itemEnsureServerConversions = ensureMethod;
            itemCopyDataFrom = copyMethod;
        }

        private Method findSetDataMethod(Class<?> dctClass, Class<?> valueClass) throws NoSuchMethodException {
            for (Method m : ItemStack.class.getMethods()) {
                if (!m.getName().equals("setData")) continue;
                Class<?>[] params = m.getParameterTypes();
                if (params.length != 2) continue;
                // accept any data component type class
                if (!dctClass.isAssignableFrom(params[0]) && !params[0].getName().contains("DataComponentType")) continue;
                if (!params[1].isAssignableFrom(valueClass) && !valueClass.isAssignableFrom(params[1]) && !params[1].isAssignableFrom(Object.class)) continue;
                return m;
            }
            throw new NoSuchMethodException(ItemStack.class.getName() + "#setData(DataComponentType, AttackRange)");
        }

        void apply(ItemStack stack, float min, float max, float minCreative, float maxCreative, float margin, float mobFactor) {
            try {
                Object builder = attackRangeFactory.invoke(null);
                invokeSetter(minReachSetter, builder, min);
                invokeSetter(maxReachSetter, builder, max);
                invokeSetter(minCreativeSetter, builder, minCreative);
                invokeSetter(maxCreativeSetter, builder, maxCreative);
                invokeSetter(hitboxSetter, builder, margin);
                invokeSetter(mobFactorSetter, builder, mobFactor);
                Object arObj = buildMethod.invoke(builder);
                itemSetData.invoke(stack, attackRangeType, arObj);
                ensureServerConversions(stack);
            } catch (Throwable t) {
                if (!warned) {
                    Messenger.warn("Attack range component application failed; leaving item unchanged. (" + t.getClass().getSimpleName() + ": " + t.getMessage() + ")");
                    warned = true;
                }
            }
        }

        private void invokeSetter(Method setter, Object builder, float value) throws Exception {
            Object result = setter.invoke(builder, value);
            if (result != null && !setter.getReturnType().equals(void.class) && !setter.getReturnType().equals(Void.class)) {
                // Some Paper versions return the builder for chaining; others mutate in place.
                // We do not need to capture the returned value because all calls target the same instance.
            }
        }

        boolean hasComponent(ItemStack stack) {
            try {
                return (boolean) itemHasData.invoke(stack, attackRangeType);
            } catch (Throwable t) {
                return false;
            }
        }

        void clear(ItemStack stack) {
            try {
                if (hasComponent(stack)) {
                    itemUnsetData.invoke(stack, attackRangeType);
                    ensureServerConversions(stack);
                }
            } catch (Throwable ignored) {
                // ignore
            }
        }

        private void ensureServerConversions(ItemStack stack) {
            if (stack == null || itemEnsureServerConversions == null || itemCopyDataFrom == null) return;
            try {
                Object converted = itemEnsureServerConversions.invoke(stack);
                if (!(converted instanceof ItemStack)) return;
                if (converted == stack) return;
                itemCopyDataFrom.invoke(stack, converted, COPY_ALL_COMPONENTS);
            } catch (Throwable ignored) {
                // no-op: best-effort sync only
            }
        }
    }
}
