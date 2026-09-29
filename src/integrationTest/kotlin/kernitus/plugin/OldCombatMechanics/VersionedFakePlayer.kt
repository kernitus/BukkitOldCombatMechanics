/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics

import com.mojang.authlib.GameProfile
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.ChannelPromise
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.util.ReferenceCountUtil
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.entity.Player
import java.net.InetSocketAddress
import java.util.UUID

/** Synchronous login bridge for versioned Paper servers exposing a native join finaliser. */
internal class VersionedFakePlayer(
    private val uuid: UUID,
    private val name: String,
) {
    private val server =
        Bukkit
            .getServer()
            .javaClass
            .getMethod("getServer")
            .invoke(Bukkit.getServer())
    private val packageName = "net.minecraft.server.${Bukkit.getServer().javaClass.`package`.name.substringAfterLast(
        '.',
    )}"

    private fun nms(name: String): Class<*> = Class.forName("$packageName.$name", true, server.javaClass.classLoader)

    lateinit var entityPlayer: Any
        private set
    lateinit var bukkitPlayer: Player
        private set
    private var channel: EmbeddedChannel? = null
    private var networkManager: Any? = null
    private var connectedChannels: MutableCollection<Any>? = null
    private val playerList = server.javaClass.getMethod("getPlayerList").invoke(server)

    fun spawn(location: Location) {
        val world = requireNotNull(location.world)
        val worldHandle = world.javaClass.getMethod("getHandle").invoke(world)
        val managerClass = nms("PlayerInteractManager")
        val manager = managerClass.getConstructor(nms("WorldServer")).newInstance(worldHandle)
        entityPlayer =
            nms("EntityPlayer")
                .getConstructor(
                    nms("MinecraftServer"),
                    nms("WorldServer"),
                    GameProfile::class.java,
                    managerClass,
                ).newInstance(server, worldHandle, GameProfile(uuid, name), manager)
        bukkitPlayer = entityPlayer.javaClass.getMethod("getBukkitEntity").invoke(entityPlayer) as Player

        try {
            val directionClass = nms("EnumProtocolDirection")
            val direction = directionClass.enumConstants.single { (it as Enum<*>).name == "SERVERBOUND" }
            val network = nms("NetworkManager").getConstructor(directionClass).newInstance(direction)
            val keepAliveOut = nms("PacketPlayOutKeepAlive")
            val keepAliveIn = nms("PacketPlayInKeepAlive")
            val outgoingId = keepAliveOut.declaredFields.single { it.type == Long::class.javaPrimitiveType }
            val incomingId = keepAliveIn.declaredFields.single { it.type == Long::class.javaPrimitiveType }
            outgoingId.isAccessible = true
            incomingId.isAccessible = true
            val receiveKeepAlive = nms("PlayerConnection").getMethod("a", keepAliveIn)
            val embedded =
                EmbeddedChannel(
                    object : ChannelOutboundHandlerAdapter() {
                        override fun write(
                            context: ChannelHandlerContext,
                            message: Any,
                            promise: ChannelPromise,
                        ) {
                            if (keepAliveOut.isInstance(message)) {
                                val response = keepAliveIn.getConstructor().newInstance()
                                incomingId.setLong(response, outgoingId.getLong(message))
                                receiveKeepAlive.invoke(getConnection(entityPlayer), response)
                            }
                            ReferenceCountUtil.release(message)
                            promise.setSuccess()
                        }
                    },
                )
            channel = embedded
            Reflector.getField(network.javaClass, "channel").set(network, embedded)
            Reflector.getField(network.javaClass, "socketAddress").set(network, InetSocketAddress("127.0.0.1", 9999))
            val protocolClass = nms("EnumProtocol")
            val play = protocolClass.enumConstants.single { (it as Enum<*>).name == "PLAY" }
            network.javaClass.getMethod("setProtocol", protocolClass).invoke(network, play)

            // Load the spawn chunk synchronously and use the native join finaliser directly.
            // The normal login entry point queues work for a real network connection to finish later.
            world.getChunkAt(location)
            val connectionClass = nms("PlayerConnection")
            val connection =
                connectionClass
                    .getConstructor(
                        nms("MinecraftServer"),
                        network.javaClass,
                        entityPlayer.javaClass,
                    ).newInstance(server, network, entityPlayer)
            Reflector.getField(entityPlayer.javaClass, "networkManager").set(entityPlayer, network)
            entityPlayer.javaClass
                .getMethod(
                    "setPositionRotation",
                    Double::class.javaPrimitiveType,
                    Double::class.javaPrimitiveType,
                    Double::class.javaPrimitiveType,
                    Float::class.javaPrimitiveType,
                    Float::class.javaPrimitiveType,
                ).invoke(entityPlayer, location.x, location.y, location.z, location.yaw, location.pitch)
            val finishJoin =
                nms("PlayerList").getDeclaredMethod(
                    "postChunkLoadJoin",
                    entityPlayer.javaClass,
                    worldHandle.javaClass,
                    network.javaClass,
                    connectionClass,
                    nms("NBTTagCompound"),
                    String::class.java,
                    String::class.java,
                )
            finishJoin.isAccessible = true
            finishJoin.invoke(playerList, entityPlayer, worldHandle, network, connection, null, "127.0.0.1", name)
            check(Bukkit.getPlayer(uuid) != null) { "Versioned fake-player login did not register the player" }
            bukkitPlayer.gameMode = GameMode.SURVIVAL
            check(bukkitPlayer.teleport(location)) { "Could not position versioned fake player" }
            // Use the server's network tick phase so scheduled exhaustion corrections run first.
            val serverConnection = server.javaClass.getMethod("getServerConnection").invoke(server)

            @Suppress("UNCHECKED_CAST")
            val connections =
                Reflector
                    .getField(serverConnection.javaClass, "connectedChannels")
                    .get(serverConnection) as MutableCollection<Any>
            synchronized(connections) { connections.add(network) }
            networkManager = network
            connectedChannels = connections
        } catch (failure: Throwable) {
            runCatching { removePlayer() }.exceptionOrNull()?.let { failure.addSuppressed(it) }
            throw failure
        }
    }

    fun getConnection(player: Any): Any = Reflector.getField(player.javaClass, "playerConnection").get(player)

    fun removePlayer() {
        connectedChannels?.let { connections ->
            synchronized(connections) { connections.remove(networkManager) }
        }
        connectedChannels = null
        networkManager = null
        try {
            if (::entityPlayer.isInitialized && Bukkit.getPlayer(uuid) != null) {
                playerList.javaClass.getMethod("disconnect", entityPlayer.javaClass).invoke(playerList, entityPlayer)
            }
        } finally {
            channel?.finishAndReleaseAll()
            channel = null
        }
    }

    companion object {
        fun isAvailable(): Boolean {
            val version =
                Bukkit
                    .getServer()
                    .javaClass.`package`.name
                    .substringAfterLast('.')
            return try {
                val prefix = "net.minecraft.server.$version"
                val playerList = Class.forName("$prefix.PlayerList")
                playerList.getDeclaredMethod(
                    "postChunkLoadJoin",
                    Class.forName("$prefix.EntityPlayer"),
                    Class.forName("$prefix.WorldServer"),
                    Class.forName("$prefix.NetworkManager"),
                    Class.forName("$prefix.PlayerConnection"),
                    Class.forName("$prefix.NBTTagCompound"),
                    String::class.java,
                    String::class.java,
                )
                true
            } catch (_: ReflectiveOperationException) {
                false
            }
        }
    }
}
