package kernitus.plugin.OldCombatMechanics;

import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EntityEquipment;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class OCMPlayerMock extends PlayerMock {

    private EntityEquipment entityEquipment;
    private int noDamageTicks, maximumNoDamageTicks;
    private double lastDamage;

    public OCMPlayerMock(ServerMock server, String name) {
        super(server, name);
    }

    public OCMPlayerMock(ServerMock server, String name, UUID uuid) {
        super(server, name, uuid);
    }

    public EntityEquipment getEquipment() {
        return entityEquipment;
    }

    public void setEquipment(EntityEquipment entityEquipment){
       this.entityEquipment = entityEquipment;
    }

    @Override
    public int getNoDamageTicks() {
        return noDamageTicks;
    }

    @Override
    public void setNoDamageTicks(int noDamageTicks) {
        this.noDamageTicks = noDamageTicks;
    }

    @Override
    public int getMaximumNoDamageTicks() {
        return maximumNoDamageTicks;
    }

    @Override
    public void setMaximumNoDamageTicks(int maximumNoDamageTicks) {
        this.maximumNoDamageTicks = maximumNoDamageTicks;
    }

    @Override
    public double getLastDamage() {
        return lastDamage;
    }

    @Override
    public void setLastDamage(double lastDamage) {
        this.lastDamage = lastDamage;
    }

    public EntityDamageByEntityEvent attackEntity(@NotNull Entity target, double damage){
        EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(this, target,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK, damage);
        Bukkit.getPluginManager().callEvent(event);

        return event;
    }
}
