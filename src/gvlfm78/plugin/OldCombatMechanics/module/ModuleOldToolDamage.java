package gvlfm78.plugin.OldCombatMechanics.module;

import gvlfm78.plugin.OldCombatMechanics.Config;
import gvlfm78.plugin.OldCombatMechanics.MobDamage;
import gvlfm78.plugin.OldCombatMechanics.OCMMain;
import gvlfm78.plugin.OldCombatMechanics.WeaponDamages;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Created by Rayzr522 on 6/25/16.
 */
public class ModuleOldToolDamage extends Module {

    WeaponDamages WD = new WeaponDamages(plugin);
    private String[] weapons = {"axe", "pickaxe", "spade", "hoe"};

    private static ModuleOldToolDamage INSTANCE;

    public ModuleOldToolDamage(OCMMain plugin) {
        super(plugin);
        INSTANCE = this;
    }

    // Add when finished:
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamaged(EntityDamageByEntityEvent e) {
        World world = e.getDamager().getWorld();

        if (!(e.getDamager() instanceof Player)) {
            return;
        }

        Player p = (Player) e.getDamager();
        Material mat = p.getInventory().getItemInMainHand().getType();

        if (isHolding(mat, weapons) && Config.moduleEnabled("old-tool-damage", world)) {
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

    public static void onAttack(EntityDamageByEntityEvent e) {

        INSTANCE.onEntityDamaged(e);

    }

    private double getSharpnessDamage(int level) {
        return level >= 1 ? 1 + 0.5 * (level - 1) : 0;
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
