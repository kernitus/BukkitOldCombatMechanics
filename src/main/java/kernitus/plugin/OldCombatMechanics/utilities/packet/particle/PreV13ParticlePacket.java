/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
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
