/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module;

import com.cryptomorin.xseries.XAttribute;
import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.MathsHelper;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExhaustionEvent;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Deque;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Establishes custom health regeneration rules.
 * Default values based on 1.8 from
 * <a href="https://minecraft.gamepedia.com/Hunger?oldid=948685">wiki</a>
 */
public class ModulePlayerRegen extends OCMModule {

    // Vanilla 1.8 natural regen is driven by ticks (foodTickTimer reaches 80 ticks), not wall-clock time.
    // We therefore measure "interval" in ticks so behaviour stays consistent with TPS drops, rather than
    // speeding up/slowing down based on real time.
    //
    // Performance/correctness:
    // - Use a normal HashMap (WeakHashMap<UUID, ...> can drop entries unpredictably).
    // - Keep a single shared tick counter task that runs only while we are tracking at least one player, rather
    //   than any per-player repeating tasks.
    private final Map<UUID, Long> lastHealTick = new HashMap<>();
    private BukkitTask tickTask;
    private long tickCounter;
    private long intervalTicks;
    private int healAmount;
    private float exhaustionToApply;
    private final Map<UUID, Deque<RegenCharge>> pendingCharges = new HashMap<>();
    private final boolean exhaustionEventAvailable;
    private static final Method FAST_REGEN = Reflector.getMethod(EntityRegainHealthEvent.class, "isFastRegen");

    public ModulePlayerRegen(OCMMain plugin) {
        super(plugin, "old-player-regen");
        exhaustionEventAvailable = hasExhaustionEvent();
        if (exhaustionEventAvailable) {
            // Isolate the optional event type in a nested listener for legacy class loading.
            Bukkit.getPluginManager().registerEvents(new ExhaustionListener(), plugin);
        }
        reload();
    }

    @Override
    public void reload() {
        final long intervalMillis = module().getLong("interval");
        // Config is in milliseconds for user friendliness, but internal logic is tick based.
        intervalTicks = Math.max(1L, Math.round(intervalMillis / 50.0));
        healAmount = module().getInt("amount");
        exhaustionToApply = (float) module().getDouble("exhaustion");

        if (tickTask != null && lastHealTick.isEmpty()) {
            tickTask.cancel();
            tickTask = null;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRegen(EntityRegainHealthEvent e) {
        if (e.getEntityType() != EntityType.PLAYER
                || e.getRegainReason() != EntityRegainHealthEvent.RegainReason.SATIATED)
            return;

        final Player p = (Player) e.getEntity();
        if (!isEnabled(p))
            return;

        final UUID playerId = p.getUniqueId();

        // Replace the heal and its exhaustion charge independently.
        e.setCancelled(true);

        // Get exhaustion & saturation values before healing modifies them
        final float previousExhaustion = p.getExhaustion();
        final float previousSaturation = p.getSaturation();

        ensureTickTaskRunning();

        // Check that it has been at least x ticks since last heal
        final long currentTick = tickCounter;
        final Long lastTick = lastHealTick.get(playerId);
        debug("Exh: " + previousExhaustion + " Sat: " + previousSaturation + " Ticks since: " +
                        (lastTick == null ? "?" : (currentTick - lastTick)),
                p);

        if (lastTick != null && currentTick - lastTick < intervalTicks) {
            replaceRegenerationCharge(p, e, 0);
            return;
        }

        final double maxHealth = p.getAttribute(XAttribute.MAX_HEALTH.get()).getValue();
        final double playerHealth = p.getHealth();

        if (playerHealth < maxHealth) {
            p.setHealth(MathsHelper.clamp(playerHealth + healAmount, 0.0, maxHealth));
            lastHealTick.put(playerId, currentTick);
        }

        replaceRegenerationCharge(p, e, exhaustionToApply);
    }

    private void replaceRegenerationCharge(Player player, EntityRegainHealthEvent event, float amount) {
        final UUID uuid = player.getUniqueId();
        final RegenCharge charge = new RegenCharge(amount,
                exhaustionEventAvailable ? 0 : legacyRegenerationCost(player, event));
        pendingCharges.computeIfAbsent(uuid, ignored -> new ArrayDeque<>()).addLast(charge);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            final Deque<RegenCharge> pending = pendingCharges.get(uuid);
            if (pending != null) {
                pending.remove(charge);
                if (pending.isEmpty()) pendingCharges.remove(uuid);
            }
            if (charge.handled || !player.isOnline()) return;
            // Legacy servers add the native cost directly after the heal event.
            // Adjust the current total by that cost, preserving intervening changes.
            // On event-capable servers this also covers synthetic heals with no native charge.
            player.setExhaustion(Math.max(0, player.getExhaustion() - charge.nativeCost + charge.amount));
        }, 1L);
    }

    private float legacyRegenerationCost(Player player, EntityRegainHealthEvent event) {
        boolean fast = player.getFoodLevel() >= 20 && player.getSaturation() > 0;
        if (FAST_REGEN != null) fast = Reflector.invokeMethod(FAST_REGEN, event);
        final float cost;
        if (fast) {
            cost = Math.min(player.getSaturation(), 6.0f);
        } else {
            final YamlConfiguration spigot = Bukkit.spigot().getConfig();
            final double fallback = spigot.getDouble("world-settings.default.hunger.regen-exhaustion", 6.0);
            cost = (float) spigot.getDouble(
                    "world-settings." + player.getWorld().getName() + ".hunger.regen-exhaustion", fallback);
        }
        // Legacy FoodMetaData caps its addition at 40 exhaustion.
        return Math.max(0, Math.min(cost, 40.0f - player.getExhaustion()));
    }

    private static boolean hasExhaustionEvent() {
        try {
            Class.forName("org.bukkit.event.entity.EntityExhaustionEvent");
            return true;
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }

    private final class ExhaustionListener implements Listener {
        @EventHandler(priority = EventPriority.LOWEST)
        public void onExhaustion(EntityExhaustionEvent event) {
            if (event.getExhaustionReason() != EntityExhaustionEvent.ExhaustionReason.REGEN) return;
            final UUID uuid = event.getEntity().getUniqueId();
            final Deque<RegenCharge> pending = pendingCharges.get(uuid);
            if (pending == null || pending.isEmpty()) return;
            final RegenCharge charge = pending.removeFirst();
            if (pending.isEmpty()) pendingCharges.remove(uuid);
            charge.handled = true;
            // Later listeners can still modify or cancel this cost normally.
            event.setExhaustion(charge.amount);
        }
    }

    private static final class RegenCharge {
        private final float amount;
        private final float nativeCost;
        private boolean handled;

        private RegenCharge(float amount, float nativeCost) {
            this.amount = amount;
            this.nativeCost = nativeCost;
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        lastHealTick.remove(e.getPlayer().getUniqueId());
        pendingCharges.remove(e.getPlayer().getUniqueId());
        stopTickTaskIfIdle();
    }

    private void ensureTickTaskRunning() {
        if (tickTask != null) return;
        tickCounter = 0;
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            tickCounter++;
            if (lastHealTick.isEmpty()) {
                stopTickTaskIfIdle();
            }
        }, 1L, 1L);
    }

    private void stopTickTaskIfIdle() {
        if (tickTask == null) return;
        if (!lastHealTick.isEmpty()) return;
        tickTask.cancel();
        tickTask = null;
    }
}
