package kernitus.plugin.OldCombatMechanics

import com.mojang.authlib.GameProfile
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.embedded.EmbeddedChannel
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.event.player.AsyncPlayerPreLoginEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import xyz.jpenilla.reflectionremapper.ReflectionRemapper
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
    private val reflectionRemapper = ReflectionRemapper.forReobfMappingsInPaperJar()

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
        val profilePublicKeyClass = getNMSClass("net.minecraft.world.entity.player.ProfilePublicKey")
        val serverPlayerConstructor = serverPlayerClass.getConstructor(
            minecraftServerClass,
            worldServer.javaClass,
            GameProfile::class.java,
            profilePublicKeyClass
        )

        // Create a new instance of ServerPlayer
        this.serverPlayer = serverPlayerConstructor.newInstance(
            minecraftServer, worldServer, gameProfile, null
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
        addToPlayerList(minecraftServer)

        // Retrieve the Bukkit Player instance
        bukkitPlayer = Bukkit.getPlayer(uuid)
            ?: throw RuntimeException("Bukkit player with UUID $uuid not found!")

        // Fire PlayerJoinEvent
        firePlayerJoinEvent()

        // Notify other players and spawn the fake player
        notifyPlayersOfJoin()
        spawnPlayerInWorld(worldServer, minecraftServer)

        plugin.logger.info("Spawn: completed successfully")
    }

    private fun setupPlayerConnection(minecraftServer: Any, worldServer: Any) {
        // Access ServerGamePacketListenerImpl class
        val serverGamePacketListenerImplClass = getNMSClass("net.minecraft.server.network.ServerGamePacketListenerImpl")

        // Create a new Connection object
        val connectionClass = getNMSClass("net.minecraft.network.Connection")
        val packetFlowClass = getNMSClass("net.minecraft.network.protocol.PacketFlow")
        val clientboundFieldName = reflectionRemapper.remapFieldName(packetFlowClass, "CLIENTBOUND")
        val packetFlowClientbound = packetFlowClass.getDeclaredField(clientboundFieldName).get(null)
        val connectionConstructor = connectionClass.getConstructor(packetFlowClass)
        val connection = connectionConstructor.newInstance(packetFlowClientbound)

        // Create a custom EmbeddedChannel with an overridden remoteAddress()
        val remoteAddress = InetSocketAddress("127.0.0.1", 9999)
        val embeddedChannel = object : EmbeddedChannel(ChannelInboundHandlerAdapter()) {
            override fun remoteAddress(): SocketAddress {
                return remoteAddress
            }

            override fun localAddress(): SocketAddress {
                return remoteAddress
            }
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

        // Create a new ServerGamePacketListenerImpl instance
        val serverPlayerClass = serverPlayer.javaClass
        val minecraftServerClass = getNMSClass("net.minecraft.server.MinecraftServer")
        val serverGamePacketListenerImplConstructor = serverGamePacketListenerImplClass.getConstructor(
            minecraftServerClass,
            connectionClass,
            serverPlayerClass
        )
        val listenerInstance = serverGamePacketListenerImplConstructor.newInstance(
            minecraftServer, connection, serverPlayer
        )

        // Set the listenerInstance to the player's 'connection' field
        val connectionFieldName = reflectionRemapper.remapFieldName(serverPlayerClass, "connection")
        val connectionField = Reflector.getField(serverPlayerClass, connectionFieldName)
        Reflector.setFieldValue(connectionField, serverPlayer, listenerInstance)
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
                Double::class.javaPrimitiveType!!,
                Double::class.javaPrimitiveType!!,
                Double::class.javaPrimitiveType!!
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
            val asyncPreLoginEvent = AsyncPlayerPreLoginEvent(name, ipAddress, uuid)
            Thread { Bukkit.getPluginManager().callEvent(asyncPreLoginEvent) }.start()
        } catch (e: UnknownHostException) {
            plugin.logger.severe("Spawn: UnknownHostException - ${e.message}")
            e.printStackTrace()
        }
    }

    private fun addToPlayerList(minecraftServer: Any) {
        val playerList = getPlayerList(minecraftServer)

        // Add the player to the PlayerList
        val playerListClass = getNMSClass("net.minecraft.server.players.PlayerList")
        val loadMethodName = reflectionRemapper.remapMethodName(
            playerListClass,
            "load",
            serverPlayer.javaClass
        )
        val loadMethod = checkNotNull(Reflector.getMethod(playerListClass, loadMethodName, serverPlayer.javaClass))
        Reflector.invokeMethod<Any>(loadMethod, playerList, serverPlayer)

        val playersFieldName = reflectionRemapper.remapFieldName(playerListClass, "players")
        val playersField = playerListClass.getDeclaredField(playersFieldName)
        playersField.isAccessible = true
        val players = playersField.get(playerList) as MutableList<Any>
        players.add(serverPlayer)

        // Add player to the UUID map
        val playersByUUIDField = Reflector.getMapFieldWithTypes(
            playerListClass, UUID::class.java, serverPlayer.javaClass
        )
        val playerByUUID = Reflector.getFieldValue(playersByUUIDField, playerList) as MutableMap<UUID, Any>
        playerByUUID[uuid] = serverPlayer
    }

    private fun firePlayerJoinEvent() {
        val joinMessage = "$name joined the game"
        val playerJoinEvent = PlayerJoinEvent(bukkitPlayer!!, joinMessage)
        Bukkit.getPluginManager().callEvent(playerJoinEvent)
    }

    private fun notifyPlayersOfJoin() {
        // Send ClientboundPlayerInfoPacket to all players
        val clientboundPlayerInfoPacketClass =
            getNMSClass("net.minecraft.network.protocol.game.ClientboundPlayerInfoPacket")
        val actionClass = getNMSClass("net.minecraft.network.protocol.game.ClientboundPlayerInfoPacket\$Action")
        val addPlayerFieldName = reflectionRemapper.remapFieldName(actionClass, "ADD_PLAYER")
        val addPlayerAction = actionClass.getDeclaredField(addPlayerFieldName).get(null)

        val clientboundPlayerInfoPacketConstructor = clientboundPlayerInfoPacketClass.getConstructor(
            actionClass,
            Collection::class.java
        )

        val packet = clientboundPlayerInfoPacketConstructor.newInstance(
            addPlayerAction,
            listOf(serverPlayer)
        )
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
        val addNewPlayerMethod = worldServerClass.getMethod(addNewPlayerMethodName, serverPlayer.javaClass)
        addNewPlayerMethod.invoke(worldServer, serverPlayer)

        // Send world info to the player
        val minecraftServerClass = getNMSClass("net.minecraft.server.MinecraftServer")
        val getStatusMethodName = reflectionRemapper.remapMethodName(minecraftServerClass, "getStatus")
        val getStatusMethod = Reflector.getMethod(minecraftServerClass, getStatusMethodName)
        val status = Reflector.invokeMethod<Any>(getStatusMethod!!, minecraftServer)

        val sendServerStatusMethodName = reflectionRemapper.remapMethodName(
            serverPlayer.javaClass,
            "sendServerStatus",
            status.javaClass
        )
        val sendServerStatusMethod =
            checkNotNull(Reflector.getMethod(serverPlayer.javaClass, sendServerStatusMethodName, status.javaClass))
        sendServerStatusMethod.invoke(serverPlayer, status)

        // Send ClientboundAddPlayerPacket to all players
        val clientboundAddPlayerPacketClass =
            getNMSClass("net.minecraft.network.protocol.game.ClientboundAddPlayerPacket")
        val playerClassName = getNMSClass("net.minecraft.world.entity.player.Player")
        // ServerPlayer is subclass of Player
        val clientboundAddPlayerPacketConstructor = clientboundAddPlayerPacketClass.getConstructor(playerClassName)

        val packet = clientboundAddPlayerPacketConstructor.newInstance(serverPlayer)
        sendPacket(packet)
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
        val entityPlayerClass = serverPlayer.javaClass
        val connectionFieldName = reflectionRemapper.remapFieldName(entityPlayerClass, "connection")
        val connectionField = Reflector.getField(entityPlayerClass, connectionFieldName)
        return connectionField.get(serverPlayer)
    }

    fun removePlayer() {
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

        // Remove from playerList
        val playerList = getPlayerList(getMinecraftServer())
        val removePlayerMethodName = reflectionRemapper.remapMethodName(
            playerList.javaClass,
            "remove",
            serverPlayer.javaClass
        )
        val removePlayerMethod = playerList.javaClass.getMethod(removePlayerMethodName, serverPlayer.javaClass)
        removePlayerMethod.invoke(playerList, serverPlayer)

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

    private fun getNMSComponent(message: String): Any {
        // Convert a String to an NMS Component
        val componentClass = getNMSClass("net.minecraft.network.chat.Component")
        val literalName = reflectionRemapper.remapMethodName(componentClass, "literal", String::class.java)
        val componentMethod = checkNotNull(Reflector.getMethod(componentClass, literalName, String::class.java))
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

    fun attack(bukkitEntity: Entity) {
        bukkitPlayer!!.attack(bukkitEntity)
    }

    fun doBlocking() {
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

        val startUsingItemMethod =
            checkNotNull(Reflector.getMethod(livingEntityClass, startUsingItemMethodName, interactionHandClass))
        Reflector.invokeMethod<Any>(startUsingItemMethod, serverPlayer, mainHand)

        // Manually set useItemRemaining field to simulate blocking
        val useItemRemainingFieldName = reflectionRemapper.remapFieldName(livingEntityClass, "useItemRemaining")
        val useItemRemainingField = livingEntityClass.getDeclaredField(useItemRemainingFieldName)
        useItemRemainingField.isAccessible = true
        useItemRemainingField.setInt(serverPlayer, 200)
    }
}