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
import org.bukkit.entity.Entity;
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
        fakeAttacker.spawn(location.add(2, 0, 0));
        fakeDefender = new FakePlayer();
        fakeDefender.spawn(location.add(0, 0, 2));

        this.attacker = Bukkit.getPlayer(fakeAttacker.getUuid());
        this.defender = Bukkit.getPlayer(fakeDefender.getUuid());

        beforeAll();
        tally = new Tally();

        // Queue all tests
        runAttacks(() -> {
        }); // with no armour
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
            queueAttack(weaponType, 1, () -> {
                preparations.run();
                defender.setMaximumNoDamageTicks(0);
            });
        }
    }

    private void testOverdamage(Runnable preparations) {
        // 1, 5, 6, 7, 3, 8 according to OCM
        // 1, 4, 5, 6, 2, 7 according to 1.9+
        Material[] weapons = {Material.WOODEN_HOE, Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_SWORD, Material.WOODEN_PICKAXE, Material.DIAMOND_SWORD};

        for (Material weaponType : weapons) {
            queueAttack(weaponType, 5, () -> {});
        }
    }

    private void queueAttack(Material weaponType, long attackDelay, Runnable preparations) {
        final OCMTest test = new OCMTest(weaponType, attackDelay, "Melee Attack " + weaponType, preparations);
        testQueue.add(test);
    }

    private boolean wasOverdamaged(double rawWeaponDamage) {
        double lastDamage = defender.getLastDamage();
        return (float) defender.getNoDamageTicks() > (float) defender.getMaximumNoDamageTicks() / 2.0F &&
                rawWeaponDamage > lastDamage;
    }

    private float calculateExpectedDamage(Material weaponType) {
        //todo include weapon enchants, armour etc. in expected calculations
        double expectedDamage = WeaponDamages.getDamage(weaponType);

        // Take into account damage reduction because of cooldown
        //final double attackCooldown = defender.getAttackCooldown();
        //expectedDamage *= 0.2F + attackCooldown * attackCooldown * 0.8F;

        if (wasOverdamaged(expectedDamage)) {
            double lastDamage = defender.getLastDamage();
            System.out.println("calculated overdamaged " + expectedDamage + " - " + lastDamage + " = " + (expectedDamage - lastDamage));
            expectedDamage -= lastDamage;
        }

        return (float) expectedDamage;
    }

    private void runQueuedTests() {
        Messenger.sendNormalMessage(sender, "Running " + testQueue.size() + " tests");

        Listener listener = new Listener() {
            @EventHandler(priority = EventPriority.MONITOR)
            public void onEvent(EntityDamageByEntityEvent e) {
                final Entity damager = e.getDamager();
                if (damager.getUniqueId() != attacker.getUniqueId() ||
                        e.getEntity().getUniqueId() != defender.getUniqueId()) return;

                final Material weaponType = ((Player) damager).getInventory().getItemInMainHand().getType();
                final OCMTest test = testQueue.remove();

                final String weaponMessage = "E: " + test.weaponType.name() + " A: " + weaponType.name();

                TesterUtils.assertEquals(calculateExpectedDamage(test.weaponType), (float) e.getFinalDamage(),
                        tally, weaponMessage, sender);
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
                            new AttributeModifier(UUID.randomUUID(), "speed", 1000,
                                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.HAND));
                    weapon.setItemMeta(meta);
                }
                attacker.getInventory().setItemInMainHand(weapon);
                attacker.updateInventory();

                double expectedDamage = calculateExpectedDamage(test.weaponType);
                Messenger.sendNormalMessage(sender, defender.getNoDamageTicks() +
                        "/" + defender.getMaximumNoDamageTicks() + " ticks Last: " + defender.getLastDamage() +
                        " cooldown: " + attacker.getAttackCooldown() +
                        " Overdamaged: " + wasOverdamaged(WeaponDamages.getDamage(test.weaponType))
                        + " expected: " + expectedDamage
                );

                //attacker.attack(defender);
                fakeAttacker.attack(fakeDefender.getEntityPlayer().getBukkitEntity());

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
            //player.setMaximumNoDamageTicks(20);
            player.setNoDamageTicks(0); // remove spawn invulnerability
            player.setInvulnerable(false);
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
