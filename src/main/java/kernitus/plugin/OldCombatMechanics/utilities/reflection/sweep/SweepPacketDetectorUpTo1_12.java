package kernitus.plugin.OldCombatMechanics.utilities.reflection.sweep;

import kernitus.plugin.OldCombatMechanics.utilities.packet.ImmutablePacket;

import java.lang.reflect.Field;
import java.util.Locale;

class SweepPacketDetectorUpTo1_12 extends AbstractSweepPacketDetector {

    private Field enumParticleField;

    SweepPacketDetectorUpTo1_12(){
        for(Field field : PACKET_CLASS.getDeclaredFields()){
            if(field.getType().getSimpleName().equals("EnumParticle")){
                this.enumParticleField = field;
                this.enumParticleField.setAccessible(true);
            }
        }
        if(enumParticleField == null){
            throwNewElementNotFoundException("EnumParticle field");
        }
    }

    @Override
    public boolean isSweepPacket(ImmutablePacket packet){
        if(isWrongPacketType(packet)){
            return false;
        }

        Object nmsPacket = packet.getNmsPacket();
        Enum<?> enumParticle = (Enum<?>) tryGetField(enumParticleField, nmsPacket);

        return enumParticle.name().toUpperCase(Locale.ROOT).contains("SWEEP");
    }
}
