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
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Arrays;
import java.util.Locale;

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
        applyToHeld(event.getPlayer());
    }

    private void applyToHeld(Player player) {
        if (!supported) return;
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) return;
        if (!isWeapon(item.getType())) return;
        applyAttackRange(item);
    }

    private boolean isWeapon(Material material) {
        final String name = material.name().toLowerCase(Locale.ROOT);
        return Arrays.stream(WEAPONS).anyMatch(name::endsWith);
    }

    private void applyAttackRange(ItemStack item) {
        paperAdapter.apply(item, minRange, maxRange, minCreative, maxCreative, hitboxMargin, mobFactor);
    }

    /**
     * Paper-only adapter to avoid reflection in hot paths.
     */
    private static class PaperAttackRangeAdapter {
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
            Class<?> typeClass = dct.getField("ATTACK_RANGE").getType();
            itemSetData = ItemStack.class.getMethod("setData", typeClass, ar);
        }

        void apply(ItemStack stack, float min, float max, float minCreative, float maxCreative, float margin, float mobFactor) {
            try {
                Object builder = attackRangeFactory.invoke(null);
                builder = minReachSetter.invoke(builder, min);
                builder = maxReachSetter.invoke(builder, max);
                builder = minCreativeSetter.invoke(builder, minCreative);
                builder = maxCreativeSetter.invoke(builder, maxCreative);
                builder = hitboxSetter.invoke(builder, margin);
                builder = mobFactorSetter.invoke(builder, mobFactor);
                Object arObj = buildMethod.invoke(builder);
                itemSetData.invoke(stack, attackRangeType, arObj);
            } catch (Throwable t) {
                if (!warned) {
                    Messenger.warn("Attack range component application failed; leaving item unchanged. (" + t.getClass().getSimpleName() + ": " + t.getMessage() + ")");
                    warned = true;
                }
            }
        }
    }
}
