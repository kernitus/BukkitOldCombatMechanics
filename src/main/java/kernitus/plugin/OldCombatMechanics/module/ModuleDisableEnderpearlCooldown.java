/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.Messenger;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.projectiles.ProjectileSource;

import java.util.*;

/**
 * Allows you to throw enderpearls as often as you like, not only after a cooldown.
 */
public class ModuleDisableEnderpearlCooldown extends OCMModule {

    /**
     * Contains players that threw an ender pearl. As the handler calls launchProjectile,
     * which also calls ProjectileLaunchEvent, we need to ignore that event call.
     */
    private final Set<UUID> ignoredPlayers = new HashSet<>();
    private Map<UUID, Long> lastLaunched;
    private int cooldown;
    private String message;
    private static ModuleDisableEnderpearlCooldown INSTANCE;

    public ModuleDisableEnderpearlCooldown(OCMMain plugin) {
        super(plugin, "disable-enderpearl-cooldown");
        INSTANCE = this;
        reload();
    }

    public void reload() {
        cooldown = module().getInt("cooldown");
        if (cooldown > 0) {
            if (lastLaunched == null) lastLaunched = new WeakHashMap<>();
        } else lastLaunched = null;

        message = module().getBoolean("showMessage") ? module().getString("message") : null;
    }

    public static ModuleDisableEnderpearlCooldown getInstance() {
        return INSTANCE;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerShoot(ProjectileLaunchEvent e) {
        if (e.isCancelled()) return; // For compatibility with other plugins

        final Projectile projectile = e.getEntity();
        if (!(projectile instanceof EnderPearl)) return;
        final ProjectileSource shooter = projectile.getShooter();

        if (!(shooter instanceof Player)) return;
        final Player player = (Player) shooter;

        if (!isEnabled(player)) return;

        final UUID uuid = player.getUniqueId();

        if (ignoredPlayers.contains(uuid)) return;

        e.setCancelled(true);

        // Check if the cooldown has expired yet
        if (lastLaunched != null) {
            final long currentTime = System.currentTimeMillis() / 1000;
            if (lastLaunched.containsKey(uuid)) {
                final long elapsedSeconds = currentTime - lastLaunched.get(uuid);
                if (elapsedSeconds < cooldown) {
                    if (message != null) Messenger.send(player, message, cooldown - elapsedSeconds);
                    return;
                }
            }

            lastLaunched.put(uuid, currentTime);
        }

        // Make sure we ignore the event triggered by launchProjectile
        ignoredPlayers.add(uuid);
        final EnderPearl pearl = player.launchProjectile(EnderPearl.class);
        ignoredPlayers.remove(uuid);

        pearl.setVelocity(player.getEyeLocation().getDirection().multiply(2));

        if (player.getGameMode() == GameMode.CREATIVE) return;

        final ItemStack enderpearlItemStack;
        final PlayerInventory playerInventory = player.getInventory();
        final ItemStack mainHand = playerInventory.getItemInMainHand();
        final ItemStack offHand = playerInventory.getItemInOffHand();

        if (isEnderPearl(mainHand)) enderpearlItemStack = mainHand;
        else if (isEnderPearl(offHand)) enderpearlItemStack = offHand;
        else return;

        enderpearlItemStack.setAmount(enderpearlItemStack.getAmount() - 1);
    }

    private boolean isEnderPearl(ItemStack itemStack) {
        return itemStack != null && itemStack.getType() == Material.ENDER_PEARL;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        if (lastLaunched != null) lastLaunched.remove(e.getPlayer().getUniqueId());
    }

    /**
     * Get the remaining cooldown time for ender pearls for a given player.
     * @param playerUUID The UUID of the player to check the cooldown for.
     * @return The remaining cooldown time in seconds, or 0 if there is no cooldown or it has expired.
     */
    public long getEnderpearlCooldown(UUID playerUUID) {
        if (lastLaunched != null && lastLaunched.containsKey(playerUUID)) {
            final long currentTime = System.currentTimeMillis() / 1000; // Current time in seconds
            final long lastLaunchTime = lastLaunched.get(playerUUID); // Last launch time in seconds
            final long elapsedSeconds = currentTime - lastLaunchTime;
            final long cooldownRemaining = cooldown - elapsedSeconds;
            return Math.max(cooldownRemaining, 0); // Return the remaining cooldown or 0 if it has expired
        }
        return 0;
    }
}
