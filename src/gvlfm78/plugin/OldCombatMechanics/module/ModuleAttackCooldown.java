package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.Config;
import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.OCMUpdateChecker;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Created by Rayzr522 on 6/25/16.
 */
public class ModuleAttackCooldown extends Module {

    public ModuleAttackCooldown(OCMMain plugin) {
        super(plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLogin(PlayerJoinEvent e) {

        OCMUpdateChecker updateChecker = new OCMUpdateChecker(plugin);
        Player p = e.getPlayer();
        World world = p.getWorld();

        // Checking for updates
        if (p.hasPermission("OldCombatMechanics.notify")) {
            updateChecker.sendUpdateMessages(p);
        }

        double GAS = plugin.getConfig().getDouble("disable-attack-cooldown.general-attack-speed");

        if (Config.moduleEnabled("disable-attack-cooldown", world)) {// Setting to no cooldown
            AttributeInstance attribute = p.getAttribute(Attribute.GENERIC_ATTACK_SPEED);
            double baseValue = attribute.getBaseValue();
            if (baseValue != GAS) {
                attribute.setBaseValue(GAS);
                p.saveData();
            }
        } else {// Re-enabling cooldown
            AttributeInstance attribute = p.getAttribute(Attribute.GENERIC_ATTACK_SPEED);
            double baseValue = attribute.getBaseValue();
            if (baseValue != 4) {
                attribute.setBaseValue(4);
                p.saveData();
            }
        }

    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWorldChange(PlayerChangedWorldEvent e) {

        Player player = e.getPlayer();
        World world = player.getWorld();

        AttributeInstance attribute = player.getAttribute(Attribute.GENERIC_ATTACK_SPEED);
        double baseValue = attribute.getBaseValue();

        if (Config.moduleEnabled("disable-attack-cooldown", world)) {//Disabling cooldown

            double GAS = plugin.getConfig().getDouble("disable-attack-cooldown.general-attack-speed");

            if (baseValue != GAS) {
                attribute.setBaseValue(GAS);
                player.saveData();
            }

        } else {//Re-enabling cooldown

            if (baseValue != 4) {
                attribute.setBaseValue(4);
                player.saveData();
            }

        }

    }

}
