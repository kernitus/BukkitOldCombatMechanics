package kernitus.plugin.OldCombatMechanics;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public class OCMListener implements Listener {

    private OCMMain plugin;
    private FileConfiguration config;

    public OCMListener(OCMMain instance) {

        this.plugin = instance;
        this.config = plugin.getConfig();

    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLogin(PlayerJoinEvent e) {

        OCMUpdateChecker updateChecker = new OCMUpdateChecker(plugin);
        Player p = e.getPlayer();

        // Checking for updates
        if (p.hasPermission("OldCombatMechanics.notify")) {
            updateChecker.sendUpdateMessages(p);
        }

        if (moduleEnabled("plugin-active")) {// Setting to no cooldown
            AttributeInstance attribute = p.getAttribute(Attribute.GENERIC_ATTACK_SPEED);
            double baseValue = attribute.getBaseValue();
            if (baseValue != 1024) {
                attribute.setBaseValue(1024);
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

    // Add when finished: @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamaged(EntityDamageByEntityEvent e) {


        // Add '|| !moduleEnabled("disable-knockback-attack")' when you add that feature
        if (!moduleEnabled("old-axe-damage")) {
            return;
        }

        if (!(e.getDamager() instanceof Player)) {
            return;
        }

        if (isHolding((Player) e.getDamager(), "axe")) {

            onAxeAttack(e);

        }

    }

    private void onAxeAttack(EntityDamageByEntityEvent e) {

    }

    private void onSwordAttack() {
    }

    private boolean isHolding(Player p, String type) {

        return p.getItemOnCursor().getType().toString().endsWith("_" + type.toUpperCase());

    }

    private boolean moduleEnabled(String name) {
        return config.getBoolean(name);
    }

}