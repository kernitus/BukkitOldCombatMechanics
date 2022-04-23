package kernitus.plugin.OldCombatMechanics.tester;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.damage.WeaponDamages;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import static kernitus.plugin.OldCombatMechanics.tester.TesterUtils.PlayerInfo;

public class InGameTester {

    private final OCMMain ocm;
    private final Map<UUID, PlayerInfo> playerInfo;
    //todo also save inventory before the start of tests

    public InGameTester(OCMMain ocm) {
        this.ocm = ocm;
        playerInfo = new WeakHashMap<>();
    }

    public void performTests(Player attacker, Player attackee) {

        long delay = 0;

        for (Material weaponType : WeaponDamages.getMaterialDamages().keySet()) {
            Bukkit.getScheduler().runTaskLater(ocm, () -> {
                preparePlayer(attacker, attackee);
                testMeleeAttack(attacker, attackee, weaponType,0);
            }, delay);

            delay += 2;
        }

        testOverdamage(attacker, attackee, delay);
    }

    private void testOverdamage(Player attacker, Player attackee, long delay) {
        Material[] weapons = {Material.WOODEN_HOE, Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_AXE};
        for (Material weaponType : weapons) {
            Bukkit.getScheduler().runTaskLater(ocm, () -> {
                preparePlayer(attacker, attackee);
                attackee.setMaximumNoDamageTicks(100);
                testMeleeAttack(attacker, attackee, weaponType,10);
            }, delay);

            delay += 40;
        }

    }

    private void testMeleeAttack(Player attacker, Player attackee, Material weaponType, long attackDelay) {
        ItemStack weapon = new ItemStack(weaponType);

        double expectedDamage = WeaponDamages.getDamage(weaponType);
        if ((float) attackee.getNoDamageTicks() > (float) attackee.getMaximumNoDamageTicks() / 2.0F)
            expectedDamage -= attackee.getLastDamage();

        attacker.getInventory().setItemInMainHand(weapon);
        attacker.updateInventory();

        double finalExpectedDamage = expectedDamage;

        Listener listener = new Listener() {
            @EventHandler(priority = EventPriority.MONITOR)
            public void onEvent(EntityDamageByEntityEvent e) {
                if (e.getDamager() != attacker || e.getEntity() != attackee) return;

                TesterUtils.assertEquals(finalExpectedDamage, e.getFinalDamage(), "Melee Attack " + weaponType, attacker, attackee);
            }
        };

        Bukkit.getServer().getPluginManager().registerEvents(listener, ocm);

        // Have to run this later because setting the item in the main hand apparently is affected by the cooldown
        Bukkit.getScheduler().runTaskLater(ocm, () -> {
            attacker.attack(attackee);
            cleanupPlayer(attacker, attackee);
            EntityDamageByEntityEvent.getHandlerList().unregister(listener);
        }, attackDelay);

    }

    private void preparePlayer(Player... players) {
        for (Player player : players) {
            final PlayerInfo info = new PlayerInfo(player.getLocation(), player.getMaximumNoDamageTicks());
            playerInfo.put(player.getUniqueId(), info);
            player.setGameMode(GameMode.SURVIVAL);
            player.getInventory().clear();
            player.setExhaustion(0);
            player.setHealth(20);
            //player.setMaximumNoDamageTicks(0);
        }
    }

    private void cleanupPlayer(Player... players) {
        for (Player player : players) {
            final PlayerInfo info = playerInfo.get(player.getUniqueId());
            playerInfo.remove(player.getUniqueId());

            player.getInventory().clear();
            player.setExhaustion(0);
            player.setHealth(20);
            player.teleport(info.location);
            //player.setMaximumNoDamageTicks(info.maximumNoDamageTicks);
        }
    }
}
