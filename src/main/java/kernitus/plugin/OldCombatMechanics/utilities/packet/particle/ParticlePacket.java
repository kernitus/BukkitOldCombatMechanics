package kernitus.plugin.OldCombatMechanics.utilities.packet.particle;

import kernitus.plugin.OldCombatMechanics.utilities.packet.ImmutablePacket;
import kernitus.plugin.OldCombatMechanics.utilities.packet.PacketHelper;
import kernitus.plugin.OldCombatMechanics.utilities.packet.PacketHelper.PacketType;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector;

import java.util.Optional;

public abstract class ParticlePacket implements ImmutablePacket {
    protected static final Class<?> PACKET_CLASS = PacketHelper.getPacketClass(PacketType.PlayOut, "WorldParticles");

    private final Object nmsPacket;

    protected ParticlePacket(ImmutablePacket packet){
        this.nmsPacket = packet.getNmsPacket();
    }

    @Override
    public Object getNmsPacket(){
        return nmsPacket;
    }

    /**
     * @return the particle this packet is for
     */
    public abstract String getParticleName();

    /**
     * Creates a new {@link ParticlePacket} from a given {@link ImmutablePacket}, if it is of the correct type.
     *
     * @param packet the original packet
     * @return a particle packet, if the type was correct
     */
    public static Optional<ParticlePacket> from(ImmutablePacket packet){
        if(packet.getPacketClass() != PACKET_CLASS){
            return Optional.empty();
        }

        if(Reflector.versionIsNewerOrEqualAs(1, 13, 0)){
            return Optional.of(new V13ParticlePacket(packet));
        }
        return Optional.of(new PreV13ParticlePacket(packet));
    }
}
