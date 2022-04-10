package kernitus.plugin.OldCombatMechanics.module;

import be.seeseemelk.mockbukkit.MockBukkit;
import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.OCMPlayerMock;
import kernitus.plugin.OldCombatMechanics.OCMServerMock;
import kernitus.plugin.OldCombatMechanics.utilities.damage.ToolDamage;
import org.bukkit.Material;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ModuleOldToolDamageTest {

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

    @Test
    void testToolDamagePlayerAttack() {
        OCMPlayerMock playerA = server.addPlayer();
        OCMPlayerMock playerB = server.addPlayer();

        EntityEquipment equipment = mock(EntityEquipment.class);

        for (ToolDamage value : ToolDamage.values()) {
            Material weaponMaterial;
            try {
                weaponMaterial = Material.valueOf(value.toString());
            } catch (IllegalArgumentException e) {
                // We want to skip over the items named different across versions
                continue;
            }

            ItemStack weapon = new ItemStack(weaponMaterial);
            when(equipment.getItemInMainHand()).thenReturn(weapon);
            playerA.setEquipment(equipment);

            // Register the listeners for the modules we want to test
            ModuleOldToolDamage module = new ModuleOldToolDamage(ocm);
            server.getPluginManager().registerEvents(module, ocm);

            // Call base event with 1.9 damage value
            //EntityDamageByEntityEvent event = playerA.attackEntity(playerB, ToolDamage.getDamage(weaponMaterial));

            // OldToolDamage module should have changed the value of the attack
            //double damage = event.getDamage();
            //double expectedDamage = WeaponDamages.getDamage(weaponMaterial);
            //assertEquals(expectedDamage, damage);
        }
    }
}