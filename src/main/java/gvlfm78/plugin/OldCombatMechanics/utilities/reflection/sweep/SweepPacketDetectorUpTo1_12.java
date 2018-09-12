package gvlfm78.plugin.OldCombatMechanics.utilities.reflection.sweep;

import gvlfm78.plugin.OldCombatMechanics.utilities.packet.Packet;

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
    public boolean isSweepPacket(Packet packet){
        if(isWrongPacketType(packet)){
            return false;
        }

        Object nmsPacket = packet.getNMSPacket();

        Enum<?> enumParticle = (Enum<?>) tryGetField(enumParticleField, nmsPacket);

        return enumParticle.name().toUpperCase(Locale.ROOT).contains("SWEEP");
    }
}
