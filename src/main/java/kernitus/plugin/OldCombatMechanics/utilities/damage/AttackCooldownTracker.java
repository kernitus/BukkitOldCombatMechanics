package kernitus.plugin.OldCombatMechanics.utilities.damage;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.module.OCMModule;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.VersionCompatUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Spigot versions below 1.16 did not have way of getting attack cooldown.
 * Obtaining through NMS works, but value is reset before EntityDamageEvent is called.
 * This means we must keep track of the cooldown to get the correct values.
 */
public class AttackCooldownTracker extends OCMModule {
    private static AttackCooldownTracker INSTANCE;
    private final Map<UUID, Float> lastCooldown;
    private final Set<UUID> attackedSinceSample = new HashSet<>();
    private final boolean hasSweepDamageCause;

    public AttackCooldownTracker(OCMMain plugin) {
        super(plugin, "attack-cooldown-tracker");
        lastCooldown = new HashMap<>();
        boolean sweepDamageCausePresent;
        try {
            EntityDamageEvent.DamageCause.valueOf("ENTITY_SWEEP_ATTACK");
            sweepDamageCausePresent = true;
        } catch (IllegalArgumentException ignored) {
            sweepDamageCausePresent = false;
        }
        hasSweepDamageCause = sweepDamageCausePresent;

        // This module only matters on versions where HumanEntity#getAttackCooldown does not exist (pre-1.16).
        // OCMMain already gates registration via feature detection, but keep this as a safety net in case a
        // fork/backport adds the method or another plugin initialises this module manually.
        if (Reflector.getMethod(HumanEntity.class, "getAttackCooldown", 0) != null) {
            INSTANCE = null;
            return;
        }

        INSTANCE = this;

        Runnable cooldownTask = () -> {
            attackedSinceSample.clear();
            Bukkit.getOnlinePlayers().forEach(
                player -> lastCooldown.put(player.getUniqueId(),
                        VersionCompatUtils.getAttackCooldown(player)
                ));
        };
        // Performance: one global per-tick task, not per-player. We must sample every tick because the NMS value
        // is reset before the Bukkit damage event fires, so on-demand reads would be incorrect.
        Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, cooldownTask, 0, 1L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event){
        lastCooldown.remove(event.getPlayer().getUniqueId());
        attackedSinceSample.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerAttack(EntityDamageByEntityEvent event) {
        if (INSTANCE != this || !hasSweepDamageCause || event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || !(event.getDamager() instanceof HumanEntity)) return;

        // Cancelled native attacks also consume recharge on legacy servers. Preserve the sample for any
        // secondary sweep hits, and refresh only when another primary attack enters damage recalculation.
        final HumanEntity attacker = (HumanEntity) event.getDamager();
        final Float sampled = lastCooldown.get(attacker.getUniqueId());
        if (sampled != null && VersionCompatUtils.getAttackCooldown(attacker) < sampled) {
            attackedSinceSample.add(attacker.getUniqueId());
        }
    }

    public static void prepareAttack(EntityDamageByEntityEvent event) {
        final AttackCooldownTracker instance = INSTANCE;
        if (instance == null || event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || !(event.getDamager() instanceof HumanEntity)) return;

        final HumanEntity attacker = (HumanEntity) event.getDamager();
        if (instance.attackedSinceSample.remove(attacker.getUniqueId())) {
            instance.lastCooldown.put(attacker.getUniqueId(), VersionCompatUtils.getAttackCooldown(attacker));
        }
    }

    public static Float getLastCooldown(UUID uuid) {
        final AttackCooldownTracker instance = INSTANCE;
        if (instance == null) return null;
        return instance.lastCooldown.get(uuid);
    }

}
