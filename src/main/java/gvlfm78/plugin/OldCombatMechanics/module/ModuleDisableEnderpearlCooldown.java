package gvlfm78.plugin.OldCombatMechanics.module;

import gvlfm78.plugin.OldCombatMechanics.OCMMain;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public class ModuleDisableEnderpearlCooldown extends Module {

    public ModuleDisableEnderpearlCooldown(OCMMain plugin){
        super(plugin, "disable-enderpearl-cooldown");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerShoot(PlayerInteractEvent e){

        Action action = e.getAction();

        if(action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player player = e.getPlayer();

        if(!isEnabled(player.getWorld())) return;

        if(e.getMaterial() != Material.ENDER_PEARL) return;

        if(e.isCancelled()) return;

        e.setCancelled(true);

        EnderPearl pearl = player.launchProjectile(EnderPearl.class);

        pearl.setVelocity(player.getEyeLocation().getDirection().multiply(2));

        GameMode mode = player.getGameMode();

        if(mode != GameMode.CREATIVE){
            PlayerInventory inv = player.getInventory();

            boolean offhand = e.getHand() == EquipmentSlot.OFF_HAND;
            ItemStack hand = e.getItem();

            hand.setAmount(hand.getAmount() - 1);

            if(e.getHand() == EquipmentSlot.HAND){
                inv.setItemInMainHand(hand);
            } else {
                inv.setItemInOffHand(hand);
            }
        }
    }
}
