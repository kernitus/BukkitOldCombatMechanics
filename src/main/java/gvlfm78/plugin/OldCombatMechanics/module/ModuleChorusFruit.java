package gvlfm78.plugin.OldCombatMechanics.module;

import gvlfm78.plugin.OldCombatMechanics.OCMMain;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Optional;
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

        if(getMaxTeleportationDistance() == 8){
            debug("Using vanilla teleport implementation!", e.getPlayer());
            return;
        }

        e.setCancelled(true);

        if(getMaxTeleportationDistance() <= 0){
            debug("Chorus teleportation is not allowed", e.getPlayer());
            return;
        }

        // Not sure when this can occur, but it is marked as @Nullable
        if(e.getTo() == null){
            debug("Teleport target is null", e.getPlayer());
            return;
        }

        for(int i = 0; i < module().getInt("max-teleport-tries"); i++){
            Optional<Location> freeSpot = findFreeSpotWithinDistance(e.getFrom(), getMaxTeleportationDistance());
            if(freeSpot.isPresent()){
                Bukkit.getScheduler().runTaskLater(plugin, () -> teleportPlayer(freeSpot.get(), e.getPlayer()), 1);
                return;
            }
        }
    }

    private void teleportPlayer(Location target, Player player){
        Location from = player.getLocation();
        player.teleport(target.clone().setDirection(player.getLocation().getDirection()));

        World world = target.getWorld();

        world.playSound(target, Sound.ITEM_CHORUS_FRUIT_TELEPORT, 1, 1);
        player.playSound(target, Sound.ITEM_CHORUS_FRUIT_TELEPORT, 1, 1);

        ThreadLocalRandom random = ThreadLocalRandom.current();
        double width = 0.6;
        double height = 1.8;

        // Litter the way with portal dots
        for(int i = 0; i < 128; ++i){
            // Current step on the way from player to target
            double step = i / 127.0;

            // Particle offsets (just make them a bit random)
            float offsetX = (random.nextFloat() - 0.5f) * 0.2f;
            float offsetY = (random.nextFloat() - 0.5f) * 0.2f;
            float offsetZ = (random.nextFloat() - 0.5f) * 0.2f;

            // lerp x, y and z and add some random offset to prevent a uniform line
            double x = lerp(from.getX(), target.getX(), step) + (random.nextDouble() - 0.5) * width * 2.0;
            double y = lerp(from.getY(), target.getY(), step) + random.nextDouble() * height;
            double z = lerp(from.getZ(), target.getZ(), step) + (random.nextDouble() - 0.5) * width * 2.0;

            world.spawnParticle(Particle.PORTAL, x, y, z, 1, offsetX, offsetY, offsetZ);
        }
    }

    private double lerp(double start, double end, double step){
        return start + (end - start) * step;
    }

    private Optional<Location> findFreeSpotWithinDistance(Location center, double maxAxisDistance){
        Vector randomVector = new Vector(
                ThreadLocalRandom.current().nextDouble(-maxAxisDistance, maxAxisDistance),
                ThreadLocalRandom.current().nextDouble(-maxAxisDistance, maxAxisDistance),
                ThreadLocalRandom.current().nextDouble(-maxAxisDistance, maxAxisDistance)
        );

        Location randomPoint = center.getBlock().getLocation().add(randomVector);

        while(!randomPoint.getBlock().getType().isSolid()){
            randomPoint.subtract(0, 1, 0);
            // At the bottom of thw word, we could go on forever otherwise
            if(randomPoint.getY() <= 0){
                return Optional.empty();
            }
        }

        // Now we are at the top block, so add one to get the air space
        randomPoint.add(0, 1, 0);

        // We were in a block
        if(randomPoint.getBlock().getType().isSolid()){
            return Optional.empty();
        }

        // We want to be able to stay there
        if(!randomPoint.clone().add(0, 1, 0).getBlock().isPassable()){
            return Optional.empty();
        }

        if(randomPoint.getBlock().getType() == Material.WATER || randomPoint.getBlock().getType() == Material.LAVA){
            return Optional.empty();
        }

        return Optional.of(randomPoint);
    }
}
