/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
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
    private long delay;
    private Player attacker, defender;
    private Runnable extras; // for giving defender armour etc.

    // todo test with armour
    // todo test with enchanted weapons
    // todo test with enchanted armour
    // todo test armour durability
    // todo test with critical hits
    // todo test with potion effects
    // todo test with shield blocking

    public InGameTester(OCMMain ocm) {
        this.ocm = ocm;
        delay = 0;
        playerInfo = new WeakHashMap<>();
    }

    /**
     * Perform all tests using the two specified players
     */
    public void performTests(Player attacker, Player defender) {
        this.attacker = attacker;
        this.defender = defender;
        beforeAll();
        tally = new Tally();

        runAttacks(); // with no armour
        testArmour();

        Bukkit.getScheduler().runTaskLater(ocm, this::afterAll, delay);
    }

    private void runAttacks() {
        testMelee();
        testOverdamage();
    }

    private void appendExtras(Runnable runnable) {
        final Runnable oldExtras = extras;
        extras = () -> {
            oldExtras.run();
            runnable.run();
        };
    }

    private void testArmour() {
        extras = () -> {
            // give defender some armour
            defender.getInventory().setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
            defender.updateInventory();
        };
        runAttacks();
    }

    private void testMelee() {
        for (Material weaponType : WeaponDamages.getMaterialDamages().keySet()) {
            Bukkit.getScheduler().runTaskLater(ocm, () -> {
                beforeEach();
                testMeleeAttack(weaponType, 0);
            }, delay);

            delay += 2;
        }
    }

    private void testOverdamage() {
        Material[] weapons = {Material.WOODEN_HOE, Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_AXE};
        appendExtras(() -> defender.setMaximumNoDamageTicks(100));
        for (Material weaponType : weapons) {
            Bukkit.getScheduler().runTaskLater(ocm, () -> {
                beforeEach();
                testMeleeAttack(weaponType, 10);
            }, delay);

            delay += 40;
        }
    }

    private void testMeleeAttack(Material weaponType, long attackDelay) {
        ItemStack weapon = new ItemStack(weaponType);

        //todo include weapon enchants, armour etc. in expected calculations
        double expectedDamage = WeaponDamages.getDamage(weaponType);
        if ((float) defender.getNoDamageTicks() > (float) defender.getMaximumNoDamageTicks() / 2.0F)
            expectedDamage -= defender.getLastDamage();

        attacker.getInventory().setItemInMainHand(weapon);
        attacker.updateInventory();

        double finalExpectedDamage = expectedDamage;
        monitor(finalExpectedDamage, attackDelay, "Melee Attack " + weaponType);
    }

    private void monitor(double expectedDamage, long attackDelay, String message) {
        final boolean[] eventHappened = {false};

        Listener listener = new Listener() {
            @EventHandler(priority = EventPriority.MONITOR)
            public void onEvent(EntityDamageByEntityEvent e) {
                if (e.getDamager().getUniqueId() != attacker.getUniqueId() ||
                        e.getEntity().getUniqueId() != defender.getUniqueId()) return;
                eventHappened[0] = true;
                TesterUtils.assertEquals(expectedDamage, e.getFinalDamage(), tally, message, attacker, defender);
            }
        };

        Bukkit.getServer().getPluginManager().registerEvents(listener, ocm);

        // Delay the attack when testing overdamage
        Bukkit.getScheduler().runTaskLater(ocm, () -> {
            attacker.attack(defender);
            afterEach();
            EntityDamageByEntityEvent.getHandlerList().unregister(listener);
            if (!eventHappened[0]) tally.failed();
        }, attackDelay);
    }

    private void beforeAll() {
        for (Player player : new Player[]{attacker, defender}) {
            player.setGameMode(GameMode.SURVIVAL);
            final PlayerInfo info = new PlayerInfo(player.getLocation(), player.getMaximumNoDamageTicks(), player.getInventory().getContents());
            playerInfo.put(player.getUniqueId(), info);
            player.setMaximumNoDamageTicks(0);
        }
    }

    private void afterAll() {
        for (Player player : new Player[]{attacker, defender}) {
            final UUID uuid = player.getUniqueId();
            final PlayerInfo info = playerInfo.get(uuid);
            playerInfo.remove(uuid);
            player.getInventory().setContents(info.inventoryContents);
            player.setMaximumNoDamageTicks(info.maximumNoDamageTicks);
            Messenger.send(player, "Passed: &a%d &rFailed: &c%d &rTotal: &7%d", tally.getPassed(), tally.getFailed(), tally.getTotal());
        }
    }

    private void beforeEach() {
        for (Player player : new Player[]{attacker, defender}) {
            player.getInventory().clear();
            player.setExhaustion(0);
            player.setHealth(20);
        }
        extras.run();
    }

    private void afterEach() {
        for (Player player : new Player[]{attacker, defender}) {
            final PlayerInfo info = playerInfo.get(player.getUniqueId());
            player.setExhaustion(0);
            player.setHealth(20);
            player.teleport(info.location);
        }
    }
}
