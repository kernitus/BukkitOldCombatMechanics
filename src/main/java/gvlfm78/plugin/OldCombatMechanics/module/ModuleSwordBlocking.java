package gvlfm78.plugin.OldCombatMechanics.module;

import gvlfm78.plugin.OldCombatMechanics.OCMMain;
import gvlfm78.plugin.OldCombatMechanics.utilities.Chatter;
import gvlfm78.plugin.OldCombatMechanics.utilities.ItemUtils;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.HashMap;
import java.util.UUID;

/**
 * Created by Rayzr522 on 7/4/16.
 */
public class ModuleSwordBlocking extends Module {

    private HashMap<UUID, ItemStack> storedOffhandItems = new HashMap<UUID, ItemStack>();

    public ModuleSwordBlocking(OCMMain plugin) {
        super(plugin, "sword-blocking");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRightClick(PlayerInteractEvent e) {

        if (!e.getAction().toString().startsWith("RIGHT_CLICK")) {
            return;
        }

        if (e.getItem() == null) {
            return;
        }

//        if (e.getItem().getType() == Material.DIAMOND) {
//            e.getPlayer().getInventory().addItem(ItemUtils.makeItem("shield, named &7Sturdy &6Shield, with enchantment durability 10 and damage all 5"));
//        }

        Player p = e.getPlayer();
        World world = p.getWorld();

        if (!isEnabled(world)) {
            return;
        }

        UUID id = p.getUniqueId();
        Chatter.send(p, "Clicky clicky!!");

        if (storedOffhandItems.containsKey(id)) {
            return;
        }

        ItemStack item = e.getItem();

        if (!isHolding(item.getType(), "sword") || hasShield(p)) {
            return;
        }

        PlayerInventory inv = p.getInventory();

        storedOffhandItems.put(id, inv.getItemInOffHand());

        inv.setItemInOffHand(ItemUtils.makeItem("shield, named &0, with enchant durability 10 & silk touch 10"));

    }

    private boolean inMainHand(ItemStack item, Player p) {

        return item.equals(p.getInventory().getItemInMainHand());

    }

    private boolean hasShield(Player p) {
        return p.getInventory().getItemInOffHand().getType() == Material.SHIELD;
    }

    private boolean isHolding(Material mat, String type) {
        return mat.toString().endsWith("_" + type.toUpperCase());
    }

}
