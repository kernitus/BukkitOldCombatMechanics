package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;

import java.util.HashSet;
import java.util.Set;

/**
 * Allows you to throw enderpearls as often as you like, not only after a cooldown.
 */
public class ModuleDisableEnderpearlCooldown extends Module {

    /**
     * Contains players that threw an ender pearl. As the handler calls launchProjectile, which also calls the event,
     * we need to ignore that event call.
     */
    private Set<Player> ignoredPlayers;

    public ModuleDisableEnderpearlCooldown(OCMMain plugin){
        super(plugin, "disable-enderpearl-cooldown");

        ignoredPlayers = new HashSet<>();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerShoot(ProjectileLaunchEvent e){
        if(!(e.getEntity() instanceof EnderPearl)) return;
        ProjectileSource shooter = e.getEntity().getShooter();

        if(!(shooter instanceof Player)) return;
        Player player = (Player) shooter;

        if(ignoredPlayers.contains(player)) return;

        e.setCancelled(true);

        // ignore the event triggered by launchProjectile
        ignoredPlayers.add(player);

        EnderPearl pearl = player.launchProjectile(EnderPearl.class);

        ignoredPlayers.remove(player);

        pearl.setVelocity(player.getEyeLocation().getDirection().multiply(2));

        if(player.getGameMode() == GameMode.CREATIVE) return;

        ItemStack enderpearlItemStack;
        if(isEnderPearl(player.getInventory().getItemInMainHand())){
            enderpearlItemStack = player.getInventory().getItemInMainHand();
        } else if(isEnderPearl(player.getInventory().getItemInOffHand())){
            enderpearlItemStack = player.getInventory().getItemInOffHand();
        } else {
            return;
        }

        enderpearlItemStack.setAmount(enderpearlItemStack.getAmount() - 1);
    }

    private boolean isEnderPearl(ItemStack itemStack){
        return itemStack != null && itemStack.getType() == Material.ENDER_PEARL;
    }
}
