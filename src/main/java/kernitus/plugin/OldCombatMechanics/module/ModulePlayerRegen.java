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
import org.bukkit.attribute.Attribute;
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

    public ModulePlayerRegen(OCMMain plugin) {
        super(plugin, "old-player-regen");
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

        // We cancel the regen, but saturation and exhaustion need to be adjusted
        // separately
        // Exhaustion is modified in the next tick, and saturation in the tick following
        // that (if exhaustion > 4)
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

        // If we're skipping this heal, we must fix the exhaustion in the following tick
        if (lastTick != null && currentTick - lastTick < intervalTicks) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> p.setExhaustion(previousExhaustion), 1L);
            return;
        }

        final double maxHealth = p.getAttribute(XAttribute.MAX_HEALTH.get()).getValue();
        final double playerHealth = p.getHealth();

        if (playerHealth < maxHealth) {
            p.setHealth(MathsHelper.clamp(playerHealth + healAmount, 0.0, maxHealth));
            lastHealTick.put(playerId, currentTick);
        }

        // Calculate new exhaustion value, must be between 0 and 4. If above, it will
        // reduce the saturation in the following tick.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // We do this in the next tick because bukkit doesn't stop the exhaustion change
            // when cancelling the event
            p.setExhaustion(previousExhaustion + exhaustionToApply);
            debug("Exh before: " + previousExhaustion + " Now: " + p.getExhaustion() +
                    " Sat now: " + previousSaturation, p);
        }, 1L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        lastHealTick.remove(e.getPlayer().getUniqueId());
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
