/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import com.cryptomorin.xseries.XEnchantment;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.stream.Collectors;

public class ModuleOldArmourDurability extends OCMModule {

    // Armour durability events can fire right after an explosion damage event. We suppress those for one tick so
    // old-armour-durability doesn't double-apply/over-apply changes during explosion bursts.
    //
    // Performance/correctness:
    // - Use a normal HashMap (WeakHashMap<UUID, ...> can drop entries unpredictably).
    // - Avoid scheduling one task per explosion: keep a shared cleanup task that runs only while entries exist.
    // - Entries expire after 1 tick, which is long enough for the follow-up PlayerItemDamageEvents to fire but
    //   short enough to not interfere with unrelated armour wear later.
    private final Map<UUID, ExplosionDamagedArmour> explosionDamaged = new HashMap<>();
    private BukkitTask explosionCleanupTask;
    private long explosionTickCounter;

    public ModuleOldArmourDurability(OCMMain plugin) {
        super(plugin, "old-armour-durability");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onItemDamage(PlayerItemDamageEvent e) {
        final Player player = e.getPlayer();

        if (!isEnabled(player)) return;
        final ItemStack item = e.getItem();
        final Material itemType = item.getType();

        // Check if it's a piece of armour they're currently wearing
        if (Arrays.stream(player.getInventory().getArmorContents())
                .noneMatch(armourPiece -> armourPiece != null &&
                        armourPiece.getType() == itemType &&
                        armourPiece.getType() != Material.ELYTRA // ignore elytra as it doesn't provide any protection anyway
                )) return;

        final UUID uuid = player.getUniqueId();
        if (explosionDamaged.containsKey(uuid)) {
            final ExplosionDamagedArmour data = explosionDamaged.get(uuid);
            if (data == null) return;
            final List<ItemStack> armour = data.armour;
            // ItemStack.equals() checks material, durability and quantity to make sure nothing changed in the meantime
            // We're checking all the pieces this way just in case they're wearing two helmets or something strange
            final List<ItemStack> matchedPieces = armour.stream()
                    .filter(piece -> piece.equals(item))
                    .collect(Collectors.toList());
            armour.removeAll(matchedPieces);
            debug("Item matched explosion, ignoring...", player);
            if (!matchedPieces.isEmpty()) return;
        }

        int reduction = module().getInt("reduction");

        // 60 + (40 / (level + 1) ) % chance that durability is reduced (for each point of durability)
        final int damageChance = 60 + (40 / (item.getEnchantmentLevel(XEnchantment.UNBREAKING.getEnchant()) + 1));
        final Random random = new Random();
        final int randomInt = random.nextInt(100); // between 0 (inclusive) and 100 (exclusive)
        if (randomInt >= damageChance)
            reduction = 0;

        debug("Item damaged: " + itemType + " Damage: " + e.getDamage() + " Changed to: " + reduction, player);
        e.setDamage(reduction);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerExplosionDamage(EntityDamageEvent e) {
        if (e.isCancelled()) return;
        if (e.getEntityType() != EntityType.PLAYER) return;
        final EntityDamageEvent.DamageCause cause = e.getCause();
        if (cause != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION &&
                cause != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) return;

        final Player player = (Player) e.getEntity();
        final UUID uuid = player.getUniqueId();
        final List<ItemStack> armour = Arrays.stream(player.getInventory().getArmorContents()).filter(Objects::nonNull).collect(Collectors.toList());
        explosionDamaged.put(uuid, new ExplosionDamagedArmour(armour, explosionTickCounter + 1L));
        ensureExplosionCleanupTaskRunning();

        debug("Detected explosion!", player);
    }

    private void ensureExplosionCleanupTaskRunning() {
        if (explosionCleanupTask != null) return;
        explosionTickCounter = 0;

        explosionCleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            explosionTickCounter++;
            if (explosionDamaged.isEmpty()) {
                stopExplosionCleanupTaskIfIdle();
                return;
            }

            final Iterator<Map.Entry<UUID, ExplosionDamagedArmour>> it = explosionDamaged.entrySet().iterator();
            while (it.hasNext()) {
                final ExplosionDamagedArmour data = it.next().getValue();
                if (data == null || data.expiresAtTick <= explosionTickCounter) {
                    it.remove();
                }
            }

            stopExplosionCleanupTaskIfIdle();
        }, 1L, 1L);
    }

    private void stopExplosionCleanupTaskIfIdle() {
        if (explosionCleanupTask == null) return;
        if (!explosionDamaged.isEmpty()) return;
        explosionCleanupTask.cancel();
        explosionCleanupTask = null;
    }

    private static final class ExplosionDamagedArmour {
        private final List<ItemStack> armour;
        private final long expiresAtTick;

        private ExplosionDamagedArmour(List<ItemStack> armour, long expiresAtTick) {
            this.armour = armour;
            this.expiresAtTick = expiresAtTick;
        }
    }
}
