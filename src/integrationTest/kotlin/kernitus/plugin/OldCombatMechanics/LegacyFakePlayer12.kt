/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics

import com.mojang.authlib.GameProfile
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.embedded.EmbeddedChannel
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.player.AsyncPlayerPreLoginEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerPreLoginEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.net.InetAddress
import java.util.UUID

internal class LegacyFakePlayer12(
    private val plugin: JavaPlugin,
    val uuid: UUID,
    val name: String
) {
    private val cbVersion: String = Bukkit.getServer().javaClass.`package`.name.substringAfterLast('.')
    var entityPlayer: Any? = null
        private set
    var bukkitPlayer: Player? = null
        private set

    private fun nmsClass(simpleName: String): Class<*> =
        Class.forName("net.minecraft.server.$cbVersion.$simpleName")

    private fun craftClass(simpleName: String): Class<*> =
        Class.forName("org.bukkit.craftbukkit.$cbVersion.$simpleName")

    fun spawn(location: Location) {
        plugin.logger.info("Spawn: Starting (legacy $cbVersion)")
        val world = location.world ?: throw IllegalArgumentException("Location has no world!")

        val craftWorld = craftClass("CraftWorld").cast(world)
        val worldServer = craftWorld.javaClass.getMethod("getHandle").invoke(craftWorld)

        val craftServer = craftClass("CraftServer").cast(Bukkit.getServer())
        val minecraftServer = craftServer.javaClass.getMethod("getServer").invoke(craftServer)

        val entityPlayer = createEntityPlayer(minecraftServer, worldServer)
        this.entityPlayer = entityPlayer
        val bukkitPlayer = entityPlayer.javaClass.getMethod("getBukkitEntity").invoke(entityPlayer) as Player
        this.bukkitPlayer = bukkitPlayer

        firePreLoginEvents()

        val playerList = minecraftServer.javaClass.getMethod("getPlayerList").invoke(minecraftServer)
        invokeMethodIfExists(playerList, "a", entityPlayer)

        setPositionRotation(entityPlayer, location)
        setDataWatcherFlags(entityPlayer)

        spawnInWorld(entityPlayer, worldServer)
        setGameMode(entityPlayer, worldServer)
        setupConnection(entityPlayer, minecraftServer)

        addToPlayerChunkMap(worldServer, entityPlayer)
        addToPlayerList(playerList, entityPlayer)
        updatePlayerListMaps(playerList, entityPlayer)

        val joinMessage = "§e$name joined the game"
        val joinEvent = PlayerJoinEvent(bukkitPlayer, joinMessage)
        Bukkit.getPluginManager().callEvent(joinEvent)
        broadcastMessage(joinEvent.joinMessage)

        sendSpawnPackets(entityPlayer)
        addEntityToWorld(worldServer, entityPlayer)

        Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, Runnable {
            runCatching { invokeMethod(entityPlayer, "playerTick") }
        }, 1L, 1L)

        plugin.logger.info("Spawn: completed successfully (legacy)")
    }

    fun removePlayer() {
        val entityPlayer = entityPlayer ?: return
        val bukkitPlayer = bukkitPlayer ?: return

        val craftServer = craftClass("CraftServer").cast(Bukkit.getServer())
        val minecraftServer = craftServer.javaClass.getMethod("getServer").invoke(craftServer)
        val playerList = minecraftServer.javaClass.getMethod("getPlayerList").invoke(minecraftServer)

        val quitMessage = "§e$name left the game"
        val quitEvent = PlayerQuitEvent(bukkitPlayer, quitMessage)
        Bukkit.getPluginManager().callEvent(quitEvent)

        val worldServer = getWorldServer(entityPlayer)
        val playerChunkMap = getPlayerChunkMap(worldServer)
        invokeMethodIfExists(playerChunkMap, "removePlayer", entityPlayer)
        invokeMethodIfExists(worldServer, "removeEntity", entityPlayer)

        bukkitPlayer.kickPlayer(quitMessage)

        removeFromPlayerList(playerList, entityPlayer)
        removeFromPlayerMaps(playerList, entityPlayer)

        sendRemovePackets(entityPlayer)

        invokeMethodIfExists(playerList, "savePlayerFile", entityPlayer)
    }

    fun startUsingOffhand() {
        val entityPlayer = entityPlayer ?: return
        val enumHandClass = nmsClass("EnumHand")
        val offHand = enumValue(enumHandClass, "OFF_HAND")
        val candidateNames = arrayOf("c", "a", "startUsingItem")
        for (name in candidateNames) {
            val method = findMethod(entityPlayer.javaClass, name, arrayOf(offHand), ignoreMissing = true)
            if (method != null) {
                method.invoke(entityPlayer, offHand)
                return
            }
        }
        val fallback = entityPlayer.javaClass.methods.firstOrNull { method ->
            method.parameterTypes.size == 1 && method.parameterTypes[0] == enumHandClass
        }
        fallback?.invoke(entityPlayer, offHand)
    }

    fun getConnection(serverPlayer: Any): Any {
        val connectionField = findField(serverPlayer.javaClass, "playerConnection")
            ?: throw NoSuchFieldException("playerConnection not found on ${serverPlayer.javaClass.name}")
        return connectionField.get(serverPlayer)
    }

    private fun createEntityPlayer(minecraftServer: Any, worldServer: Any): Any {
        val entityPlayerClass = nmsClass("EntityPlayer")
        val playerInteractManagerClass = nmsClass("PlayerInteractManager")
        val pimCtor = playerInteractManagerClass.constructors.firstOrNull { ctor ->
            ctor.parameterTypes.size == 1 && ctor.parameterTypes[0].isAssignableFrom(worldServer.javaClass)
        } ?: playerInteractManagerClass.constructors.first()
        val playerInteractManager = pimCtor.newInstance(worldServer)
        val gameProfile = GameProfile(uuid, name)
        val ctor = entityPlayerClass.constructors.firstOrNull { ctor ->
            val params = ctor.parameterTypes
            params.size == 4 &&
                params[0].isAssignableFrom(minecraftServer.javaClass) &&
                params[1].isAssignableFrom(worldServer.javaClass) &&
                params[2] == GameProfile::class.java &&
                params[3].isAssignableFrom(playerInteractManagerClass)
        } ?: entityPlayerClass.constructors.first()
        return ctor.newInstance(minecraftServer, worldServer, gameProfile, playerInteractManager)
    }

    private fun firePreLoginEvents() {
        try {
            val address = InetAddress.getByName("127.0.0.1")
            val asyncPreLogin = AsyncPlayerPreLoginEvent(name, address, uuid)
            val preLogin = PlayerPreLoginEvent(name, address, uuid)
            Thread { Bukkit.getPluginManager().callEvent(asyncPreLogin) }.start()
            Bukkit.getPluginManager().callEvent(preLogin)
        } catch (e: Exception) {
            plugin.logger.warning("Failed to fire pre-login events: ${e.message}")
        }
    }

    private fun setPositionRotation(entityPlayer: Any, location: Location) {
        val method = entityPlayer.javaClass.getMethod(
            "setPositionRotation",
            Double::class.javaPrimitiveType,
            Double::class.javaPrimitiveType,
            Double::class.javaPrimitiveType,
            Float::class.javaPrimitiveType,
            Float::class.javaPrimitiveType
        )
        method.invoke(
            entityPlayer,
            location.x,
            location.y,
            location.z,
            location.yaw,
            location.pitch
        )
    }

    private fun setDataWatcherFlags(entityPlayer: Any) {
        runCatching {
            val dataWatcher = invokeMethod(entityPlayer, "getDataWatcher")
            val dataWatcherRegistryClass = nmsClass("DataWatcherRegistry")
            val serializer = dataWatcherRegistryClass.getField("a").get(null)
            val dataWatcherObject = invokeMethod(serializer, "a", 13)
            val setMethod = dataWatcher.javaClass.methods.firstOrNull { it.name == "set" && it.parameterCount == 2 }
            setMethod?.invoke(dataWatcher, dataWatcherObject, 127.toByte())
        }
    }

    private fun spawnInWorld(entityPlayer: Any, worldServer: Any) {
        invokeMethodIfExists(entityPlayer, "spawnIn", worldServer)
        val playerInteractManager = findField(entityPlayer.javaClass, "playerInteractManager")?.get(entityPlayer)
        if (playerInteractManager != null) {
            invokeMethodIfExists(playerInteractManager, "a", worldServer)
        }
    }

    private fun setGameMode(entityPlayer: Any, worldServer: Any) {
        val playerInteractManager = findField(entityPlayer.javaClass, "playerInteractManager")?.get(entityPlayer) ?: return
        val enumGamemodeClass = nmsClass("EnumGamemode")
        val gameMode = Bukkit.getServer().defaultGameMode
        val enumValue = enumValue(enumGamemodeClass, gameMode)
        invokeMethodIfExists(playerInteractManager, "b", enumValue)
    }

    private fun setupConnection(entityPlayer: Any, minecraftServer: Any) {
        val networkManagerClass = nmsClass("NetworkManager")
        val enumProtocolDirectionClass = nmsClass("EnumProtocolDirection")
        val serverbound = enumValue(enumProtocolDirectionClass, "SERVERBOUND")
        val networkManager = networkManagerClass
            .getConstructor(enumProtocolDirectionClass)
            .newInstance(serverbound)

        val playerConnectionClass = nmsClass("PlayerConnection")
        val pcCtor = playerConnectionClass.constructors.firstOrNull { ctor ->
            val params = ctor.parameterTypes
            params.size == 3 &&
                params[0].isAssignableFrom(minecraftServer.javaClass) &&
                params[1].isAssignableFrom(networkManagerClass) &&
                params[2].isAssignableFrom(entityPlayer.javaClass)
        } ?: playerConnectionClass.constructors.first()
        val playerConnection = pcCtor.newInstance(minecraftServer, networkManager, entityPlayer)

        setFieldValue(entityPlayer, "playerConnection", playerConnection)
        val channel = EmbeddedChannel(ChannelInboundHandlerAdapter())
        setFieldValue(networkManager, "channel", channel)
        runCatching { channel.close() }
    }

    private fun addToPlayerChunkMap(worldServer: Any, entityPlayer: Any) {
        val playerChunkMap = getPlayerChunkMap(worldServer)
        invokeMethodIfExists(playerChunkMap, "addPlayer", entityPlayer)
    }

    private fun addToPlayerList(playerList: Any, entityPlayer: Any) {
        runCatching {
            val playersField = findField(playerList.javaClass, "players")
            @Suppress("UNCHECKED_CAST") val players = playersField?.get(playerList) as? MutableCollection<Any>
            players?.add(entityPlayer)
        }
    }

    private fun updatePlayerListMaps(playerList: Any, entityPlayer: Any) {
        runCatching {
            val byUuidField = findField(playerList.javaClass, "j")
            @Suppress("UNCHECKED_CAST") val byUuid = byUuidField?.get(playerList) as? MutableMap<Any, Any>
            byUuid?.put(uuid, entityPlayer)
        }
        runCatching {
            val byNameField = findField(playerList.javaClass, "playersByName")
            @Suppress("UNCHECKED_CAST") val byName = byNameField?.get(playerList) as? MutableMap<Any, Any>
            byName?.put(name, entityPlayer)
        }
    }

    private fun sendSpawnPackets(entityPlayer: Any) {
        val packetPlayOutPlayerInfo = nmsClass("PacketPlayOutPlayerInfo")
        val actionClass = nmsClass("PacketPlayOutPlayerInfo\$EnumPlayerInfoAction")
        val addAction = enumValue(actionClass, "ADD_PLAYER")
        val entityArray = java.lang.reflect.Array.newInstance(entityPlayer.javaClass, 1)
        java.lang.reflect.Array.set(entityArray, 0, entityPlayer)
        val infoPacket = packetPlayOutPlayerInfo
            .getConstructor(actionClass, entityArray.javaClass)
            .newInstance(addAction, entityArray)

        val namedSpawnPacket = createSingleArgPacket("PacketPlayOutNamedEntitySpawn", entityPlayer)

        val craftPlayerClass = craftClass("entity.CraftPlayer")
        for (player in Bukkit.getOnlinePlayers()) {
            val craftPlayer = craftPlayerClass.cast(player)
            val handle = craftPlayerClass.getMethod("getHandle").invoke(craftPlayer)
            val connection = findField(handle.javaClass, "playerConnection")?.get(handle) ?: continue
            sendPacket(connection, infoPacket)
            if (namedSpawnPacket != null) {
                sendPacket(connection, namedSpawnPacket)
            }
        }
    }

    private fun sendRemovePackets(entityPlayer: Any) {
        val packetDestroy = createEntityDestroyPacket(entityPlayer)
        val packetInfo = createPlayerInfoPacket("REMOVE_PLAYER", entityPlayer)
        val craftPlayerClass = craftClass("entity.CraftPlayer")
        for (player in Bukkit.getOnlinePlayers()) {
            val craftPlayer = craftPlayerClass.cast(player)
            val handle = craftPlayerClass.getMethod("getHandle").invoke(craftPlayer)
            val connection = findField(handle.javaClass, "playerConnection")?.get(handle) ?: continue
            if (packetDestroy != null) sendPacket(connection, packetDestroy)
            if (packetInfo != null) sendPacket(connection, packetInfo)
        }
    }

    private fun createPlayerInfoPacket(actionName: String, entityPlayer: Any): Any? {
        return runCatching {
            val packetPlayOutPlayerInfo = nmsClass("PacketPlayOutPlayerInfo")
            val actionClass = nmsClass("PacketPlayOutPlayerInfo\$EnumPlayerInfoAction")
            val action = enumValue(actionClass, actionName)
            val entityArray = java.lang.reflect.Array.newInstance(entityPlayer.javaClass, 1)
            java.lang.reflect.Array.set(entityArray, 0, entityPlayer)
            packetPlayOutPlayerInfo
                .getConstructor(actionClass, entityArray.javaClass)
                .newInstance(action, entityArray)
        }.getOrNull()
    }

    private fun createEntityDestroyPacket(entityPlayer: Any): Any? {
        return runCatching {
            val packetClass = nmsClass("PacketPlayOutEntityDestroy")
            val getIdMethod = entityPlayer.javaClass.getMethod("getId")
            val entityId = getIdMethod.invoke(entityPlayer) as Int
            val ids = intArrayOf(entityId)
            packetClass.getConstructor(IntArray::class.java).newInstance(ids)
        }.getOrNull()
    }

    private fun createSingleArgPacket(className: String, entityPlayer: Any): Any? {
        return runCatching {
            val packetClass = nmsClass(className)
            val ctor = packetClass.constructors.firstOrNull { ctor ->
                ctor.parameterTypes.size == 1 && ctor.parameterTypes[0].isAssignableFrom(entityPlayer.javaClass)
            } ?: packetClass.constructors.firstOrNull { ctor ->
                ctor.parameterTypes.size == 1 && ctor.parameterTypes[0].isAssignableFrom(entityPlayer.javaClass.superclass)
            }
            ctor?.newInstance(entityPlayer)
        }.getOrNull()
    }

    private fun addEntityToWorld(worldServer: Any, entityPlayer: Any) {
        invokeMethodIfExists(worldServer, "addEntity", entityPlayer)
    }

    private fun getWorldServer(entityPlayer: Any): Any {
        val worldField = findField(entityPlayer.javaClass, "world")
        return worldField?.get(entityPlayer) ?: invokeMethod(entityPlayer, "getWorld")
    }

    private fun getPlayerChunkMap(worldServer: Any): Any {
        val getPlayerChunkMap = worldServer.javaClass.methods.firstOrNull { it.name == "getPlayerChunkMap" }
        return if (getPlayerChunkMap != null) {
            getPlayerChunkMap.invoke(worldServer)
        } else {
            findField(worldServer.javaClass, "playerChunkMap")?.get(worldServer)
                ?: throw NoSuchFieldException("playerChunkMap not found on ${worldServer.javaClass.name}")
        }
    }

    private fun removeFromPlayerList(playerList: Any, entityPlayer: Any) {
        runCatching {
            val playersField = findField(playerList.javaClass, "players")
            @Suppress("UNCHECKED_CAST") val players = playersField?.get(playerList) as? MutableCollection<Any>
            players?.remove(entityPlayer)
        }
    }

    private fun removeFromPlayerMaps(playerList: Any, entityPlayer: Any) {
        runCatching {
            val byUuidField = findField(playerList.javaClass, "j")
            @Suppress("UNCHECKED_CAST") val byUuid = byUuidField?.get(playerList) as? MutableMap<Any, Any>
            byUuid?.remove(uuid)
        }
        runCatching {
            val byNameField = findField(playerList.javaClass, "playersByName")
            @Suppress("UNCHECKED_CAST") val byName = byNameField?.get(playerList) as? MutableMap<Any, Any>
            byName?.remove(name)
        }
    }

    private fun sendPacket(connection: Any, packet: Any) {
        val packetClass = nmsClass("Packet")
        val sendMethod = connection.javaClass.getMethod("sendPacket", packetClass)
        sendMethod.invoke(connection, packet)
    }

    private fun broadcastMessage(message: String?) {
        if (message.isNullOrEmpty()) return
        for (player in Bukkit.getOnlinePlayers()) {
            player.sendMessage(message)
        }
    }

    private fun invokeMethod(target: Any, name: String, vararg args: Any?): Any {
        val method = findMethod(target.javaClass, name, args)
            ?: throw NoSuchMethodException("Method '$name' not found on ${target.javaClass.name}")
        return method.invoke(target, *args)
    }

    private fun invokeMethodIfExists(target: Any, name: String, vararg args: Any?) {
        val method = findMethod(target.javaClass, name, args, ignoreMissing = true) ?: return
        method.invoke(target, *args)
    }

    private fun findMethod(
        clazz: Class<*>,
        name: String,
        args: Array<out Any?>,
        ignoreMissing: Boolean = false
    ): Method? {
        val candidates = (clazz.methods + clazz.declaredMethods).filter { it.name == name }
        val method = candidates.firstOrNull { method ->
            if (method.parameterTypes.size != args.size) return@firstOrNull false
            method.parameterTypes.indices.all { idx ->
                val arg = args[idx]
                arg == null || method.parameterTypes[idx].isAssignableFrom(arg.javaClass)
            }
        } ?: candidates.firstOrNull { it.parameterTypes.size == args.size }
        if (method == null && !ignoreMissing) {
            throw NoSuchMethodException("Method '$name' not found on ${clazz.name}")
        }
        method?.isAccessible = true
        return method
    }

    private fun findField(clazz: Class<*>, name: String): Field? {
        var current: Class<*>? = clazz
        while (current != null) {
            runCatching {
                val field = current.getDeclaredField(name)
                field.isAccessible = true
                return field
            }
            current = current.superclass
        }
        return null
    }

    private fun setFieldValue(target: Any, name: String, value: Any?) {
        val field = findField(target.javaClass, name) ?: return
        field.set(target, value)
    }

    private fun enumValue(enumClass: Class<*>, name: String): Any {
        @Suppress("UNCHECKED_CAST")
        val enumType = enumClass as Class<out Enum<*>>
        return java.lang.Enum.valueOf(enumType, name)
    }

    private fun enumValue(enumClass: Class<*>, gameMode: GameMode): Any {
        return enumValue(enumClass, gameMode.name)
    }
}
