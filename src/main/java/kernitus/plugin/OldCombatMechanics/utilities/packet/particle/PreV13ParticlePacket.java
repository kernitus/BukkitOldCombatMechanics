package kernitus.plugin.OldCombatMechanics.utilities.packet.particle;

import kernitus.plugin.OldCombatMechanics.utilities.packet.ImmutablePacket;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector;

import java.lang.reflect.Field;

public class PreV13ParticlePacket extends ParticlePacket {

    private static final PacketAccess PACKET_ACCESS = new PacketAccess();

    protected PreV13ParticlePacket(ImmutablePacket packet){
        super(packet);
    }

    @Override
    public String getParticleName(){
        return PACKET_ACCESS.getParticleName(getNmsPacket());
    }

    private static class PacketAccess {

        private static final Field PARTICLE_PARAM_FIELD;

        static{
            PARTICLE_PARAM_FIELD = Reflector.getFieldByType(PACKET_CLASS, "EnumParticle");
        }

        public String getParticleName(Object nmsPacket){
            return Reflector.getUnchecked(() -> (String) PARTICLE_PARAM_FIELD.get(nmsPacket));
        }
    }

}
