package gvlfm78.plugin.OldCombatMechanics.module;

import com.google.common.base.Functions;
import com.google.common.collect.ImmutableMap;
import gvlfm78.plugin.OldCombatMechanics.OCMMain;
import gvlfm78.plugin.OldCombatMechanics.utilities.reflection.MemoizingFeatureBranch;
import gvlfm78.plugin.OldCombatMechanics.utilities.reflection.Reflector;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.util.Vector;

import java.util.EnumMap;

public class ModuleFishingKnockback extends Module {

    private MemoizingFeatureBranch<PlayerFishEvent, Entity> hookEntityFeature;

    public ModuleFishingKnockback(OCMMain plugin){
        super(plugin, "old-fishing-knockback");

        //noinspection Convert2MethodRef as the Method reference would error at initialization, not just when invoked
        hookEntityFeature = MemoizingFeatureBranch.onException(
                playerFishEvent -> playerFishEvent.getHook(),
                playerFishEvent -> playerFishEvent.getHook(),
                // fall back to reflection on 1.12 and suck up some performance penalty
                Reflector.memoizeMethodAndInvoke(PlayerFishEvent.class, "getHook")
        );
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRodLand(ProjectileHitEvent e){

        Entity hookEntity = e.getEntity();
        World world = hookEntity.getWorld();

        if(!isEnabled(world)) return;

        if(e.getEntityType() != EntityType.FISHING_HOOK)
            return;


        Entity hitEntity;

        try{
            hitEntity = e.getHitEntity();
        } catch(NoSuchMethodError e1){ //For older version that don't have such method
            hitEntity = world.getNearbyEntities(hookEntity.getLocation(), 0.25, 0.25, 0.25).stream()
                    .filter(entity -> entity instanceof Player)
                    .findFirst()
                    .orElse(null);
        }

        if(hitEntity == null) return;
        if(!(hitEntity instanceof Player)) return;

        FishHook hook = (FishHook) hookEntity;
        Player rodder = (Player) hook.getShooter();
        Player player = (Player) hitEntity;

        debug("You were hit by a fishing rod!", player);

        if(player.getUniqueId().equals(rodder.getUniqueId()))
            return;

        if(player.getGameMode() == GameMode.CREATIVE) return;

        double damage = module().getDouble("damage");
        if(damage < 0) damage = 0.2;

        EntityDamageEvent event = makeEvent(rodder, player, damage);
        Bukkit.getPluginManager().callEvent(event);

        if(module().getBoolean("checkCancelled") && event.isCancelled()){

            //This is to check what plugins are listening to the event
            if(plugin.getConfig().getBoolean("debug.enabled")){
                debug("You can't do that here!", rodder);
                HandlerList hl = event.getHandlers();

                for(RegisteredListener rl : hl.getRegisteredListeners())
                    debug("Plugin Listening: " + rl.getPlugin().getName(), rodder);
            }

            return;
        }

        player.damage(damage);

        player.setVelocity(calculateKnockbackVelocity(player.getVelocity(), player.getLocation(), hook.getLocation()));
    }

    private Vector calculateKnockbackVelocity(Vector currentVelocity, Location player, Location entity){
        double xDistance = entity.getX() - player.getX();
        double zDistance = entity.getZ() - player.getZ();

        // ensure distance is not zero and randomise in that case (I guess?)
        while(xDistance * xDistance + zDistance * zDistance < 0.0001){
            xDistance = (Math.random() - Math.random()) * 0.01D;
            zDistance = (Math.random() - Math.random()) * 0.01D;
        }

        double distance = Math.sqrt(xDistance * xDistance + zDistance * zDistance);

        double y = currentVelocity.getY() / 2;
        double x = currentVelocity.getX() / 2;
        double z = currentVelocity.getZ() / 2;

        // Normalize distance to have similar knockback, no matter the distance
        x -= xDistance / distance * 0.4;

        // slow the fall or throw upwards
        y += 0.4;

        // Normalize distance to have similar knockback, no matter the distance
        z -= zDistance / distance * 0.4;

        // do not shoot too high up
        if(y >= 0.4){
            y = 0.4;
        }

        return new Vector(x, y, z);
    }

    /**
     * This is to cancel dragging the player closer when you reel in
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    private void onReelIn(PlayerFishEvent e){
        if(!isSettingEnabled("cancelDraggingIn") || e.getState() != PlayerFishEvent.State.CAUGHT_ENTITY) return;
        hookEntityFeature.apply(e).remove(); //Nuke the bobber and don't do anything else
        e.setCancelled(true);
    }

    @SuppressWarnings({"rawtypes", "unchecked", "deprecation"})
    private EntityDamageEvent makeEvent(Player rodder, Player player, double damage){
        if(module().getBoolean("useEntityDamageEvent"))
            return new EntityDamageEvent(player,
                    EntityDamageEvent.DamageCause.PROJECTILE,
                    new EnumMap(ImmutableMap.of(EntityDamageEvent.DamageModifier.BASE, damage)),
                    new EnumMap(ImmutableMap.of(EntityDamageEvent.DamageModifier.BASE, Functions.constant(damage))));
        else
            return new EntityDamageByEntityEvent(rodder, player,
                    EntityDamageEvent.DamageCause.PROJECTILE,
                    new EnumMap(ImmutableMap.of(EntityDamageEvent.DamageModifier.BASE, damage)),
                    new EnumMap(ImmutableMap.of(EntityDamageEvent.DamageModifier.BASE, Functions.constant(damage))));
    }
}