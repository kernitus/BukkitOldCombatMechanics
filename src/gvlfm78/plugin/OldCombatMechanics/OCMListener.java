package kernitus.plugin.OldCombatMechanics;

import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public class OCMListener implements Listener {

    private OCMMain plugin;
    private FileConfiguration config;

    public OCMListener(OCMMain instance) {

        this.plugin = instance;
        this.config = plugin.getConfig();

    }

    OCMTask task = new OCMTask(plugin);

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
        if (Config.moduleEnabled("disable-player-collisions")) {
            task.addPlayerToScoreboard(p);
        } else {
            task.removePlayerFromScoreboard(p);
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

        if (Config.moduleEnabled("disable-player-collisions", world))
            task.addPlayerToScoreboard(player);
        else {
            task.removePlayerFromScoreboard(player);
        }
    }

    // Add when finished:
    // @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamaged(EntityDamageByEntityEvent e) {
        World world = e.getDamager().getWorld();

        // Add '|| !moduleEnabled("disable-knockback-attack")' when you add that feature
        if (!Config.moduleEnabled("old-tool-damage", world)) {
            return;
        }

        if (!(e.getDamager() instanceof Player)) {
            return;
        }

        Player p = (Player) e.getDamager();

        if (isHolding(p, "axe")) {

            onAxeAttack(e, p);

        }

    }

    private void onAxeAttack(EntityDamageByEntityEvent e, Player p) {
        AttributeInstance attribute = p.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        double baseValue = attribute.getBaseValue();
        //Get item in main hand, check if one of following types and set new base value accordingly
        //Stone 4
        //Iron 5
        //Diamond 6
        //Gold,wood 3
        attribute.setBaseValue(6);
    }

    private void onSwordAttack() {
    }

    private boolean isHolding(Player p, String type) {
        return p.getInventory().getItemInMainHand().getType().toString().endsWith("_" + type.toUpperCase());
    }

}