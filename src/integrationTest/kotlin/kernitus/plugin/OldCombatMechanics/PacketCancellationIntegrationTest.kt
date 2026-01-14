/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.event.PacketSendEvent
import com.github.retrooper.packetevents.manager.server.ServerVersion
import com.github.retrooper.packetevents.netty.buffer.ByteBufHelper
import com.github.retrooper.packetevents.protocol.ConnectionState
import com.github.retrooper.packetevents.protocol.PacketSide
import com.github.retrooper.packetevents.protocol.packettype.PacketType
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon
import com.github.retrooper.packetevents.protocol.particle.Particle
import com.github.retrooper.packetevents.protocol.particle.type.ParticleTypes
import com.github.retrooper.packetevents.protocol.sound.Sound
import com.github.retrooper.packetevents.protocol.sound.SoundCategory
import com.github.retrooper.packetevents.protocol.sound.Sounds
import com.github.retrooper.packetevents.protocol.player.User
import com.github.retrooper.packetevents.protocol.player.UserProfile
import com.github.retrooper.packetevents.util.Vector3d
import com.github.retrooper.packetevents.util.Vector3f
import com.github.retrooper.packetevents.util.Vector3i
import com.github.retrooper.packetevents.wrapper.PacketWrapper
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerParticle
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSoundEffect
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kernitus.plugin.OldCombatMechanics.module.ModuleAttackSounds
import kernitus.plugin.OldCombatMechanics.module.ModuleSwordSweepParticles
import kernitus.plugin.OldCombatMechanics.utilities.Config
import kotlinx.coroutines.delay
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.Callable

@OptIn(ExperimentalKotest::class)
class PacketCancellationIntegrationTest : FunSpec({
    val testPlugin = JavaPlugin.getPlugin(OCMTestMain::class.java)
    val ocm = Bukkit.getPluginManager().getPlugin("OldCombatMechanics") as OCMMain

    fun <T> runSync(action: () -> T): T {
        return if (Bukkit.isPrimaryThread()) {
            action()
        } else {
            Bukkit.getScheduler().callSyncMethod(testPlugin, Callable { action() }).get()
        }
    }

    extensions(MainThreadDispatcherExtension(testPlugin))

    beforeSpec {
        require(PacketEvents.getAPI().isInitialized) { "PacketEvents not initialised for integration tests." }
    }

    suspend fun withModuleState(
        moduleName: String,
        enabled: Boolean,
        preReload: (() -> Unit)? = null,
        postRestore: (() -> Unit)? = null,
        block: suspend () -> Unit
    ) {
        val disabledOriginal = ocm.config.getStringList("disabled_modules")
        val alwaysOriginal = ocm.config.getStringList("always_enabled_modules")
        val modesetsSection = ocm.config.getConfigurationSection("modesets")
            ?: error("Missing modesets section in config")
        val modesetsOriginal = modesetsSection.getKeys(false).associateWith {
            ocm.config.getStringList("modesets.$it")
        }

        fun List<String>.withoutModule(): MutableList<String> =
            filterNot { it.equals(moduleName, true) }.toMutableList()

        val disabledUpdated = disabledOriginal.withoutModule()
        val alwaysUpdated = alwaysOriginal.withoutModule()
        if (enabled) {
            alwaysUpdated.add(moduleName)
        } else {
            disabledUpdated.add(moduleName)
        }

        ocm.config.set("disabled_modules", disabledUpdated)
        ocm.config.set("always_enabled_modules", alwaysUpdated)
        modesetsOriginal.keys.forEach { key ->
            val filtered = modesetsOriginal.getValue(key).withoutModule()
            ocm.config.set("modesets.$key", filtered)
        }
        preReload?.invoke()
        ocm.saveConfig()
        Config.reload()
        delay(2 * 50L)

        try {
            block()
        } finally {
            ocm.config.set("disabled_modules", disabledOriginal)
            ocm.config.set("always_enabled_modules", alwaysOriginal)
            modesetsOriginal.forEach { (key, value) ->
                ocm.config.set("modesets.$key", value)
            }
            postRestore?.invoke()
            ocm.saveConfig()
            Config.reload()
            delay(2 * 50L)
        }
    }

    fun spawnFakePlayer(): Pair<FakePlayer, Player> {
        val world = Bukkit.getWorld("world") ?: error("world not loaded")
        val location = Location(world, 0.0, 120.0, 0.0, 0f, 0f)
        val fake = FakePlayer(testPlugin)
        fake.spawn(location)
        val player = Bukkit.getPlayer(fake.uuid) ?: error("Player not found after spawn")
        return fake to player
    }

    suspend fun removeFakePlayer(fake: FakePlayer) {
        runSync { fake.removePlayer() }
        delay(2 * 50L)
    }

    suspend fun withBlockedSounds(module: ModuleAttackSounds, blocked: Set<String>, block: suspend () -> Unit) {
        val field = module.javaClass.getDeclaredField("blockedSounds")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val current = field.get(module) as MutableSet<String>
        val snapshot = current.toSet()
        current.clear()
        current.addAll(blocked)
        try {
            block()
        } finally {
            current.clear()
            current.addAll(snapshot)
        }
    }

    fun createPacketSendEvent(
        packetId: Int,
        packetType: PacketTypeCommon,
        serverVersion: ServerVersion,
        channel: Any,
        user: User,
        player: Player,
        buffer: Any
    ): PacketSendEvent {
        val constructor = PacketSendEvent::class.java.getDeclaredConstructor(
            Int::class.javaPrimitiveType,
            PacketTypeCommon::class.java,
            ServerVersion::class.java,
            Any::class.java,
            User::class.java,
            Any::class.java,
            Any::class.java
        )
        constructor.isAccessible = true
        return constructor.newInstance(
            packetId,
            packetType,
            serverVersion,
            channel,
            user,
            player,
            buffer
        ) as PacketSendEvent
    }

    fun runPacketThroughPacketEvents(player: Player, wrapper: PacketWrapper<*>): Boolean {
        val api = PacketEvents.getAPI()
        val channel = api.playerManager.getChannel(player) ?: error("Missing channel for ${player.name}")
        val serverVersion = api.serverManager.version
        val user = User(
            channel,
            ConnectionState.PLAY,
            serverVersion.toClientVersion(),
            UserProfile(player.uniqueId, player.name)
        )
        user.setEncoderState(ConnectionState.PLAY)
        user.setDecoderState(ConnectionState.PLAY)
        user.setEntityId(player.entityId)

        val buffers = api.protocolManager.transformWrappers(wrapper, channel, true)
        val buffer = buffers.firstOrNull() ?: error("No buffer produced for ${wrapper.javaClass.simpleName}")
        val packetId = ByteBufHelper.readVarInt(buffer)
        val packetType = PacketType.getById(PacketSide.SERVER, ConnectionState.PLAY, user.clientVersion, packetId)
            ?: error("No packet type for id $packetId in ${user.clientVersion}")
        val event = createPacketSendEvent(packetId, packetType, serverVersion, channel, user, player, buffer)

        api.eventManager.callEvent(event)

        return event.isCancelled
    }

    fun soundName(sound: Sound): String = sound.soundId.toString()

    test("sweep particles are cancelled when module enabled") {
        withModuleState("disable-sword-sweep-particles", enabled = true) {
            val (fake, player) = runSync { spawnFakePlayer() }
            try {
                val particle = Particle(ParticleTypes.SWEEP_ATTACK)
                val position = Vector3d(player.location.x, player.location.y, player.location.z)
                val offset = Vector3f(0f, 0f, 0f)
                val wrapper = WrapperPlayServerParticle(particle, false, position, offset, 0.0f, 1)

                val cancelled = runPacketThroughPacketEvents(player, wrapper)

                cancelled shouldBe true
            } finally {
                removeFakePlayer(fake)
            }
        }
    }

    test("sweep particles are not cancelled when module disabled") {
        withModuleState("disable-sword-sweep-particles", enabled = false) {
            val (fake, player) = runSync { spawnFakePlayer() }
            try {
                val particle = Particle(ParticleTypes.FLAME)
                val position = Vector3d(player.location.x, player.location.y, player.location.z)
                val offset = Vector3f(0f, 0f, 0f)
                val wrapper = WrapperPlayServerParticle(particle, false, position, offset, 0.0f, 1)

                val cancelled = runPacketThroughPacketEvents(player, wrapper)

                cancelled shouldBe false
            } finally {
                removeFakePlayer(fake)
            }
        }
    }

    test("blocked attack sounds are cancelled when module enabled") {
        withModuleState("disable-attack-sounds", enabled = true) {
            val (fake, player) = runSync { spawnFakePlayer() }
            try {
                val module = ModuleLoader.getModules().filterIsInstance<ModuleAttackSounds>().firstOrNull()
                    ?: error("ModuleAttackSounds not registered")
                val sound = Sounds.ENTITY_PLAYER_ATTACK_STRONG
                val position = Vector3i(player.location.blockX, player.location.blockY, player.location.blockZ)
                val wrapper = WrapperPlayServerSoundEffect(sound, SoundCategory.PLAYER, position, 0.37f, 0.71f)

                withBlockedSounds(module, setOf(soundName(sound))) {
                    val cancelled = runPacketThroughPacketEvents(player, wrapper)
                    cancelled shouldBe true
                }
            } finally {
                removeFakePlayer(fake)
            }
        }
    }

    test("non-blocked attack sounds are not cancelled when module enabled") {
        withModuleState("disable-attack-sounds", enabled = true) {
            val (fake, player) = runSync { spawnFakePlayer() }
            try {
                val module = ModuleLoader.getModules().filterIsInstance<ModuleAttackSounds>().firstOrNull()
                    ?: error("ModuleAttackSounds not registered")
                val blockedSound = Sounds.ENTITY_PLAYER_ATTACK_STRONG
                val sound = Sounds.ENTITY_PLAYER_LEVELUP
                val position = Vector3i(player.location.blockX, player.location.blockY, player.location.blockZ)
                val wrapper = WrapperPlayServerSoundEffect(sound, SoundCategory.PLAYER, position, 0.43f, 0.21f)

                withBlockedSounds(module, setOf(soundName(blockedSound))) {
                    val cancelled = runPacketThroughPacketEvents(player, wrapper)
                    cancelled shouldBe false
                }
            } finally {
                removeFakePlayer(fake)
            }
        }
    }
})
