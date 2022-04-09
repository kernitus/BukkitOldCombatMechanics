package kernitus.plugin.OldCombatMechanics;

import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.UnimplementedOperationException;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import kernitus.plugin.OldCombatMechanics.utilities.damage.ToolDamage;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
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

    @Override
    public void attack(@NotNull Entity target){
        if(!(target instanceof LivingEntity)) throw new UnimplementedOperationException();
        LivingEntity livingTarget = (LivingEntity) target;

        livingTarget.damage(getMeleeAttackDamage(), this);

        /* Player attack
    protected void d(DamageSource damagesource, float f) {
        if (!this.isInvulnerable(damagesource)) {
            if (!damagesource.ignoresArmor() && this.isBlocking() && f > 0.0F) {
                f = (1.0F + f) * 0.5F;
            }

            f = this.applyArmorModifier(damagesource, f);
            f = this.applyMagicModifier(damagesource, f);
            float f1 = f;

            f = Math.max(f - this.getAbsorptionHearts(), 0.0F);
            this.setAbsorptionHearts(this.getAbsorptionHearts() - (f1 - f));
            if (f != 0.0F) {
                this.applyExhaustion(damagesource.getExhaustionCost());
                float f2 = this.getHealth();

                this.setHealth(this.getHealth() - f);
                this.bs().a(damagesource, f2, f);
                if (f < 3.4028235E37F) {
                    this.a(StatisticList.x, Math.round(f * 10.0F));
                }

            }
        }
    }
         */
    }

    private float getMeleeAttackDamage(){
        Material weapon = entityEquipment.getItemInMainHand().getType();
        if(weapon == Material.AIR) weapon = entityEquipment.getItemInOffHand().getType();
        return ToolDamage.getDamage(weapon);
    }
}
