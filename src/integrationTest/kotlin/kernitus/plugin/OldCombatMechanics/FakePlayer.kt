/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics

import com.mojang.authlib.GameProfile
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.embedded.EmbeddedChannel
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.event.player.AsyncPlayerPreLoginEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerPreLoginEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import xyz.jpenilla.reflectionremapper.ReflectionRemapper
import java.lang.reflect.Method
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.net.UnknownHostException
import java.util.*

class FakePlayer(private val plugin: JavaPlugin) {
    val uuid: UUID = UUID.randomUUID()
    private val name: String = uuid.toString().substring(0, 16)
    private lateinit var serverPlayer: Any // NMS ServerPlayer instance
    private var bukkitPlayer: Player? = null
    private var networkConnection: Any? = null
    private var usedPlaceNewPlayer: Boolean = false
    private var tickTaskId: Int? = null
    private val isLegacy9 = !Reflector.versionIsNewerOrEqualTo(1, 10, 0) // 1.9.x and below
    private val isLegacy12 = !Reflector.versionIsNewerOrEqualTo(1, 13, 0) && Reflector.versionIsNewerOrEqualTo(1, 10, 0)
    private val legacyImpl9: LegacyFakePlayer9? = if (isLegacy9) LegacyFakePlayer9(plugin, uuid, name) else null
    private val legacyImpl12: LegacyFakePlayer12? = if (isLegacy12) LegacyFakePlayer12(plugin, uuid, name) else null
    private val reflectionRemapper: ReflectionRemapper = try {
        ReflectionRemapper.forReobfMappingsInPaperJar()
    } catch (e: Throwable) {
        plugin.logger.warning("Reflection mappings not found; using no-op remapper for legacy server.")
        ReflectionRemapper.noop()
    }

    // Helper function to load NMS classes using the appropriate class loader and remap names
    fun getNMSClass(name: String): Class<*> {
        // Remap the class name
        val remappedName = reflectionRemapper.remapClassName(name)
        // Get the NMS MinecraftServer from the Bukkit server
        val server = Bukkit.getServer()
        val craftServerClass = server.javaClass
        val getServerMethod = Reflector.getMethod(craftServerClass, "getServer")
            ?: throw NoSuchMethodException("Cannot find getServer method in ${craftServerClass.name}")
        val minecraftServer = Reflector.invokeMethod<Any>(getServerMethod, server)

        return Class.forName(remappedName, true, minecraftServer.javaClass.classLoader)
    }

    fun spawn(location: Location) {
        if (isLegacy9) {
            legacyImpl9!!.spawn(location)
            serverPlayer = legacyImpl9.entityPlayer ?: throw IllegalStateException("Legacy9 entity player not created.")
            bukkitPlayer = legacyImpl9.bukkitPlayer
            return
        }
        if (isLegacy12) {
            legacyImpl12!!.spawn(location)
            serverPlayer = legacyImpl12.entityPlayer ?: throw IllegalStateException("Legacy12 entity player not created.")
            bukkitPlayer = legacyImpl12.bukkitPlayer
            return
        }
        plugin.logger.info("Spawn: Starting")

        // Get the NMS WorldServer (ServerLevel) from the Bukkit world
        val world = location.world ?: throw IllegalArgumentException("Location has no world!")
        val craftWorldClass = world.javaClass
        val getHandleMethod = Reflector.getMethod(craftWorldClass, "getHandle")
            ?: throw NoSuchMethodException("Cannot find getHandle method in ${craftWorldClass.name}")
        val worldServer = Reflector.invokeMethod<Any>(getHandleMethod, world)

        val minecraftServer = getMinecraftServer()

        // Create a GameProfile for the fake player
        val gameProfile = GameProfile(uuid, name)

        // Get the ServerPlayer class and its constructor
        val minecraftServerClass = getNMSClass("net.minecraft.server.MinecraftServer")
        val serverPlayerClass = getNMSClass("net.minecraft.server.level.ServerPlayer")

        // Create a new instance of ServerPlayer (constructor signature varies by version)
        this.serverPlayer = createServerPlayer(
            serverPlayerClass,
            minecraftServerClass,
            minecraftServer,
            worldServer,
            gameProfile
        )
        plugin.logger.info("Spawn: created serverPlayer")

        // Set up the connection for the ServerPlayer
        setupPlayerConnection(minecraftServer, worldServer)

        // Set the GameMode to SURVIVAL
        setPlayerGameMode("SURVIVAL", minecraftServer)

        setPlayerPosition(location)
        setPlayerRotation(0f, 0f)

        // Fire AsyncPlayerPreLoginEvent
        fireAsyncPlayerPreLoginEvent()

        // Add the player to the server's player list
        usedPlaceNewPlayer = addToPlayerList(minecraftServer)

        // Retrieve the Bukkit Player instance
        bukkitPlayer = Bukkit.getPlayer(uuid)
            ?: throw RuntimeException("Bukkit player with UUID $uuid not found!")

        // Fire PlayerJoinEvent
        firePlayerJoinEvent()

        // Notify other players and spawn the fake player
        if (!usedPlaceNewPlayer) {
            notifyPlayersOfJoin()
            spawnPlayerInWorld(worldServer, minecraftServer)
        } else if (bukkitPlayer?.world?.players?.contains(bukkitPlayer) != true) {
            spawnPlayerInWorld(worldServer, minecraftServer)
        }

        scheduleServerPlayerTick()

        plugin.logger.info("Spawn: completed successfully")
    }

    private fun setupPlayerConnection(minecraftServer: Any, worldServer: Any) {
        // Access ServerGamePacketListenerImpl class
        val serverGamePacketListenerImplClass = getNMSClass("net.minecraft.server.network.ServerGamePacketListenerImpl")

        // Create a new Connection object
        val connectionClass = getNMSClass("net.minecraft.network.Connection")
        val packetFlowClass = getNMSClass("net.minecraft.network.protocol.PacketFlow")
        val serverboundFieldName = reflectionRemapper.remapFieldName(packetFlowClass, "SERVERBOUND")
        val packetFlow = runCatching {
            Reflector.getEnumConstant(packetFlowClass, serverboundFieldName, "SERVERBOUND")
        }.getOrElse {
            val clientboundFieldName = reflectionRemapper.remapFieldName(packetFlowClass, "CLIENTBOUND")
            Reflector.getEnumConstant(packetFlowClass, clientboundFieldName, "CLIENTBOUND")
        }
        val connectionConstructor = connectionClass.getConstructor(packetFlowClass)
        val connection = connectionConstructor.newInstance(packetFlow)
        networkConnection = connection

        // Create a custom EmbeddedChannel with an overridden remoteAddress()
        val remoteAddress = InetSocketAddress("127.0.0.1", 9999)
        val embeddedChannel = EmbeddedChannel(ChannelInboundHandlerAdapter())
        val pipeline = embeddedChannel.pipeline()
        if (pipeline.get("decoder") == null) {
            pipeline.addLast("decoder", ChannelInboundHandlerAdapter())
        }
        if (pipeline.get("encoder") == null) {
            pipeline.addLast("encoder", ChannelOutboundHandlerAdapter())
        }

        // Set the 'channel' field of 'connection' to the custom EmbeddedChannel
        val channelFieldName = reflectionRemapper.remapFieldName(connectionClass, "channel")
        val channelField = Reflector.getField(connectionClass, channelFieldName)
        channelField.isAccessible = true
        channelField.set(connection, embeddedChannel)

        // Set address field of connection
        val addressFieldName = reflectionRemapper.remapFieldName(connectionClass, "address")
        val addressField = Reflector.getField(connectionClass, addressFieldName)
        addressField.set(connection, remoteAddress)

        // Create a new ServerGamePacketListenerImpl instance (constructor signature varies by version)
        val serverPlayerClass = serverPlayer.javaClass
        val minecraftServerClass = getNMSClass("net.minecraft.server.MinecraftServer")
        val listenerInstance = createServerGamePacketListener(
            serverGamePacketListenerImplClass,
            minecraftServerClass,
            connectionClass,
            serverPlayerClass,
            minecraftServer,
            connection,
            serverPlayer
        )

        // Set the listenerInstance to the player's 'connection' field
        val connectionFieldName = reflectionRemapper.remapFieldName(serverPlayerClass, "connection")
        val connectionField = Reflector.getField(serverPlayerClass, connectionFieldName)
        Reflector.setFieldValue(connectionField, serverPlayer, listenerInstance)

        val setListenerName = reflectionRemapper.remapMethodName(
            connectionClass,
            "setListener",
            listenerInstance.javaClass
        )
        val setListenerMethod = Reflector.getMethodAssignable(
            connectionClass,
            setListenerName,
            listenerInstance.javaClass
        ) ?: Reflector.getMethodAssignable(connectionClass, "setListener", listenerInstance.javaClass)
        if (setListenerMethod != null) {
            Reflector.invokeMethod<Any>(setListenerMethod, connection, listenerInstance)
        }
    }

    private fun createServerGamePacketListener(
        listenerClass: Class<*>,
        minecraftServerClass: Class<*>,
        connectionClass: Class<*>,
        serverPlayerClass: Class<*>,
        minecraftServer: Any,
        connection: Any,
        serverPlayer: Any
    ): Any {
        val constructors = listenerClass.constructors.sortedBy { it.parameterCount }
        for (ctor in constructors) {
            val params = ctor.parameterTypes
            if (params.size < 3) continue
            if (!params[0].isAssignableFrom(minecraftServerClass)) continue
            if (!params[1].isAssignableFrom(connectionClass)) continue
            if (!params[2].isAssignableFrom(serverPlayerClass)) continue

            val args = ArrayList<Any?>()
            args.add(minecraftServer)
            args.add(connection)
            args.add(serverPlayer)

            var supported = true
            for (i in 3 until params.size) {
                val param = params[i]
                when (param.simpleName) {
                    "CommonListenerCookie" -> args.add(createCommonListenerCookie(param, serverPlayer))
                    else -> {
                        supported = false
                        break
                    }
                }
            }

            if (!supported) continue
            return ctor.newInstance(*args.toTypedArray())
        }

        throw NoSuchMethodException("No compatible ServerGamePacketListenerImpl constructor found for ${listenerClass.name}")
    }

    private fun createCommonListenerCookie(cookieClass: Class<*>, serverPlayer: Any): Any {
        val getProfileName = reflectionRemapper.remapMethodName(serverPlayer.javaClass, "getGameProfile")
        val getProfileMethod = Reflector.getMethod(serverPlayer.javaClass, getProfileName)
            ?: Reflector.getMethod(serverPlayer.javaClass, "getGameProfile")
            ?: throw NoSuchMethodException("getGameProfile not found in ${serverPlayer.javaClass.name}")
        val gameProfile = Reflector.invokeMethod<GameProfile>(getProfileMethod, serverPlayer)

        val remappedName = reflectionRemapper.remapMethodName(
            cookieClass,
            "createInitial",
            GameProfile::class.java,
            Boolean::class.javaPrimitiveType
        )
        val method = Reflector.getMethod(cookieClass, remappedName, "GameProfile", "boolean")
            ?: Reflector.getMethod(cookieClass, "createInitial", "GameProfile", "boolean")
            ?: throw NoSuchMethodException("createInitial not found in ${cookieClass.name}")
        return Reflector.invokeMethod(method, null, gameProfile, false)
    }

    private fun createServerPlayer(
        serverPlayerClass: Class<*>,
        minecraftServerClass: Class<*>,
        minecraftServer: Any,
        worldServer: Any,
        gameProfile: GameProfile
    ): Any {
        val constructors = serverPlayerClass.constructors.sortedBy { it.parameterCount }
        for (ctor in constructors) {
            val params = ctor.parameterTypes
            if (params.size < 3) continue
            if (!params[0].isAssignableFrom(minecraftServerClass)) continue
            if (!params[1].isAssignableFrom(worldServer.javaClass)) continue
            if (params[2] != GameProfile::class.java) continue

            val args = ArrayList<Any?>()
            args.add(minecraftServer)
            args.add(worldServer)
            args.add(gameProfile)

            var supported = true
            for (i in 3 until params.size) {
                val param = params[i]
                when (param.simpleName) {
                    "ProfilePublicKey" -> args.add(null)
                    "ClientInformation" -> args.add(createDefaultClientInformation(param))
                    else -> {
                        supported = false
                        break
                    }
                }
            }

            if (!supported) continue
            return ctor.newInstance(*args.toTypedArray())
        }

        throw NoSuchMethodException("No compatible ServerPlayer constructor found for ${serverPlayerClass.name}")
    }

    private fun createDefaultClientInformation(clientInfoClass: Class<*>): Any {
        val remappedName = reflectionRemapper.remapMethodName(clientInfoClass, "createDefault")
        val method = Reflector.getMethod(clientInfoClass, remappedName)
            ?: Reflector.getMethod(clientInfoClass, "createDefault")
            ?: throw NoSuchMethodException("createDefault not found in ${clientInfoClass.name}")
        return Reflector.invokeMethod(method, null)
    }

    private fun setPlayerGameMode(gameModeName: String, minecraftServer: Any) {
        val gameModeClass = getNMSClass("net.minecraft.world.level.GameType")
        val gameModeFieldName = reflectionRemapper.remapFieldName(gameModeClass, gameModeName)
        val gameModeField = Reflector.getField(gameModeClass, gameModeFieldName)
        val gameMode = gameModeField.get(null)
        val setGameModeMethodName = reflectionRemapper.remapMethodName(
            serverPlayer.javaClass,
            "setGameMode",
            gameModeClass
        )
        val setGameModeMethod = serverPlayer.javaClass.getMethod(setGameModeMethodName, gameModeClass)
        setGameModeMethod.invoke(serverPlayer, gameMode)
    }

    private fun setPlayerPosition(location: Location) {
        val entityClass = getNMSClass("net.minecraft.world.entity.Entity")
        val setPosMethodName = reflectionRemapper.remapMethodName(
            entityClass,
            "setPos",
            Double::class.javaPrimitiveType,
            Double::class.javaPrimitiveType,
            Double::class.javaPrimitiveType
        )
        val setPosMethod = checkNotNull(
            Reflector.getMethod(
                entityClass,
                setPosMethodName,
                "double",
                "double",
                "double"
            )
        )
        setPosMethod.invoke(serverPlayer, location.x, location.y, location.z)
    }

    private fun setPlayerRotation(xRot: Float, yRot: Float) {
        val entityClass = getNMSClass("net.minecraft.world.entity.Entity")
        val xRotFieldName = reflectionRemapper.remapFieldName(entityClass, "xRot")
        val xRotField = Reflector.getField(entityClass, xRotFieldName)
        xRotField.setFloat(serverPlayer, xRot)

        val yRotFieldName = reflectionRemapper.remapFieldName(entityClass, "yRot")
        val yRotField = Reflector.getField(entityClass, yRotFieldName)
        yRotField.setFloat(serverPlayer, yRot)
    }

    private fun fireAsyncPlayerPreLoginEvent() {
        try {
            val ipAddress = InetAddress.getByName("127.0.0.1")
            @Suppress("DEPRECATION") // Legacy constructor kept for older server compatibility in tests.
            val asyncPreLoginEvent = AsyncPlayerPreLoginEvent(name, ipAddress, uuid)
            Thread { Bukkit.getPluginManager().callEvent(asyncPreLoginEvent) }.start()
        } catch (e: UnknownHostException) {
            plugin.logger.severe("Spawn: UnknownHostException - ${e.message}")
            e.printStackTrace()
        }
    }

    private fun addToPlayerList(minecraftServer: Any): Boolean {
        val playerList = getPlayerList(minecraftServer)

        // Add the player to the PlayerList
        val playerListClass = getNMSClass("net.minecraft.server.players.PlayerList")
        val placeMethodName = reflectionRemapper.remapMethodName(playerListClass, "placeNewPlayer")
        val connection = checkNotNull(networkConnection) { "Connection not initialised" }
        val placeMethodWithCookie = Reflector.getMethodAssignable(
            playerListClass,
            placeMethodName,
            connection.javaClass,
            serverPlayer.javaClass,
            null
        ) ?: Reflector.getMethodAssignable(
            playerListClass,
            "placeNewPlayer",
            connection.javaClass,
            serverPlayer.javaClass,
            null
        )

        if (placeMethodWithCookie != null && placeMethodWithCookie.parameterTypes.size == 3) {
            val cookieClass = placeMethodWithCookie.parameterTypes[2]
            val cookie = createCommonListenerCookie(cookieClass, serverPlayer)
            placeMethodWithCookie.invoke(playerList, connection, serverPlayer, cookie)
            return true
        }

        val placeMethod = Reflector.getMethodAssignable(
            playerListClass,
            placeMethodName,
            connection.javaClass,
            serverPlayer.javaClass
        ) ?: Reflector.getMethodAssignable(
            playerListClass,
            "placeNewPlayer",
            connection.javaClass,
            serverPlayer.javaClass
        )

        if (placeMethod != null) {
            placeMethod.invoke(playerList, connection, serverPlayer)
            return true
        }

        val loadMethodName = reflectionRemapper.remapMethodName(playerListClass, "load", serverPlayer.javaClass)
        val loadMethod = playerListClass.methods.firstOrNull { method ->
            method.name == loadMethodName &&
                method.parameterCount == 1 &&
                method.parameterTypes[0].isAssignableFrom(serverPlayer.javaClass)
        }

        if (loadMethod != null) {
            Reflector.invokeMethod<Any>(loadMethod, playerList, serverPlayer)

            val playersFieldName = reflectionRemapper.remapFieldName(playerListClass, "players")
            val playersField = playerListClass.getDeclaredField(playersFieldName)
            playersField.isAccessible = true
            @Suppress("UNCHECKED_CAST") // Reflection into NMS collection; types vary by version.
            val players = playersField.get(playerList) as MutableList<Any>
            players.add(serverPlayer)

            // Add player to the UUID map
            val playersByUUIDField = Reflector.getMapFieldWithTypes(
                playerListClass, UUID::class.java, serverPlayer.javaClass
            )
            @Suppress("UNCHECKED_CAST") // Reflection into NMS map; types vary by version.
            val playerByUUID = Reflector.getFieldValue(playersByUUIDField, playerList) as MutableMap<UUID, Any>
            playerByUUID[uuid] = serverPlayer
            return false
        }

        throw NoSuchMethodException("No compatible PlayerList add method found for ${playerListClass.name}")
    }

    private fun firePlayerJoinEvent() {
        val joinMessage = "$name joined the game"
        val playerJoinEvent = PlayerJoinEvent(bukkitPlayer!!, joinMessage)
        Bukkit.getPluginManager().callEvent(playerJoinEvent)
    }

    private fun notifyPlayersOfJoin() {
        if (Bukkit.getOnlinePlayers().isEmpty()) return

        val packet = runCatching { createLegacyPlayerInfoPacket() }.getOrNull()
            ?: runCatching { createPlayerInfoUpdatePacket() }.getOrNull()
            ?: return
        sendPacket(packet)
    }

    private fun spawnPlayerInWorld(worldServer: Any, minecraftServer: Any) {
        // Add the player to the world
        val worldServerClass = worldServer.javaClass
        val addNewPlayerMethodName = reflectionRemapper.remapMethodName(
            worldServerClass,
            "addNewPlayer",
            serverPlayer.javaClass
        )
        val addNewPlayerMethod = Reflector.getMethodAssignable(
            worldServerClass,
            addNewPlayerMethodName,
            serverPlayer.javaClass
        )
            ?: Reflector.getMethodAssignable(worldServerClass, "addNewPlayer", serverPlayer.javaClass)
            ?: Reflector.getMethodAssignable(worldServerClass, "addPlayer", serverPlayer.javaClass)
            ?: Reflector.getMethodAssignable(worldServerClass, "addFreshEntity", serverPlayer.javaClass)
            ?: Reflector.getMethodAssignable(worldServerClass, "addEntity", serverPlayer.javaClass)
        if (addNewPlayerMethod != null) {
            addNewPlayerMethod.invoke(worldServer, serverPlayer)
        } else {
            plugin.logger.warning("Spawn: Could not find a world add method for ${worldServerClass.name}")
        }

        // Send world info to the player
        val minecraftServerClass = getNMSClass("net.minecraft.server.MinecraftServer")
        runCatching {
            val getStatusMethodName = reflectionRemapper.remapMethodName(minecraftServerClass, "getStatus")
            val getStatusMethod = Reflector.getMethod(minecraftServerClass, getStatusMethodName)
            val status = Reflector.invokeMethod<Any>(getStatusMethod!!, minecraftServer)

            val sendServerStatusMethodName = reflectionRemapper.remapMethodName(
                serverPlayer.javaClass,
                "sendServerStatus",
                status.javaClass
            )
            val sendServerStatusMethod = Reflector.getMethod(
                serverPlayer.javaClass,
                sendServerStatusMethodName,
                status.javaClass.simpleName
            ) ?: Reflector.getMethod(serverPlayer.javaClass, "sendServerStatus", status.javaClass.simpleName)
            if (sendServerStatusMethod != null) {
                sendServerStatusMethod.invoke(serverPlayer, status)
            }
        }

        // Send ClientboundAddPlayerPacket to all players
        runCatching {
            val clientboundAddPlayerPacketClass =
                getNMSClass("net.minecraft.network.protocol.game.ClientboundAddPlayerPacket")
            val playerClassName = getNMSClass("net.minecraft.world.entity.player.Player")
            // ServerPlayer is subclass of Player
            val clientboundAddPlayerPacketConstructor = clientboundAddPlayerPacketClass.getConstructor(playerClassName)

            val packet = clientboundAddPlayerPacketConstructor.newInstance(serverPlayer)
            sendPacket(packet)
        }
    }

    private fun createLegacyPlayerInfoPacket(): Any {
        val clientboundPlayerInfoPacketClass =
            getNMSClass("net.minecraft.network.protocol.game.ClientboundPlayerInfoPacket")
        val actionClass = getNMSClass("net.minecraft.network.protocol.game.ClientboundPlayerInfoPacket\$Action")
        val addPlayerFieldName = reflectionRemapper.remapFieldName(actionClass, "ADD_PLAYER")
        val addPlayerAction = actionClass.getDeclaredField(addPlayerFieldName).get(null)

        val clientboundPlayerInfoPacketConstructor = clientboundPlayerInfoPacketClass.getConstructor(
            actionClass,
            Collection::class.java
        )

        return clientboundPlayerInfoPacketConstructor.newInstance(
            addPlayerAction,
            listOf(serverPlayer)
        )
    }

    private fun createPlayerInfoUpdatePacket(): Any {
        val packetClass = getNMSClass("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket")
        val actionClass = getNMSClass("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket\$Action")
        val addPlayerFieldName = reflectionRemapper.remapFieldName(actionClass, "ADD_PLAYER")
        val addPlayerAction = actionClass.getDeclaredField(addPlayerFieldName).get(null)

        val createInitMethodName = reflectionRemapper.remapMethodName(
            packetClass,
            "createPlayerInitializing",
            serverPlayer.javaClass
        )
        val createInitMethod = Reflector.getMethodAssignable(
            packetClass,
            createInitMethodName,
            serverPlayer.javaClass
        ) ?: Reflector.getMethodAssignable(packetClass, "createPlayerInitializing", serverPlayer.javaClass)
        if (createInitMethod != null) {
            return Reflector.invokeMethod(createInitMethod, null, serverPlayer)
        }

        val constructor = packetClass.getConstructor(EnumSet::class.java, Collection::class.java)
        val enumSetNoneOf = EnumSet::class.java.getMethod("noneOf", Class::class.java)
        @Suppress("UNCHECKED_CAST")
        val actions = enumSetNoneOf.invoke(null, actionClass) as MutableSet<Any>
        actions.add(addPlayerAction)
        return constructor.newInstance(actions, listOf(serverPlayer))
    }

    private fun sendPacket(packet: Any) {
        // Get the Packet class
        val packetClass = getNMSClass("net.minecraft.network.protocol.Packet")

        // Send packet to all online players
        Bukkit.getOnlinePlayers().forEach { player ->
            val craftPlayerClass = player.javaClass
            val getHandleMethodName = reflectionRemapper.remapMethodName(craftPlayerClass, "getHandle")
            val getHandleMethod = craftPlayerClass.getMethod(getHandleMethodName)
            val entityPlayer = getHandleMethod.invoke(player)

            val connection = getConnection(entityPlayer)

            val sendMethodName = reflectionRemapper.remapMethodName(connection.javaClass, "send", packetClass)
            val sendMethod = connection.javaClass.getMethod(sendMethodName, packetClass)
            sendMethod.invoke(connection, packet)
        }
    }

    fun getConnection(serverPlayer: Any): Any {
        if (isLegacy9) return legacyImpl9!!.getConnection(serverPlayer)
        if (isLegacy12) return legacyImpl12!!.getConnection(serverPlayer)
        val entityPlayerClass = serverPlayer.javaClass
        val connectionFieldName = reflectionRemapper.remapFieldName(entityPlayerClass, "connection")
        val connectionField = Reflector.getField(entityPlayerClass, connectionFieldName)
        if (connectionField != null) return connectionField.get(serverPlayer)
        val legacyField = Reflector.getField(entityPlayerClass, "playerConnection")
        return legacyField?.get(serverPlayer) ?: error("No connection field on ${entityPlayerClass.name}")
    }

    fun removePlayer() {
        tickTaskId?.let { Bukkit.getScheduler().cancelTask(it) }
        if (isLegacy9) {
            legacyImpl9!!.removePlayer()
            return
        }
        if (isLegacy12) {
            legacyImpl12!!.removePlayer()
            return
        }
        // Fire PlayerQuitEvent
        val quitMessage = "§e$name left the game"
        val playerQuitEvent = PlayerQuitEvent(bukkitPlayer!!, quitMessage)
        Bukkit.getPluginManager().callEvent(playerQuitEvent)

        // Disconnect the player
        bukkitPlayer!!.kickPlayer(quitMessage)

        // Remove the player from the world
        /*
    val removeMethodName = reflectionRemapper.remapMethodName(
        serverPlayer.javaClass,
        "remove"
    )
    val removeMethod = serverPlayer.javaClass.getMethod(removeMethodName)
    removeMethod.invoke(serverPlayer)
         */

        // Remove from playerList if still present and not already removed
        runCatching {
            val playerList = getPlayerList(getMinecraftServer())
            if (!isEntityRemoved(serverPlayer) && isPlayerListed(playerList)) {
                val removePlayerMethodName = reflectionRemapper.remapMethodName(
                    playerList.javaClass,
                    "remove",
                    serverPlayer.javaClass
                )
                val removePlayerMethod = playerList.javaClass.getMethod(removePlayerMethodName, serverPlayer.javaClass)
                removePlayerMethod.invoke(playerList, serverPlayer)
            }
        }

        // Close the connection properly
        val connection = getConnection(serverPlayer)
        val disconnectMethodName = reflectionRemapper.remapMethodName(
            connection.javaClass,
            "disconnect",
            getNMSClass("net.minecraft.network.chat.Component")
        )
        val disconnectMethod =
            connection.javaClass.getMethod(disconnectMethodName, getNMSClass("net.minecraft.network.chat.Component"))
        val quitMessageNoColour = "$name left the game"
        val disconnectMessage = getNMSComponent(quitMessageNoColour)
        disconnectMethod.invoke(connection, disconnectMessage)
    }

    private fun scheduleServerPlayerTick() {
        if (isLegacy9 || isLegacy12) return
        val serverPlayerClass = serverPlayer.javaClass
        val tickMethod = resolveServerPlayerTickMethod(serverPlayerClass)
        val baseTickMethod = resolveBaseTickMethod(serverPlayerClass)
        val getRemainingFireTicksMethod = runCatching {
            val entityClass = getNMSClass("net.minecraft.world.entity.Entity")
            val remapped = reflectionRemapper.remapMethodName(entityClass, "getRemainingFireTicks")
            Reflector.getMethod(entityClass, remapped) ?: Reflector.getMethod(entityClass, "getRemainingFireTicks")
        }.getOrNull()
        tickTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, Runnable {
            val remainingFireTicks = if (getRemainingFireTicksMethod != null) {
                runCatching { getRemainingFireTicksMethod.invoke(serverPlayer) as Int }.getOrNull()
            } else {
                null
            }
            if (remainingFireTicks != null && remainingFireTicks > 0) {
                val player = bukkitPlayer
                val inWater = player != null && (
                    player.location.block.type == Material.WATER ||
                        player.eyeLocation.block.type == Material.WATER
                    )
                if (inWater && tickMethod != null) {
                    runCatching { tickMethod.invoke(serverPlayer) }
                    return@Runnable
                }
                if (baseTickMethod != null) {
                    runCatching { baseTickMethod.invoke(serverPlayer) }
                    return@Runnable
                }
            }

            if (tickMethod != null) {
                runCatching { tickMethod.invoke(serverPlayer) }
            } else if (baseTickMethod != null) {
                runCatching { baseTickMethod.invoke(serverPlayer) }
            }
        }, 1L, 1L)
    }

    private fun resolveServerPlayerTickMethod(serverPlayerClass: Class<*>): Method? {
        val candidateNames = listOf("doTick", "tick")
        for (name in candidateNames) {
            val remapped = reflectionRemapper.remapMethodName(serverPlayerClass, name)
            val method = Reflector.getMethod(serverPlayerClass, remapped) ?: Reflector.getMethod(serverPlayerClass, name)
            if (method != null && method.parameterCount == 0) {
                method.isAccessible = true
                return method
            }
        }

        val baseTickMethod = resolveBaseTickMethod(serverPlayerClass)
        if (baseTickMethod != null) {
            return baseTickMethod
        }

        val fallback = serverPlayerClass.methods.firstOrNull { method ->
            method.parameterCount == 0 &&
                method.returnType == Void.TYPE &&
                method.name.lowercase().contains("tick")
        } ?: serverPlayerClass.methods.firstOrNull { method ->
            method.parameterCount == 0 && method.returnType == Void.TYPE
        }

        fallback?.isAccessible = true
        return fallback
    }

    private fun resolveBaseTickMethod(serverPlayerClass: Class<*>): Method? {
        val entityClass = runCatching { getNMSClass("net.minecraft.world.entity.Entity") }.getOrNull()
        val candidateNames = buildList {
            entityClass?.let { add(reflectionRemapper.remapMethodName(it, "baseTick")) }
            add("baseTick")
        }.distinct()
        for (name in candidateNames) {
            val method = (serverPlayerClass.declaredMethods + serverPlayerClass.methods).firstOrNull { candidate ->
                candidate.name == name && candidate.parameterCount == 0
            } ?: entityClass?.let { clazz ->
                (clazz.declaredMethods + clazz.methods).firstOrNull { candidate ->
                    candidate.name == name && candidate.parameterCount == 0
                }
            }
            if (method != null) {
                method.isAccessible = true
                return method
            }
        }
        return null
    }

    private fun getNMSComponent(message: String): Any {
        // Convert a String to an NMS Component
        val componentClass = getNMSClass("net.minecraft.network.chat.Component")
        val literalName = reflectionRemapper.remapMethodName(componentClass, "literal", String::class.java)
        val componentMethod = checkNotNull(Reflector.getMethod(componentClass, literalName, "String"))
        return componentMethod.invoke(null, message)
    }

    private fun getMinecraftServer(): Any {
        val server = Bukkit.getServer()
        val craftServerClass = server.javaClass
        val getServerMethod = Reflector.getMethod(craftServerClass, "getServer")
            ?: throw NoSuchMethodException("Cannot find getServer method in ${craftServerClass.name}")
        return Reflector.invokeMethod(getServerMethod, server)
    }

    private fun getPlayerList(minecraftServer: Any): Any {
        val playerListFieldName = reflectionRemapper.remapMethodName(minecraftServer.javaClass, "getPlayerList")
        val playerListMethod = checkNotNull(Reflector.getMethod(minecraftServer.javaClass, playerListFieldName))
        return Reflector.invokeMethod(playerListMethod, minecraftServer)
    }

    private fun isPlayerListed(playerList: Any): Boolean {
        val playerListClass = getNMSClass("net.minecraft.server.players.PlayerList")
        return runCatching {
            val playersByUUIDField = Reflector.getMapFieldWithTypes(
                playerListClass,
                UUID::class.java,
                serverPlayer.javaClass
            )
            @Suppress("UNCHECKED_CAST") // Reflection into NMS map; types vary by version.
            val playerByUUID = Reflector.getFieldValue(playersByUUIDField, playerList) as Map<UUID, Any>
            playerByUUID.containsKey(uuid)
        }.getOrElse {
            val getPlayerMethodName = reflectionRemapper.remapMethodName(playerListClass, "getPlayer", UUID::class.java)
            val getPlayerMethod = Reflector.getMethodAssignable(
                playerListClass,
                getPlayerMethodName,
                UUID::class.java
            ) ?: Reflector.getMethodAssignable(playerListClass, "getPlayer", UUID::class.java)
            if (getPlayerMethod != null) {
                Reflector.invokeMethod<Any?>(getPlayerMethod, playerList, uuid) != null
            } else {
                true
            }
        }
    }

    private fun isEntityRemoved(entity: Any): Boolean {
        val entityClass = getNMSClass("net.minecraft.world.entity.Entity")
        val isRemovedMethodName = reflectionRemapper.remapMethodName(entityClass, "isRemoved")
        val isRemovedMethod = Reflector.getMethod(entityClass, isRemovedMethodName)
            ?: Reflector.getMethod(entityClass, "isRemoved")
        if (isRemovedMethod != null) {
            return Reflector.invokeMethod(isRemovedMethod, entity)
        }
        return runCatching {
            val removedFieldName = reflectionRemapper.remapFieldName(entityClass, "removed")
            val removedField = Reflector.getField(entityClass, removedFieldName)
            removedField.getBoolean(entity)
        }.getOrDefault(false)
    }

    fun attack(bukkitEntity: Entity) {
        attackCompat(checkNotNull(bukkitPlayer), bukkitEntity)
    }

    fun doBlocking() {
        if (isLegacy9 || isLegacy12) {
            // Legacy blocking: ensure sword in hand + shield in offhand, then raise offhand.
            bukkitPlayer!!.inventory.setItemInMainHand(org.bukkit.inventory.ItemStack(Material.DIAMOND_SWORD))
            if (bukkitPlayer!!.inventory.itemInOffHand.type != Material.SHIELD) {
                bukkitPlayer!!.inventory.setItemInOffHand(org.bukkit.inventory.ItemStack(Material.SHIELD))
            }
            bukkitPlayer!!.updateInventory()
            if (isLegacy12) legacyImpl12?.startUsingOffhand()
            return
        }
        bukkitPlayer!!.inventory.setItemInMainHand(org.bukkit.inventory.ItemStack(Material.SHIELD))

        val livingEntityClass = getNMSClass("net.minecraft.world.entity.LivingEntity")

        // Start using item (simulate blocking)
        val interactionHandClass = getNMSClass("net.minecraft.world.InteractionHand")
        val mainHandFieldName = reflectionRemapper.remapFieldName(interactionHandClass, "MAIN_HAND")
        val mainHandField = interactionHandClass.getDeclaredField(mainHandFieldName)
        val mainHand = mainHandField.get(null)

        val startUsingItemMethodName = reflectionRemapper.remapMethodName(
            livingEntityClass,
            "startUsingItem",
            interactionHandClass
        )

        val startUsingItemMethod = checkNotNull(
            Reflector.getMethod(livingEntityClass, startUsingItemMethodName, interactionHandClass.simpleName)
        )
        Reflector.invokeMethod<Any>(startUsingItemMethod, serverPlayer, mainHand)

        // Manually set useItemRemaining field to simulate blocking
        val useItemRemainingFieldName = reflectionRemapper.remapFieldName(livingEntityClass, "useItemRemaining")
        val useItemRemainingField = livingEntityClass.getDeclaredField(useItemRemainingFieldName)
        useItemRemainingField.isAccessible = true
        useItemRemainingField.setInt(serverPlayer, 200)
    }
}
