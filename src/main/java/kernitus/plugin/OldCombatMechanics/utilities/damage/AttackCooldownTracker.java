package kernitus.plugin.OldCombatMechanics.utilities.damage;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.module.OCMModule;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.VersionCompatUtils;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

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
        INSTANCE = this;
        lastCooldown = new WeakHashMap<>();

        Runnable cooldownTask = () -> Bukkit.getOnlinePlayers().forEach(
                player -> lastCooldown.put(player.getUniqueId(),
                        VersionCompatUtils.getAttackCooldown(player)
                ));
        Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, cooldownTask, 0, 1L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event){
        lastCooldown.remove(event.getPlayer().getUniqueId());
    }

    public static Float getLastCooldown(UUID uuid) {
        return INSTANCE.lastCooldown.get(uuid);
    }

}
