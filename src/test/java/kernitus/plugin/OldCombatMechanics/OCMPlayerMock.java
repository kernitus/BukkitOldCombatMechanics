package kernitus.plugin.OldCombatMechanics;

import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import org.bukkit.inventory.EntityEquipment;

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
}
