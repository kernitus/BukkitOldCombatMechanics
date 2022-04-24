package kernitus.plugin.OldCombatMechanics.tester;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.Messenger;
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
    private Tally tally;

    // todo test with armour
    // todo test with enchanted weapons
    // todo test with enchanted armour
    // todo test armour durability
    // todo test with critical hits
    // todo test with potion effects
    // todo test with shield blocking

    public InGameTester(OCMMain ocm) {
        this.ocm = ocm;
        playerInfo = new WeakHashMap<>();
    }

    /**
     * Perform all tests using the two specified players
     */
    public void performTests(Player attacker, Player attackee) {
        beforeAll(attacker, attackee);
        tally = new Tally();

        long delay = 0;

        for (Material weaponType : WeaponDamages.getMaterialDamages().keySet()) {
            Bukkit.getScheduler().runTaskLater(ocm, () -> {
                beforeEach(attacker, attackee);
                testMeleeAttack(attacker, attackee, weaponType, 0);
            }, delay);

            delay += 2;
        }

        delay = testOverdamage(attacker, attackee, delay);

        Bukkit.getScheduler().runTaskLater(ocm, () -> {
            afterAll(attacker, attackee);
        }, delay);

    }

    private long testOverdamage(Player attacker, Player attackee, long delay) {
        Material[] weapons = {Material.WOODEN_HOE, Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_AXE};
        for (Material weaponType : weapons) {
            Bukkit.getScheduler().runTaskLater(ocm, () -> {
                beforeEach(attacker, attackee);
                attackee.setMaximumNoDamageTicks(100);
                testMeleeAttack(attacker, attackee, weaponType, 10);
            }, delay);

            delay += 40;
        }
        return delay;
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

                TesterUtils.assertEquals(finalExpectedDamage, e.getFinalDamage(), tally, "Melee Attack " + weaponType, attacker, attackee);
            }
        };

        Bukkit.getServer().getPluginManager().registerEvents(listener, ocm);

        // Have to run this later because setting the item in the main hand apparently is affected by the cooldown
        Bukkit.getScheduler().runTaskLater(ocm, () -> {
            attacker.attack(attackee);
            afterEach(attacker, attackee);
            EntityDamageByEntityEvent.getHandlerList().unregister(listener);
        }, attackDelay);

    }

    private void beforeAll(Player... players) {
        for (Player player : players) {
            final PlayerInfo info = new PlayerInfo(player.getLocation(), player.getMaximumNoDamageTicks(), player.getInventory().getContents());
            playerInfo.put(player.getUniqueId(), info);
            player.setMaximumNoDamageTicks(0);
        }
    }

    private void afterAll(Player... players) {
        for (Player player : players) {
            final UUID uuid = player.getUniqueId();
            final PlayerInfo info = playerInfo.get(uuid);
            playerInfo.remove(uuid);
            player.getInventory().setContents(info.inventoryContents);
            player.setMaximumNoDamageTicks(info.maximumNoDamageTicks);
            Messenger.send(player, "Passed: &a%d &rFailed: &c%d &rTotal: &7%d", tally.getPassed(), tally.getFailed(), tally.getTotal());
        }
    }

    private void beforeEach(Player... players) {
        for (Player player : players) {
            player.setGameMode(GameMode.SURVIVAL);
            player.getInventory().clear();
            player.setExhaustion(0);
            player.setHealth(20);
        }
    }

    private void afterEach(Player... players) {
        for (Player player : players) {
            final PlayerInfo info = playerInfo.get(player.getUniqueId());
            player.getInventory().clear();
            player.setExhaustion(0);
            player.setHealth(20);
            player.teleport(info.location);
        }
    }
}
