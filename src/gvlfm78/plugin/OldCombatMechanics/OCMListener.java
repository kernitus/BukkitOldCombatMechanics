package kernitus.plugin.OldCombatMechanics;

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
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Arrays;
import java.util.List;

public class OCMListener implements Listener {

    private OCMMain plugin;
    private String[] weapons = {"axe", "pickaxe", "spade", "hoe"};
    private List<PotionEffect> enchantedGoldenAppleEffects = Arrays.asList(new PotionEffect(PotionEffectType.REGENERATION, 30*20, 4), new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 300*20, 0), new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 300*20, 0), new PotionEffect(PotionEffectType.ABSORPTION, 120*20, 0));
    private List<PotionEffect> goldenAppleEffects = Arrays.asList(new PotionEffect(PotionEffectType.REGENERATION, 5*20, 1), new PotionEffect(PotionEffectType.ABSORPTION, 120*20, 0));

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
    public void onPrepareItemCraft(PrepareItemCraftEvent e) {

        ItemStack item = e.getInventory().getResult();

        if (item.getType() == Material.GOLDEN_APPLE && item.getDurability() == (short) 1) {

            if (Config.moduleEnabled("old-golden-apples") && !Config.moduleSettingEnabled("old-golden-apples", "enchant-golden-apple-crafting")) {

                e.getInventory().setResult(null);

            }

        }

    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemConsume(PlayerItemConsumeEvent e) {

        if (e.getItem().getType() != Material.GOLDEN_APPLE) {
            return;
        }

        if (!Config.moduleEnabled("old-golden-apples") || !Config.moduleSettingEnabled("old-golden-apples", "old-potion-effects")) {
            return;
        }

        ItemStack item = e.getItem();

        e.setCancelled(true);

        int foodLevel = e.getPlayer().getFoodLevel();
        foodLevel = foodLevel + 4 > 20 ? 20 : foodLevel + 4;

        item.setAmount(item.getAmount() - 1);

        if (item.getDurability() == (short) 1) {

            e.getPlayer().addPotionEffects(enchantedGoldenAppleEffects);

        } else {

            e.getPlayer().addPotionEffects(goldenAppleEffects);

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

        if (!(e.getDamager() instanceof Player)) {
            return;
        }

        Player p = (Player) e.getDamager();
        Material mat = p.getInventory().getItemInMainHand().getType();

        if (isHolding(mat, "sword") && Config.moduleEnabled("disable-sword-sweep", world)) {
            onSwordAttack(e, p, mat);
        } else if (isHolding(mat, weapons) && Config.moduleEnabled("old-tool-damage", world)) {
            onAttack(e, p, mat);
        }
    }

    private void onAttack(EntityDamageByEntityEvent e, Player p, Material mat) {
        ItemStack item = p.getInventory().getItemInMainHand();
        EntityType entity = e.getEntityType();

        double baseDamage = e.getDamage();
        double enchantmentDamage = (MobDamage.applyEntityBasedDamage(entity, item, baseDamage) + getSharpnessDamage(item.getEnchantmentLevel(Enchantment.DAMAGE_ALL))) - baseDamage;

        double divider = WD.getDamage(mat);
        double newDamage = (baseDamage - enchantmentDamage) / divider;
        newDamage += enchantmentDamage;//Re-add damage from enchantments
        e.setDamage(newDamage);
        if (Config.debugEnabled())
            p.sendMessage("Item: " + mat.toString() + " Old Damage: " + baseDamage + " Enchantment Damage: " + enchantmentDamage + " Divider: " + divider + " Afterwards damage: " + e.getFinalDamage());
    }

    private double getSharpnessDamage(int level) {
        return level >= 1 ? 1 + 0.5 * (level - 1) : 0;
    }

    private void onSwordAttack(EntityDamageByEntityEvent e, Player p, Material mat) {
        //Disable sword sweep

        int locHashCode = p.getLocation().hashCode(); // ATTACKER
        if (e.getDamage() == 1.0) {
            // Possibly a sword sweep attack
            if (sweepTask().swordLocations.contains(locHashCode)) {
                e.setCancelled(true);
            }
        } else {
            sweepTask().swordLocations.add(locHashCode);
        }

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

    private OCMSweepTask sweepTask() {
        return plugin.sweepTask();
    }

}