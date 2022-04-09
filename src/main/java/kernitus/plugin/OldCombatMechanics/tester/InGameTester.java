package kernitus.plugin.OldCombatMechanics.tester;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.damage.WeaponDamages;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class InGameTester {

    private final OCMMain ocm;
    private final Map<UUID, Location> playerLocations;

    public InGameTester(OCMMain ocm){
        this.ocm = ocm;
        playerLocations = new HashMap<>();
    }

    public void performTests(Player attacker, Player attackee){

        long delay = 0;

        for (Material weaponType : WeaponDamages.getMaterialDamages().keySet()) {
            Bukkit.getScheduler().runTaskLater(ocm,() -> {
                preparePlayer(attacker, attackee);
                testMeleeAttack(attacker,attackee,weaponType);
                cleanupPlayer(attacker,attackee);
            },delay);

            delay += 2;
        }
    }

    private void testMeleeAttack(Player attacker, Player attackee, Material weaponType){
        ItemStack weapon = new ItemStack(weaponType);

        double expectedDamage = WeaponDamages.getDamage(weaponType);
        attacker.getInventory().setItemInMainHand(weapon);

        class EntityDamageByEntityMonitor implements Listener {
            @EventHandler(priority = EventPriority.MONITOR)
            public void onEntityDamageByEntityEvent(EntityDamageByEntityEvent e){
                if(e.getDamager() != attacker || e.getEntity() != attackee) return;

                TesterUtils.assertEquals(expectedDamage,e.getFinalDamage(),"Melee Attack " + weaponType, attacker,attackee);
            }
        }

        EntityDamageByEntityMonitor mon = new EntityDamageByEntityMonitor();
        Bukkit.getServer().getPluginManager().registerEvents(mon, ocm);

        attacker.attack(attackee);

        EntityDamageByEntityEvent.getHandlerList().unregister(mon);
    }

    private double getMeleeAttackDamage(EntityEquipment equipment){
        Material weapon = equipment.getItemInMainHand().getType();
        if(weapon == Material.AIR) weapon = equipment.getItemInOffHand().getType();
        return WeaponDamages.getDamage(weapon);
    }

    private void preparePlayer(Player... players){
        for (Player player : players) {
            playerLocations.put(player.getUniqueId(),player.getLocation());
            player.setGameMode(GameMode.SURVIVAL);
            player.getInventory().clear();
            player.setExhaustion(0);
            player.setHealth(20);
        }
    }

    private void cleanupPlayer(Player... players){
        for (Player player : players) {
            player.setExhaustion(0);
            player.setHealth(20);
            player.teleport(playerLocations.get(player.getUniqueId()));
            playerLocations.remove(player.getUniqueId());
        }
    }
}
