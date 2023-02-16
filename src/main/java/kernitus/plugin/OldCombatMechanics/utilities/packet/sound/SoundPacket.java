/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities.packet.sound;

import kernitus.plugin.OldCombatMechanics.utilities.packet.ImmutablePacket;
import kernitus.plugin.OldCombatMechanics.utilities.packet.PacketHelper;
import kernitus.plugin.OldCombatMechanics.utilities.packet.PacketHelper.PacketType;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.type.ClassType;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;

public class SoundPacket implements ImmutablePacket {
    private static final Class<?> PACKET_CLASS = PacketHelper.getPacketClass(PacketType.PlayOut, "NamedSoundEffect");
    private static final PacketAccess PACKET_ACCESS = PacketAccess.getAccess();

    private final Object nmsPacket;

    private SoundPacket(ImmutablePacket packet) {
        this.nmsPacket = packet.getNmsPacket();
    }

    public String getSoundName() {
        return PACKET_ACCESS.getSoundName(getNmsPacket());
    }

    @Override
    public Object getNmsPacket() {
        return nmsPacket;
    }

    /**
     * Converts the given {@link ImmutablePacket} to a {@link SoundPacket}, if applicable.
     *
     * @param packet the packet to convert
     * @return the sound packet or empty if it had thr wrong type
     */
    public static Optional<SoundPacket> from(ImmutablePacket packet) {
        if (packet.getPacketClass() == PACKET_CLASS) {
            return Optional.of(new SoundPacket(packet));
        }
        return Optional.empty();
    }

    private interface PacketAccess {
        String getSoundName(Object packet);

        static PacketAccess getAccess() {
            try {
                return new PacketAccessPreV1_19_3();
            } catch (Throwable igored) {
                try {
                    return new PacketAccessV1_19_3();
                } catch (Throwable e) {
                    throw new RuntimeException("Sound packet init failed", e);
                }
            }
        }
    }

    private static class PacketAccessV1_19_3 implements PacketAccess {
        private static final Class<?> SOUND_EFFECT_CLASS = Reflector.getClass(ClassType.NMS, "sounds.SoundEffect");

        private final Field soundEffectHolderField;
        private final Method soundEffectMethod;
        private final Field minecraftKeyField;

        private PacketAccessV1_19_3() {
            soundEffectHolderField = Reflector.getFieldByType(PACKET_CLASS, "Holder");

            Class<?> holderClass = Reflector.getClass(ClassType.NMS, "core.Holder");
            soundEffectMethod = Reflector.getMethodByGenericReturnType(holderClass.getTypeParameters()[0], holderClass);
            minecraftKeyField = Reflector.getFieldByType(SOUND_EFFECT_CLASS, "MinecraftKey");
        }

        public String getSoundName(Object packet) {
            Object holder = Reflector.getUnchecked(() -> soundEffectHolderField.get(packet));
            Object soundEffect = Reflector.getUnchecked(() -> soundEffectMethod.invoke(holder));
            Object minecraftLey = Reflector.getUnchecked(() -> minecraftKeyField.get(soundEffect));

            return minecraftLey.toString();
        }
    }

    private static class PacketAccessPreV1_19_3 implements PacketAccess {
        private static final Class<?> SOUND_EFFECT_CLASS = Reflector.getClass(ClassType.NMS, "sounds.SoundEffect");

        private final Field soundEffectField;
        private final Field minecraftKeyField;

        private PacketAccessPreV1_19_3() {
            soundEffectField = Reflector.getFieldByType(PACKET_CLASS, "SoundEffect");
            minecraftKeyField = Reflector.getFieldByType(SOUND_EFFECT_CLASS, "MinecraftKey");
        }

        public String getSoundName(Object packet) {
            Object soundEffect = Reflector.getUnchecked(() -> soundEffectField.get(packet));
            Object minecraftLey = Reflector.getUnchecked(() -> minecraftKeyField.get(soundEffect));

            return minecraftLey.toString();
        }
    }
}
