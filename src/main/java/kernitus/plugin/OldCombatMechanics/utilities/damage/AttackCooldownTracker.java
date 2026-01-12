package kernitus.plugin.OldCombatMechanics.utilities.damage;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.module.OCMModule;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.VersionCompatUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Spigot versions below 1.16 did not have way of getting attack cooldown.
 * Obtaining through NMS works, but value is reset before EntityDamageEvent is called.
 * This means we must keep track of the cooldown to get the correct values.
 */
public class AttackCooldownTracker extends OCMModule {
    private static AttackCooldownTracker INSTANCE;
    private final Map<UUID, Float> lastCooldown;

    public AttackCooldownTracker(OCMMain plugin) {
        super(plugin, "attack-cooldown-tracker");
        lastCooldown = new HashMap<>();

        // This module only matters on versions where HumanEntity#getAttackCooldown does not exist (pre-1.16).
        // OCMMain already gates registration via feature detection, but keep this as a safety net in case a
        // fork/backport adds the method or another plugin initialises this module manually.
        if (Reflector.getMethod(HumanEntity.class, "getAttackCooldown", 0) != null) {
            INSTANCE = null;
            return;
        }

        INSTANCE = this;

        Runnable cooldownTask = () -> Bukkit.getOnlinePlayers().forEach(
                player -> lastCooldown.put(player.getUniqueId(),
                        VersionCompatUtils.getAttackCooldown(player)
                ));
        // Performance: one global per-tick task, not per-player. We must sample every tick because the NMS value
        // is reset before the Bukkit damage event fires, so on-demand reads would be incorrect.
        Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, cooldownTask, 0, 1L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event){
        lastCooldown.remove(event.getPlayer().getUniqueId());
    }

    public static Float getLastCooldown(UUID uuid) {
        final AttackCooldownTracker instance = INSTANCE;
        if (instance == null) return null;
        return instance.lastCooldown.get(uuid);
    }

}
