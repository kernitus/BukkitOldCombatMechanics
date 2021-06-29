package kernitus.plugin.OldCombatMechanics.utilities.packet.particle;

import kernitus.plugin.OldCombatMechanics.utilities.packet.ImmutablePacket;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector;

import java.lang.reflect.Field;

public class PreV13ParticlePacket extends ParticlePacket {

    protected PreV13ParticlePacket(ImmutablePacket packet){
        super(packet);
    }

    @Override
    public String getParticleName(){
        return PacketAccess.getParticleName(getNmsPacket());
    }

    private static class PacketAccess {

        private static final Field PARTICLE_PARAM_FIELD;

        static{
            PARTICLE_PARAM_FIELD = Reflector.getFieldByType(PACKET_CLASS, "EnumParticle");
        }

        public static String getParticleName(Object nmsPacket){
            return Reflector.getUnchecked(() -> PARTICLE_PARAM_FIELD.get(nmsPacket)).toString();
        }
    }

}
