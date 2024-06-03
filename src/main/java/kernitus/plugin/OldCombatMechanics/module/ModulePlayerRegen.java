/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module;

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

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Establishes custom health regeneration rules.
 * Default values based on 1.8 from <a href="https://minecraft.gamepedia.com/Hunger?oldid=948685">wiki</a>
 */
public class ModulePlayerRegen extends OCMModule {

    private final Map<UUID, Long> healTimes = new WeakHashMap<>();

    public ModulePlayerRegen(OCMMain plugin) {
        super(plugin, "old-player-regen");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRegen(EntityRegainHealthEvent e) {
        if (e.getEntityType() != EntityType.PLAYER
                || e.getRegainReason() != EntityRegainHealthEvent.RegainReason.SATIATED)
            return;

        final Player p = (Player) e.getEntity();
        if (!isEnabled(p)) return;

        final UUID playerId = p.getUniqueId();

        // We cancel the regen, but saturation and exhaustion need to be adjusted separately
        // Exhaustion is modified in the next tick, and saturation in the tick following that (if exhaustion > 4)
        e.setCancelled(true);

        // Get exhaustion & saturation values before healing modifies them
        final float previousExhaustion = p.getExhaustion();
        final float previousSaturation = p.getSaturation();

        // Check that it has been at least x seconds since last heal
        final long currentTime = System.currentTimeMillis();
        final boolean hasLastHealTime = healTimes.containsKey(playerId);
        final long lastHealTime = healTimes.computeIfAbsent(playerId, id -> currentTime);

        debug("Exh: " + previousExhaustion + " Sat: " + previousSaturation + " Time: " + (currentTime - lastHealTime), p);

        // If we're skipping this heal, we must fix the exhaustion in the following tick
        if (hasLastHealTime && currentTime - lastHealTime <= module().getLong("interval")) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> p.setExhaustion(previousExhaustion), 1L);
            return;
        }

        final double maxHealth = p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        final double playerHealth = p.getHealth();

        if (playerHealth < maxHealth) {
            p.setHealth(MathsHelper.clamp(playerHealth + module().getInt("amount"), 0.0, maxHealth));
            healTimes.put(playerId, currentTime);
        }

        // Calculate new exhaustion value, must be between 0 and 4. If above, it will reduce the saturation in the following tick.
        final float exhaustionToApply = (float) module().getDouble("exhaustion");

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // We do this in the next tick because bukkit doesn't stop the exhaustion change when cancelling the event
            p.setExhaustion(previousExhaustion + exhaustionToApply);
            debug("Exh before: " + previousExhaustion + " Now: " + p.getExhaustion() +
                    " Sat now: " + previousSaturation, p);
        }, 1L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        healTimes.remove(e.getPlayer().getUniqueId());
    }
}
