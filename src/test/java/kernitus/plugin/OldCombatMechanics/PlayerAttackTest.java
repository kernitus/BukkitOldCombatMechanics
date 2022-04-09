package kernitus.plugin.OldCombatMechanics;

import be.seeseemelk.mockbukkit.MockBukkit;
import kernitus.plugin.OldCombatMechanics.module.ModuleOldToolDamage;
import kernitus.plugin.OldCombatMechanics.utilities.Config;
import kernitus.plugin.OldCombatMechanics.utilities.damage.EntityDamageByEntityListener;
import kernitus.plugin.OldCombatMechanics.utilities.damage.WeaponDamages;
import org.bukkit.Material;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PlayerAttackTest {

    private OCMServerMock server;
    private OCMMain ocm;

    @BeforeEach
    public void setUp() {
        server = MockBukkit.mock(new OCMServerMock());
        ocm = MockBukkit.load(OCMMain.class);
    }

    @AfterEach
    public void tearDown() {
        MockBukkit.unmock();
    }

    /*
    @Test
    void testPlayerAttackHand() {
        OCMPlayerMock playerA = server.addPlayer();
        OCMPlayerMock playerB = server.addPlayer();

        EntityEquipment equipment = mock(EntityEquipment.class);
        Material weaponMaterial = Material.STONE_AXE;
        ItemStack weapon = new ItemStack(weaponMaterial);
        when(equipment.getItemInMainHand()).thenReturn(weapon);
        playerA.setEquipment(equipment);

        // Enable the modules we want to test
        ModuleLoader.addModule(new EntityDamageByEntityListener(ocm));
        ModuleLoader.addModule(new ModuleOldToolDamage(ocm));
        Config.reload();

        // Call base event with 1.9 damage value
        EntityDamageByEntityEvent event = playerA.attackEntity(playerB, ToolDamage.getDamage(weaponMaterial));

        // OldToolDamage module should have changed the value of the attack
        double damage = event.getDamage();
        double expectedDamage = WeaponDamages.getDamage(weaponMaterial);
        assertEquals(expectedDamage, damage);
    }
     */

    @Test
    void testRapidSuccessionAttacks() {
        OCMPlayerMock playerA = server.addPlayer();
        OCMPlayerMock playerB = server.addPlayer();

        EntityEquipment equipment = mock(EntityEquipment.class);
        Material weaponMaterial = Material.STONE_AXE;
        ItemStack weapon = new ItemStack(weaponMaterial);
        when(equipment.getItemInMainHand()).thenReturn(weapon);
        playerA.setEquipment(equipment);

        // Enable the modules we want to test
        ModuleLoader.addModule(new EntityDamageByEntityListener(ocm));
        ModuleLoader.addModule(new ModuleOldToolDamage(ocm));
        Config.reload();

        playerA.attack(playerB);
        double expectedHealth = 20 - WeaponDamages.getDamage(weaponMaterial);
        assertEquals(expectedHealth, playerB.getHealth());
        //EntityDamageByEntityEvent event2 = playerA.attackEntity(playerB, ToolDamage.getDamage(weaponMaterial));
        //EntityDamageByEntityEvent event3 = playerA.attackEntity(playerB, ToolDamage.getDamage(weaponMaterial));

        System.out.println(playerB.getHealth());
    }
}
