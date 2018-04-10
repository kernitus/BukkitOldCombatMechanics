package gvlfm78.plugin.OldCombatMechanics.module;

import gvlfm78.plugin.OldCombatMechanics.OCMMain;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.projectiles.ProjectileSource;

public class ModuleDisableProjectileRandomness extends Module {

    public ModuleDisableProjectileRandomness(OCMMain plugin){
        super(plugin, "disable-projectile-randomness");
    }

    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent e){
        Projectile projectile = e.getEntity(); //Getting the projectile
        ProjectileSource shooter = projectile.getShooter(); //Getting the shooter
        if(shooter instanceof Player){ //If the shooter was a player
            Player player = (Player) shooter;
            if(!isEnabled(player.getWorld())) return; //If this module is enabled in this world
            debug("Making projectile go straight", player);
            //Here we get a unit vector of the direction the player is looking in and multiply it by the projectile's vector's magnitude
            //We then assign this to the projectile as its new velocity
            projectile.setVelocity(player.getLocation().getDirection().normalize().multiply(projectile.getVelocity().length()));
        }
    }
}