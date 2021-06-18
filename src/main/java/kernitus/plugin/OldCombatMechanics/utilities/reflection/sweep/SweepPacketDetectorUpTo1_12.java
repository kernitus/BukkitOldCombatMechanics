package kernitus.plugin.OldCombatMechanics.utilities.reflection.sweep;

import kernitus.plugin.OldCombatMechanics.utilities.Messenger;
import kernitus.plugin.OldCombatMechanics.utilities.packet.Packet;

import java.lang.reflect.Field;
import java.util.Locale;

class SweepPacketDetectorUpTo1_12 extends AbstractSweepPacketDetector {

    private Field enumParticleField;
    private boolean isSetup = false;

    SweepPacketDetectorUpTo1_12(){
        try{
            for(Field field : PACKET_CLASS.getDeclaredFields()){
                if(field.getType().getSimpleName().equals("EnumParticle")){
                    this.enumParticleField = field;
                    this.enumParticleField.setAccessible(true);
                }
            }
            if(enumParticleField == null){
                throwNewElementNotFoundException("EnumParticle field");
            }
            isSetup = true;
        } catch(Exception e){
            Messenger.warn(
                    e,
                    "Error setting up SweepPacketDetector. Please report it along with the following " +
                            "exception on github." +
                            "Sweep cancellation should still work, but particles might show up."
            );
        }
    }

    @Override
    public boolean isSweepPacket(Packet packet){
        if(!isSetup || isWrongPacketType(packet)){
            return false;
        }

        Object nmsPacket = packet.getNMSPacket();

        Enum<?> enumParticle = (Enum<?>) tryGetField(enumParticleField, nmsPacket);

        return enumParticle.name().toUpperCase(Locale.ROOT).contains("SWEEP");
    }
}
