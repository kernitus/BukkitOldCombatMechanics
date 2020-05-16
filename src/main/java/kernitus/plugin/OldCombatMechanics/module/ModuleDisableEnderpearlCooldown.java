package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.projectiles.ProjectileSource;

import java.util.*;

/**
 * Allows you to throw enderpearls as often as you like, not only after a cooldown.
 */
public class ModuleDisableEnderpearlCooldown extends Module {

    /**
     * Contains players that threw an ender pearl. As the handler calls launchProjectile, which also calls the event,
     * we need to ignore that event call.
     */
    private final Set<UUID> ignoredPlayers = new HashSet<>();
    private final Map<UUID, Long> lastLaunched = new HashMap<>();
    private int cooldown;

    public ModuleDisableEnderpearlCooldown(OCMMain plugin){
        super(plugin, "disable-enderpearl-cooldown");
        reload();
    }

    public void reload(){
        cooldown = module().getInt("cooldown");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerShoot(ProjectileLaunchEvent e){
        if(!(e.getEntity() instanceof EnderPearl)) return;
        ProjectileSource shooter = e.getEntity().getShooter();

        if(!(shooter instanceof Player)) return;
        Player player = (Player) shooter;
        UUID uuid = player.getUniqueId();

        if(ignoredPlayers.contains(uuid)) return;

        e.setCancelled(true);

        // Check if the cooldown has expired yet
        final long currentTime = System.currentTimeMillis() / 1000;
        if(lastLaunched.containsKey(uuid) && currentTime - lastLaunched.get(uuid) < cooldown)
            return;

        lastLaunched.put(uuid, currentTime);

        // Make sure we ignore the event triggered by launchProjectile
        ignoredPlayers.add(uuid);
        EnderPearl pearl = player.launchProjectile(EnderPearl.class);
        ignoredPlayers.remove(uuid);

        pearl.setVelocity(player.getEyeLocation().getDirection().multiply(2));

        if(player.getGameMode() == GameMode.CREATIVE) return;

        ItemStack enderpearlItemStack;
        PlayerInventory playerInventory = player.getInventory();
        ItemStack mainHand = playerInventory.getItemInMainHand();
        ItemStack offHand = playerInventory.getItemInOffHand();

        if(isEnderPearl(mainHand)) enderpearlItemStack = mainHand;
        else if(isEnderPearl(offHand)) enderpearlItemStack = offHand;
        else return;

        enderpearlItemStack.setAmount(enderpearlItemStack.getAmount() - 1);
    }

    private boolean isEnderPearl(ItemStack itemStack){
        return itemStack != null && itemStack.getType() == Material.ENDER_PEARL;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e){
        lastLaunched.remove(e.getPlayer().getUniqueId());
    }
}
