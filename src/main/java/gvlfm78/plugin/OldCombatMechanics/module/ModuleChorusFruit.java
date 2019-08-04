package gvlfm78.plugin.OldCombatMechanics.module;

import gvlfm78.plugin.OldCombatMechanics.OCMMain;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.ThreadLocalRandom;

/**
 * A module to control chorus fruits.
 */
public class ModuleChorusFruit extends Module {

    public static final String LAST_TELEPORT_METADATA_KEY = "last-chorous-teleport";

    /**
     * Creates a new module.
     *
     * @param plugin the plugin instance
     */
    public ModuleChorusFruit(OCMMain plugin){
        super(plugin, "chorus-fruit");
    }

    private double getMaxTeleportationDistance(){
        return module().getDouble("max-teleportation-distance");
    }

    @EventHandler
    public void onEat(PlayerItemConsumeEvent e){
        if(e.getItem().getType() != Material.CHORUS_FRUIT){
            return;
        }
        if(module().getBoolean("prevent-eating")){
            e.setCancelled(true);
            return;
        }

        int hungerValue = module().getInt("hunger-value");
        double saturationValue = module().getDouble("saturation-value");

        int previousFoodLevel = e.getPlayer().getFoodLevel();
        float previousSaturation = e.getPlayer().getSaturation();

        // Run it on the next tick to reset things while not cancelling the chorus fruit eat event
        // This ensures the teleport event is fired and it counts towards statistics
        new BukkitRunnable() {
            @Override
            public void run(){
                int newFoodLevel = Math.min(hungerValue + previousFoodLevel, 20);
                float newSaturation = Math.min((float) (saturationValue + previousSaturation), newFoodLevel);

                e.getPlayer().setFoodLevel(newFoodLevel);
                e.getPlayer().setSaturation(newSaturation);

                debug(
                        "Food level changed from: " + previousFoodLevel + " to " + e.getPlayer().getFoodLevel(),
                        e.getPlayer()
                );
            }
        }.runTaskLater(plugin, 2);
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent e){
        if(!e.getCause().name().equals("CHORUS_FRUIT")){
            return;
        }

        double distance = getMaxTeleportationDistance();

        if(distance == 8){
            debug("Using vanilla teleport implementation!", e.getPlayer());
            return;
        }

        if(distance <= 0){
            debug("Chorus teleportation is not allowed", e.getPlayer());
            e.setCancelled(true);
            return;
        }

        // Not sure when this can occur, but it is marked as @Nullable
        if(e.getTo() == null){
            debug("Teleport target is null", e.getPlayer());
            return;
        }

        int maxheight = e.getTo().getWorld().getMaxHeight();
        e.setTo(e.getPlayer().getLocation().add(
                ThreadLocalRandom.current().nextDouble(-distance, distance),
                clamp(ThreadLocalRandom.current().nextDouble(-distance, distance), 0, maxheight - 1),
                ThreadLocalRandom.current().nextDouble(-distance, distance)
        ));
    }

    private double clamp(double x, double min, double max){
        return Math.max(Math.min(x, max), min);
    }
}
