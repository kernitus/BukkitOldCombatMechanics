/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.Messenger;
import kernitus.plugin.OldCombatMechanics.utilities.damage.NewWeaponDamage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A module to disable the sweep attack.
 */
public class ModuleSwordSweep extends OCMModule {

    private final List<Location> sweepLocations = new ArrayList<>();
    private final ParticleListener particleListener;
    private final ProtocolManager protocolManager;
    private EntityDamageEvent.DamageCause sweepDamageCause;
    private BukkitTask task;

    public ModuleSwordSweep(OCMMain plugin) {
        super(plugin, "disable-sword-sweep");

        protocolManager = plugin.getProtocolManager();
        particleListener = new ParticleListener(plugin);

        try {
            // Available from 1.11 onwards
            sweepDamageCause = EntityDamageEvent.DamageCause.valueOf("ENTITY_SWEEP_ATTACK");
        } catch (IllegalArgumentException e) {
            sweepDamageCause = null;
        }

        reload();
    }

    @Override
    public void reload() {
        if (isEnabled())
            protocolManager.addPacketListener(particleListener);
        else
            protocolManager.removePacketListener(particleListener);

        // we didn't set anything up in the first place
        if (sweepDamageCause != null) return;

        if (task != null) task.cancel();

        task = Bukkit.getScheduler().runTaskTimer(plugin, sweepLocations::clear, 0, 1);
    }

    //Changed from HIGHEST to LOWEST to support DamageIndicator plugin
    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityDamaged(EntityDamageByEntityEvent e) {
        final Entity damager = e.getDamager();

        if (!(damager instanceof Player)) return;
        if (!isEnabled(damager, e.getEntity())) return;

        if (sweepDamageCause != null) {
            if (e.getCause() == sweepDamageCause) {
                e.setCancelled(true);
                debug("Sweep cancelled", damager);
            }
            // sweep attack detected or not, we do not need to fall back to the guessing implementation
            return;
        }

        final Player attacker = (Player) e.getDamager();
        final ItemStack weapon = attacker.getInventory().getItemInMainHand();

        if (isHoldingSword(weapon.getType()))
            onSwordAttack(e, attacker, weapon);
    }

    private void onSwordAttack(EntityDamageByEntityEvent e, Player attacker, ItemStack weapon) {
        //Disable sword sweep
        final Location attackerLocation = attacker.getLocation();

        int level = 0;

        try { //In a try catch for servers that haven't updated
            level = weapon.getEnchantmentLevel(Enchantment.SWEEPING_EDGE);
        } catch (NoSuchFieldError ignored) {
        }

        final float damage = NewWeaponDamage.getDamage(weapon.getType()) * level / (level + 1) + 1;

        if (e.getDamage() == damage) {
            // Possibly a sword-sweep attack
            if (sweepLocations.contains(attackerLocation)) {
                debug("Cancelling sweep...", attacker);
                e.setCancelled(true);
            }
        } else {
            sweepLocations.add(attackerLocation);
        }
    }

    private boolean isHoldingSword(Material mat) {
        return mat.toString().endsWith("_SWORD");
    }

    /**
     * Hides sweep particles.
     */
    private class ParticleListener extends PacketAdapter {

        private boolean disabledDueToError;

        public ParticleListener(Plugin plugin) {
            super(plugin, PacketType.Play.Server.WORLD_PARTICLES);
        }

        @Override
        public void onPacketSending(PacketEvent packetEvent) {
            if (disabledDueToError || !isEnabled(packetEvent.getPlayer().getWorld()))
                return;

            try {
                final PacketContainer packetContainer = packetEvent.getPacket();
                String particleName;
                try {
                    particleName = packetContainer.getNewParticles().read(0).getParticle().name();
                } catch (Exception exception){
                    particleName = packetContainer.getParticles().read(0).name(); // for pre 1.13
                }

                if (particleName.toUpperCase(Locale.ROOT).contains("SWEEP")) {
                    packetEvent.setCancelled(true);
                    debug("Cancelled sweep particles", packetEvent.getPlayer());
                }
            } catch (Exception | ExceptionInInitializerError e) {
                disabledDueToError = true;
                Messenger.warn(
                        e,
                        "Error detecting sweep packets. Please report it along with the following exception " +
                                "on github." +
                                "Sweep cancellation should still work, but particles might show up."
                );
            }
        }
    }
}
