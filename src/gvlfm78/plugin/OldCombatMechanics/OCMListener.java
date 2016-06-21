package gvlfm78.plugin.OldCombatMechanics;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

public class OCMListener implements Listener {

    private OCMMain plugin;

    public OCMListener(OCMMain instance) {
        this.plugin = instance;
    }

    OCMTask task = new OCMTask(plugin);
    WeaponDamages WD = new WeaponDamages(plugin);

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
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamaged(EntityDamageByEntityEvent e) {
        World world = e.getDamager().getWorld();

        // Add '|| !moduleEnabled("disable-sword-sweep")' when you add that feature
        if (!Config.moduleEnabled("old-tool-damage", world)) {
            return;
        }

        if (!(e.getDamager() instanceof Player)) {
            return;
        }

        Player p = (Player) e.getDamager();
        Material mat = p.getInventory().getItemInMainHand().getType();
        String[] weapons = {"axe", "pickaxe", "spade", "hoe"};

        if (isHolding(mat, "sword")) {
            onSwordAttack(e, p, mat);
        } else if (isHolding(mat, weapons)) {
            onAttack(e, p, mat);
        }
    }

    private void onAttack(EntityDamageByEntityEvent e, Player p, Material mat) {
        ItemStack item = p.getInventory().getItemInMainHand();
        EntityType entity = e.getEntityType();

        double baseDamage = e.getDamage();
        double enchantmentDamage = (MobDamage.applyEntityBasedDamage(entity, item, baseDamage) + getSharpnessDamage(item.getEnchantmentLevel(Enchantment.DAMAGE_ALL))) - baseDamage;
//        double baseDamage = e.getDamage();
//    	double oldDamage = baseDamage; //DEBUG, remove later
//    	double enchantmentDamage = 0;
//
//        Map<Enchantment, Integer> enchants = item.getEnchantments();
//
//        for(Enchantment ench : enchants.keySet()){
//        	p.sendMessage("Enchantment tested: "+ench.getName());
//        	if(ench.equals(Enchantment.DAMAGE_ALL)){//Sharpness
//        		enchantmentDamage += getSharpnessDamage(enchants.get(ench));
//        		p.sendMessage("Enchantment damage: "+enchantmentDamage);
//        	}
//        	if(ench.equals(Enchantment.DAMAGE_UNDEAD)){//Smite
//        		enchantmentDamage += MobDamage.applyEntityBasedDamage(entity, item, baseDamage);
//        		p.sendMessage("Enchantment damage: "+enchantmentDamage);
//        	}
//        	if(ench.equals(Enchantment.DAMAGE_ARTHROPODS)){//Bane of Arthropods
//        		enchantmentDamage += MobDamage.applyEntityBasedDamage(entity, item, baseDamage);
//        		p.sendMessage("Enchantment damage: "+enchantmentDamage);
//        	}
//        }
//
//        baseDamage -= enchantmentDamage;//Remove damage from enchantments


        double divider = WD.getDamage(mat);
        double newDamage = baseDamage / divider;
        newDamage += enchantmentDamage;//Re-add damage from enchantments
        e.setDamage(newDamage);
        p.sendMessage("Item " + mat.toString() + /*" Old damage: " + oldDamage +*/ " Enchantment Damage: " + enchantmentDamage + " Divider: " + divider + " Afterwards damage: " + e.getFinalDamage());//DEBUG
    }

    private double getSharpnessDamage(int level) {
        return level >= 1 ? 1 + 0.5 * (level - 1) : 0;
    }

    private void onSwordAttack(EntityDamageByEntityEvent e, Player p, Material mat) {
        //Disable sword sweep
        onAttack(e, p, mat);
    }

    private boolean isHolding(Material mat, String type) {
        return mat.toString().endsWith("_" + type.toUpperCase());
    }

    private boolean isHolding(Material mat, String[] types) {
        boolean hasAny = false;
        for (String type : types) {
            if (isHolding(mat, type))
                hasAny = true;
        }
        return hasAny;
    }
}