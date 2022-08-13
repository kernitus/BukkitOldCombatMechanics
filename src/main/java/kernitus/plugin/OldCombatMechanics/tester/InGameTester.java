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
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.UUID;

public class InGameTester {

    private final OCMMain ocm;
    private Tally tally;
    private CommandSender sender;
    private Player attacker, defender;
    private FakePlayer fakeAttacker, fakeDefender;
    private final Queue<OCMTest> testQueue;

    // todo test with armour
    // todo test with enchanted weapons
    // todo test with enchanted armour
    // todo test armour durability
    // todo test with critical hits
    // todo test with potion effects
    // todo test with shield blocking

    public InGameTester(OCMMain ocm) {
        this.ocm = ocm;
        testQueue = new ArrayDeque<>();
    }

    /**
     * Perform all tests using the two specified players
     */
    public void performTests(CommandSender sender, Location location) {
        this.sender = sender;
        fakeAttacker = new FakePlayer();
        fakeAttacker.spawn(location.add(2,0,0));
        fakeDefender = new FakePlayer();
        fakeDefender.spawn(location.add(0,0,2));

        this.attacker = Bukkit.getPlayer(fakeAttacker.getUuid());
        this.defender = Bukkit.getPlayer(fakeDefender.getUuid());

        beforeAll();
        tally = new Tally();

        // Queue all tests
        runAttacks(() -> {}); // with no armour
        //testArmour();

        // Run all tests in the queue
        runQueuedTests();
    }

    private void runAttacks(Runnable preparations) {
        //testMelee(preparations);
        testOverdamage(preparations);
    }

    private void testArmour() {
        runAttacks(() -> {
                    // give defender some armour
                    defender.getInventory().setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
                    defender.updateInventory();
                }
        );
    }

    private void testMelee(Runnable preparations) {
        for (Material weaponType : WeaponDamages.getMaterialDamages().keySet()) {
            queueAttack(weaponType, 2, preparations);
        }
    }

    private void testOverdamage(Runnable preparations) {
        Material[] weapons = {Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_AXE};
        queueAttack(Material.WOODEN_HOE, 2, () -> {
            defender.setMaximumNoDamageTicks(10);
        });

        for (Material weaponType : weapons) {
            queueAttack(weaponType, 2, () -> {
                        preparations.run();
                        //defender.setLastDamage(1);
                        //defender.setMaximumNoDamageTicks(20);
                        //defender.setNoDamageTicks(15);
                    }
            );
        }
    }

    private void queueAttack(Material weaponType, long attackDelay, Runnable preparations) {
        final OCMTest test = new OCMTest(weaponType, attackDelay, "Melee Attack " + weaponType, preparations);
        testQueue.add(test);
    }

    private double calculateExpectedDamage(Material weaponType) {
        //todo include weapon enchants, armour etc. in expected calculations
        double expectedDamage = WeaponDamages.getDamage(weaponType);
        double lastDamage = defender.getLastDamage();
        if ((float) defender.getNoDamageTicks() > (float) defender.getMaximumNoDamageTicks() / 2.0F &&
                expectedDamage > lastDamage) // If not greater, event will not be called at all
            expectedDamage -= lastDamage;
        return expectedDamage;
    }

    private void runQueuedTests() {
        Messenger.sendNormalMessage(sender, "Running " + testQueue.size() + " tests");

        Listener listener = new Listener() {
            @EventHandler(priority = EventPriority.MONITOR)
            public void onEvent(EntityDamageByEntityEvent e) {
                if (e.getDamager().getUniqueId() != attacker.getUniqueId() ||
                        e.getEntity().getUniqueId() != defender.getUniqueId()) return;

                final OCMTest test = testQueue.remove();

                TesterUtils.assertEquals(calculateExpectedDamage(test.weaponType), e.getFinalDamage(),
                        tally, test.message, sender);
            }
        };

        Bukkit.getServer().getPluginManager().registerEvents(listener, ocm);

        final long testCount = testQueue.size();

        // Cumulative attack delay for scheduling all the tests
        long attackDelay = 0;

        for (OCMTest test : testQueue) {
            attackDelay += test.attackDelay;

            Bukkit.getScheduler().runTaskLater(ocm, () -> {
                beforeEach();
                test.preparations.run();

                final ItemStack weapon = new ItemStack(test.weaponType);
                if(weapon.hasItemMeta()) {
                    final ItemMeta meta = weapon.getItemMeta();
                    meta.addAttributeModifier(Attribute.GENERIC_ATTACK_SPEED,
                            new AttributeModifier(UUID.randomUUID(), "speed", 100,
                                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.HAND));
                    weapon.setItemMeta(meta);
                }
                attacker.getInventory().setItemInMainHand(weapon);
                attacker.updateInventory();

                double expectedDamage = calculateExpectedDamage(attacker.getInventory().getItemInMainHand().getType());
                Messenger.sendNormalMessage(sender, "Max: " + defender.getMaximumNoDamageTicks() +
                        " Ticks: " + defender.getNoDamageTicks() + " Last: " + defender.getLastDamage() +
                        " Overdamage: " + ((float) defender.getNoDamageTicks() > (float) defender.getMaximumNoDamageTicks() / 2.0F &&
                        expectedDamage > defender.getLastDamage())
                        + " expected: " + expectedDamage);

                fakeAttacker.attack(fakeDefender.getEntityPlayer());

                afterEach();
            }, attackDelay);

        }

        Bukkit.getScheduler().runTaskLater(ocm, () -> {
            afterAll(testCount);
            EntityDamageByEntityEvent.getHandlerList().unregister(listener);
        }, attackDelay + 1);
    }

    private void beforeAll() {
        for (Player player : new Player[]{attacker, defender}) {
            player.setGameMode(GameMode.SURVIVAL);
            player.setMaximumNoDamageTicks(0);
            player.setNoDamageTicks(0);
        }
    }

    private void afterAll(long testCount) {
        fakeAttacker.removePlayer();
        fakeDefender.removePlayer();

        final long missed = testCount - tally.getTotal();
        Messenger.send(sender, "Passed: &a%d &rFailed: &c%d &rTotal: &7%d &rMissed: &7%d", tally.getPassed(), tally.getFailed(), tally.getTotal(), missed);
    }

    private void beforeEach() {
        for (Player player : new Player[]{attacker, defender}) {
            player.getInventory().clear();
            player.setExhaustion(0);
            player.setHealth(20);
        }
    }

    private void afterEach() {
        for (Player player : new Player[]{attacker, defender}) {
            player.setExhaustion(0);
            player.setHealth(20);
        }
    }
}
