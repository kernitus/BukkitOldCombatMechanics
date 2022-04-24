/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class ModuleAttackFrequency extends Module {

    private static final int DEFAULT_DELAY = 20;
    private static int playerDelay, mobDelay;

    public ModuleAttackFrequency(OCMMain plugin) {
        super(plugin, "attack-frequency");
        module().getInt("delay");
    }

    @Override
    public void reload() {
        playerDelay = module().getInt("playerDelay");
        mobDelay = module().getInt("mobDelay");

        Bukkit.getWorlds().forEach(world -> world.getLivingEntities().forEach(livingEntity -> {
            final int toApply = livingEntity instanceof HumanEntity ? playerDelay : mobDelay;
            livingEntity.setMaximumNoDamageTicks(isEnabled(world) ? toApply : DEFAULT_DELAY);
        }));
    }

    @EventHandler
    public void onPlayerLogin(PlayerJoinEvent e) {
        final Player player = e.getPlayer();
        final World world = player.getWorld();
        if (isEnabled(world)) setDelay(player, playerDelay);
    }

    @EventHandler
    public void onPlayerLogout(PlayerQuitEvent e) {
        setDelay(e.getPlayer(), DEFAULT_DELAY);
    }

    @EventHandler
    public void onPlayerChangeWorld(PlayerChangedWorldEvent e) {
        final Player player = e.getPlayer();
        final World world = player.getWorld();
        setDelay(player, isEnabled(world) ? playerDelay : DEFAULT_DELAY);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent e) {
        final Player player = e.getPlayer();
        final World world = player.getWorld();
        setDelay(player, isEnabled(world) ? playerDelay : DEFAULT_DELAY);
    }

    private void setDelay(Player player, int delay) {
        player.setMaximumNoDamageTicks(delay);
        debug("Set hit delay to " + delay, player);
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent e) {
        final LivingEntity livingEntity = e.getEntity();
        final World world = livingEntity.getWorld();
        if (isEnabled(world)) livingEntity.setMaximumNoDamageTicks(mobDelay);
    }

    @EventHandler
    public void onEntityTeleportEvent(EntityTeleportEvent e) {
        final Entity entity = e.getEntity();
        if (!(entity instanceof LivingEntity)) return;
        final LivingEntity livingEntity = (LivingEntity) entity;

        final World fromWorld = e.getFrom().getWorld();
        final World toWorld = e.getTo().getWorld();
        if (fromWorld.getUID() != toWorld.getUID()) livingEntity.setMaximumNoDamageTicks(isEnabled(toWorld) ? mobDelay : DEFAULT_DELAY);
    }
}
