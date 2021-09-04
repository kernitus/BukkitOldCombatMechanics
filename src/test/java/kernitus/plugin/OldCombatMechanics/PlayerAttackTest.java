package kernitus.plugin.OldCombatMechanics;

import be.seeseemelk.mockbukkit.MockBukkit;
import kernitus.plugin.OldCombatMechanics.module.ModuleOldToolDamage;
import kernitus.plugin.OldCombatMechanics.utilities.damage.ToolDamage;
import kernitus.plugin.OldCombatMechanics.utilities.damage.WeaponDamages;
import org.bukkit.Material;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
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

    @Test
    void testPlayerAttackHand() {
        OCMPlayerMock playerA = server.addPlayer();
        OCMPlayerMock playerB = server.addPlayer();

        EntityEquipment equipment = mock(EntityEquipment.class);
        Material weaponMaterial = Material.STONE_AXE;
        ItemStack weapon = new ItemStack(weaponMaterial);
        when(equipment.getItemInMainHand()).thenReturn(weapon);
        playerA.setEquipment(equipment);

        // Register the listeners for the modules we want to test
        ModuleOldToolDamage module = new ModuleOldToolDamage(ocm);
        server.getPluginManager().registerEvents(module, ocm);

        // Call base event with 1.9 damage value
        EntityDamageByEntityEvent event = playerA.attackEntity(playerB, ToolDamage.getDamage(weaponMaterial));

        // OldToolDamage module should have changed the value of the attack
        double damage = event.getDamage();
        double expectedDamage = WeaponDamages.getDamage(weaponMaterial);
        assertEquals(expectedDamage, damage);
    }
}
