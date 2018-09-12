package gvlfm78.plugin.OldCombatMechanics.utilities.reflection.sweep;

import gvlfm78.plugin.OldCombatMechanics.utilities.packet.Packet;
import gvlfm78.plugin.OldCombatMechanics.utilities.reflection.Reflector;
import gvlfm78.plugin.OldCombatMechanics.utilities.reflection.type.ClassType;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

class SweepPacketDetectorFrom1_13 extends AbstractSweepPacketDetector {

    private Field particleParamField;
    private Method particleParamNameMethod;

    SweepPacketDetectorFrom1_13(){
        for(Field field : PACKET_CLASS.getDeclaredFields()){
            if(field.getType().getSimpleName().equals("ParticleParam")){
                particleParamField = field;
                particleParamField.setAccessible(true);
            }
        }
        if(particleParamField == null){
            throwNewElementNotFoundException("Particle param field");
        }

        Class<?> particleParamClass = Reflector.getClass(ClassType.NMS, "ParticleParam");
        if(particleParamClass == null){
            throwNewElementNotFoundException("ParticleParam class");
        }

        for(Method method : particleParamClass.getMethods()){
            if(method.getReturnType() == String.class){
                particleParamNameMethod = method;
            }
        }
        if(particleParamNameMethod == null){
            throwNewElementNotFoundException("Particle param description method");
        }
    }

    @Override
    public boolean isSweepPacket(Packet packet){
        if(isWrongPacketType(packet)){
            return false;
        }

        Object nmsPacket = packet.getNMSPacket();

        Object particleParam = tryGetField(particleParamField, nmsPacket);

        String minecraftName = Reflector.invokeMethod(particleParamNameMethod, particleParam);

        return minecraftName.contains("sweep");
    }
}
