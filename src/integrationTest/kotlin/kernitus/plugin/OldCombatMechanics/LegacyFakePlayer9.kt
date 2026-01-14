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
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.player.AsyncPlayerPreLoginEvent
import org.bukkit.event.player.PlayerPreLoginEvent
import org.bukkit.plugin.java.JavaPlugin
import java.net.InetAddress
import java.util.*

/**
 * Fake player for 1.9.x (v1_9_R2). Uses PlayerList#a(NetworkManager, EntityPlayer)
 * so the server treats it like a real online player (damage/knockback events).
 */
internal class LegacyFakePlayer9(
    private val plugin: JavaPlugin,
    val uuid: UUID,
    val name: String
) {
    private val cbVersion: String = Bukkit.getServer().javaClass.`package`.name.substringAfterLast('.')
    var entityPlayer: Any? = null
        private set
    var bukkitPlayer: Player? = null
        private set

    private fun nms(simple: String): Class<*> =
        Class.forName("net.minecraft.server.$cbVersion.$simple", true, Bukkit.getServer().javaClass.classLoader)
    private fun craft(simple: String): Class<*> =
        Class.forName("org.bukkit.craftbukkit.$cbVersion.$simple", true, Bukkit.getServer().javaClass.classLoader)

    fun spawn(location: Location) {
        val world = location.world ?: error("Location has no world")
        val craftWorld = craft("CraftWorld").cast(world)
        val worldServer = craftWorld.javaClass.getMethod("getHandle").invoke(craftWorld)

        val craftServer = craft("CraftServer").cast(Bukkit.getServer())
        val mcServer = craftServer.javaClass.getMethod("getServer").invoke(craftServer)
        val playerList = mcServer.javaClass.getMethod("getPlayerList").invoke(mcServer)

        val ep = createEntityPlayer(mcServer, worldServer)
        entityPlayer = ep
        bukkitPlayer = ep.javaClass.getMethod("getBukkitEntity").invoke(ep) as Player

        // Ensure NMS alive/dead flags are sane so Bukkit sees the player as valid.
        runCatching {
            val deadField = ep.javaClass.superclass.getDeclaredField("dead")
            deadField.isAccessible = true
            deadField.setBoolean(ep, false)
        }
        runCatching {
            val health = ep.javaClass.getMethod("setHealth", Float::class.javaPrimitiveType)
            health.invoke(ep, 20.0f)
        }

        firePreLogin()
        setPosition(ep, location)
        setGameMode(ep)
        setVulnerability(ep, false)
        setMetaDefaults(ep)

        val nm = setupConnection(ep, mcServer)

        // Run the real join pipeline
        playerList.javaClass.getMethod("a", nms("NetworkManager"), nms("EntityPlayer"))
            .apply { isAccessible = true }
            .invoke(playerList, nm, ep)

        // Clean up the duplicate UUID warning: ensure we don't re-add if PlayerList already did
        runCatching {
            val playersField = playerList.javaClass.getDeclaredField("players")
            playersField.isAccessible = true
            val list = playersField.get(playerList) as MutableList<Any>
            if (!list.contains(ep)) list.add(ep)
        }

        // Ensure CraftServer maps see the player (for Bukkit.getPlayer)
        updateCraftMaps(craftServer, bukkitPlayer!!)

        // Force entity into world lists (safety)
        runCatching { worldServer.javaClass.getMethod("addEntity", nms("Entity")).invoke(worldServer, ep) }

        // Ensure chunk tracking in case join path skipped it
        runCatching {
            val pcm = worldServer.javaClass.getMethod("getPlayerChunkMap").invoke(worldServer)
            pcm.javaClass.methods.firstOrNull { it.name == "addPlayer" && it.parameterCount == 1 }?.invoke(pcm, ep)
            pcm.javaClass.methods.firstOrNull { it.name == "movePlayer" && it.parameterCount == 1 }?.invoke(pcm, ep)
        }

        // Broadcast spawn packets (ADD_PLAYER + NamedEntitySpawn) to online players
        runCatching {
            val packetInfoClass = nms("PacketPlayOutPlayerInfo")
            val enumInfo = packetInfoClass.declaredClasses.first { it.simpleName.contains("EnumPlayerInfoAction") }
            val addPlayer = enumInfo.getField("ADD_PLAYER").get(null)
            val epArray = java.lang.reflect.Array.newInstance(nms("EntityPlayer"), 1).apply {
                java.lang.reflect.Array.set(this, 0, ep)
            }
            val infoCtor = packetInfoClass.getConstructor(enumInfo, epArray.javaClass)
            val infoPacket = infoCtor.newInstance(addPlayer, epArray)

            val spawnPacketClass = nms("PacketPlayOutNamedEntitySpawn")
            val spawnCtor = spawnPacketClass.getConstructor(nms("EntityHuman"))
            val spawnPacket = spawnCtor.newInstance(ep)

            val attrPacketClass = nms("PacketPlayOutUpdateAttributes")
            val attrCtor = attrPacketClass.getConstructor(nms("EntityLiving"), java.util.Collection::class.java)
            val attrMethod = ep.javaClass.methods.firstOrNull { it.name == "getAttributeInstance" }
            val attributes = java.util.ArrayList<Any>()
            // health and attack damage attributes if available
            runCatching {
                val generic = Class.forName("org.bukkit.attribute.Attribute")
                val healthEnum = generic.getField("GENERIC_MAX_HEALTH").get(null)
                val dmgEnum = generic.getField("GENERIC_ATTACK_DAMAGE").get(null)
                val attrBase = ep.javaClass.methods.firstOrNull { it.name == "getAttributeInstance" && it.parameterTypes.size == 1 }
                val healthInst = attrBase?.invoke(ep, nms("GenericAttributes").getField("MAX_HEALTH").get(null))
                val dmgInst = attrBase?.invoke(ep, nms("GenericAttributes").getField("ATTACK_DAMAGE").get(null))
                if (healthInst != null) attributes.add(healthInst)
                if (dmgInst != null) attributes.add(dmgInst)
            }
            val attrPacket = runCatching { attrCtor.newInstance(ep, attributes) }.getOrNull()

            val heldSlotClass = nms("PacketPlayOutHeldItemSlot")
            val heldSlotPacket = runCatching { heldSlotClass.getConstructor(Int::class.javaPrimitiveType).newInstance(0) }.getOrNull()

            val windowItemsClass = nms("PacketPlayOutWindowItems")
            val inventory = ep.javaClass.getField("inventory").get(ep)
            val getContents = inventory.javaClass.methods.firstOrNull { it.name == "getContents" && it.parameterCount == 0 }
            val contents = runCatching { getContents?.invoke(inventory) as? Array<Any> }.getOrNull()
            val windowPacket = runCatching { windowItemsClass.constructors.first().newInstance(0, listOf(*contents ?: emptyArray())) }.getOrNull()

            val healthClass = nms("PacketPlayOutUpdateHealth")
            val healthPacket = runCatching {
                val health = ep.javaClass.getMethod("getHealth").invoke(ep) as Float
                val food = ep.javaClass.getMethod("getFoodData").invoke(ep)
                val foodLevel = food.javaClass.getMethod("getFoodLevel").invoke(food) as Int
                val saturation = food.javaClass.getMethod("getSaturationLevel").invoke(food) as Float
                healthClass.getConstructor(Float::class.javaPrimitiveType, Int::class.javaPrimitiveType, Float::class.javaPrimitiveType)
                    .newInstance(health, foodLevel, saturation)
            }.getOrNull()

            val metaClass = nms("PacketPlayOutEntityMetadata")
            val metaCtor = metaClass.getConstructor(Int::class.javaPrimitiveType, nms("DataWatcher"), Boolean::class.javaPrimitiveType)
            val dataWatcher = ep.javaClass.getMethod("getDataWatcher").invoke(ep)
            val metaPacket = metaCtor.newInstance(ep.javaClass.getMethod("getId").invoke(ep) as Int, dataWatcher, true)

            val animClass = nms("PacketPlayOutAnimation")
            val swingPacket = runCatching { animClass.getConstructor(nms("Entity"), Int::class.javaPrimitiveType).newInstance(ep, 0) }.getOrNull()

            Bukkit.getOnlinePlayers().forEach { viewer ->
                val handle = viewer.javaClass.getMethod("getHandle").invoke(viewer)
                val conn = handle.javaClass.getField("playerConnection").get(handle)
                val send = conn.javaClass.methods.first { it.name == "sendPacket" && it.parameterTypes.size == 1 }
                send.invoke(conn, infoPacket)
                send.invoke(conn, spawnPacket)
                if (attrPacket != null) send.invoke(conn, attrPacket)
                if (heldSlotPacket != null) send.invoke(conn, heldSlotPacket)
                if (windowPacket != null) send.invoke(conn, windowPacket)
                if (healthPacket != null) send.invoke(conn, healthPacket)
                send.invoke(conn, metaPacket)
                if (swingPacket != null) send.invoke(conn, swingPacket)
            }
        }

        // Tick task to keep status/effects progressing
        Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, Runnable {
            runCatching {
                // playerTick is "m" in 1.9; fall back to "n" if obf differs
                val tick = ep.javaClass.methods.firstOrNull { it.name == "m" && it.parameterCount == 0 }
                    ?: ep.javaClass.methods.firstOrNull { it.name == "n" && it.parameterCount == 0 }
                    ?: ep.javaClass.methods.firstOrNull { it.name == "playerTick" && it.parameterCount == 0 }
                tick?.invoke(ep)
                // Keep entity alive/valid flags cleared so Bukkit reports the player as valid
                runCatching {
                    val deadField = ep.javaClass.superclass.getDeclaredField("dead")
                    deadField.isAccessible = true
                    deadField.setBoolean(ep, false)
                }
                runCatching {
                    val craftEntity = Class.forName("org.bukkit.craftbukkit.$cbVersion.entity.CraftEntity")
                    val validField = craftEntity.getDeclaredField("valid")
                    validField.isAccessible = true
                    validField.setBoolean(bukkitPlayer, true)
                }
            }
            // keep chunk tracking fresh
            runCatching {
                val worldServer = ep.javaClass.getMethod("getWorld").invoke(ep)
                val pcm = worldServer.javaClass.getMethod("getPlayerChunkMap").invoke(worldServer)
                pcm.javaClass.methods.firstOrNull { it.name == "movePlayer" && it.parameterCount == 1 }?.invoke(pcm, ep)
            }
            // Apply queued effects/fire ticks
            runCatching {
                // Force entity base tick for fire/water checks
                ep.javaClass.methods.firstOrNull { it.name == "ae" && it.parameterCount == 0 } // baseTick in 1.9 obf
                    ?.invoke(ep)
            }
            // Ensure water extinguishes burning for fake players on legacy
            runCatching {
                val bp = bukkitPlayer
                if (bp != null && bp.fireTicks > 0 && bp.location.block.isLiquid) {
                    bp.fireTicks = 0
                }
            }
        }, 1L, 1L)
    }

    fun removePlayer() {
        val ep = entityPlayer ?: return
        val bp = bukkitPlayer ?: return
        val craftServer = craft("CraftServer").cast(Bukkit.getServer())
        val mcServer = craftServer.javaClass.getMethod("getServer").invoke(craftServer)
        val playerList = mcServer.javaClass.getMethod("getPlayerList").invoke(mcServer)

        bp.kickPlayer("§e$name left the game")
        runCatching {
            playerList.javaClass.getMethod("disconnect", nms("EntityPlayer")).invoke(playerList, ep)
        }.onFailure {
            runCatching { playerList.javaClass.getMethod("remove", nms("EntityPlayer")).invoke(playerList, ep) }
        }
        runCatching {
            val pcm = getPlayerChunkMap(ep)
            pcm?.javaClass?.methods?.firstOrNull { it.name == "removePlayer" && it.parameterCount == 1 }?.invoke(pcm, ep)
        }
    }

    fun getConnection(serverPlayer: Any): Any {
        val field = serverPlayer.javaClass.getField("playerConnection")
        return field.get(serverPlayer)
    }

    private fun createEntityPlayer(mcServer: Any, worldServer: Any): Any {
        val epClass = nms("EntityPlayer")
        val pimClass = nms("PlayerInteractManager")
        // PlayerInteractManager(World | WorldServer)
        val pimCtor = pimClass.constructors.firstOrNull { ctor ->
            ctor.parameterTypes.size == 1 && ctor.parameterTypes[0].isAssignableFrom(worldServer.javaClass)
        } ?: pimClass.constructors.first()
        val pim = pimCtor.newInstance(worldServer)
        val gp = GameProfile(uuid, name)

        val ctor = epClass.constructors.firstOrNull { ctor ->
            val p = ctor.parameterTypes
            p.size == 4 &&
                p[0].isAssignableFrom(mcServer.javaClass) &&
                p[1].isAssignableFrom(worldServer.javaClass) &&
                p[2].isAssignableFrom(GameProfile::class.java) &&
                p[3].isAssignableFrom(pimClass)
        } ?: epClass.constructors.first()

        return ctor.newInstance(mcServer, worldServer, gp, pim)
    }

    private fun setupConnection(ep: Any, mcServer: Any): Any {
        val nmClass = nms("NetworkManager")
        val dirClass = nms("EnumProtocolDirection")
        val clientbound = dirClass.getField("CLIENTBOUND").get(null)
        val nm = nmClass.getConstructor(dirClass).newInstance(clientbound)
        // Dummy channel with predictable address
        val remote = java.net.InetSocketAddress("127.0.0.1", 25565)
        val channel = EmbeddedChannel(ChannelInboundHandlerAdapter())
        val pipeline = channel.pipeline()
        if (pipeline.get("decoder") == null) {
            pipeline.addLast("decoder", ChannelInboundHandlerAdapter())
        }
        if (pipeline.get("encoder") == null) {
            pipeline.addLast("encoder", ChannelOutboundHandlerAdapter())
        }
        nmClass.getField("channel").set(nm, channel)
        runCatching { nmClass.getField("socketAddress").set(nm, remote) }

        val pcClass = nms("PlayerConnection")
        val pc = pcClass.getConstructor(nms("MinecraftServer"), nmClass, nms("EntityPlayer"))
            .newInstance(mcServer, nm, ep)
        ep.javaClass.getField("playerConnection").set(ep, pc)
        runCatching {
            val setListener = nmClass.methods.firstOrNull { it.name == "setPacketListener" && it.parameterCount == 1 }
            setListener?.invoke(nm, pc)
        }
        runCatching {
            val enumProtocol = nms("EnumProtocol")
            val play = enumProtocol.getField("PLAY").get(null)
            nmClass.methods.firstOrNull { it.name == "a" && it.parameterTypes.singleOrNull() == enumProtocol }
                ?.invoke(nm, play)
        }
        runCatching {
            nmClass.fields.firstOrNull { it.name == "isPending" }?.setBoolean(nm, false)
        }
        return nm
    }

    private fun setPosition(ep: Any, loc: Location) {
        ep.javaClass.getMethod(
            "setPositionRotation",
            Double::class.javaPrimitiveType,
            Double::class.javaPrimitiveType,
            Double::class.javaPrimitiveType,
            Float::class.javaPrimitiveType,
            Float::class.javaPrimitiveType
        ).invoke(ep, loc.x, loc.y, loc.z, loc.yaw, loc.pitch)
    }

    private fun setGameMode(ep: Any) {
        val gmName = when (Bukkit.getDefaultGameMode()) {
            GameMode.CREATIVE -> "CREATIVE"
            GameMode.ADVENTURE -> "ADVENTURE"
            GameMode.SPECTATOR -> "SPECTATOR"
            else -> "SURVIVAL"
        }
        val enumGMClass = nms("WorldSettings\$EnumGamemode")
        val enumGM = enumGMClass.getField(gmName).get(null)
        val pim = ep.javaClass.getField("playerInteractManager").get(ep)
        // prefer setGameMode / b(EnumGamemode)
        val method = pim.javaClass.methods.firstOrNull { it.name in listOf("setGameMode", "b") && it.parameterTypes.size == 1 }
            ?: pim.javaClass.getMethod("b", enumGMClass)
        method.isAccessible = true
        method.invoke(pim, enumGM)
    }

    private fun setVulnerability(ep: Any, invulnerable: Boolean) {
        runCatching { ep.javaClass.getField("invulnerableTicks").setInt(ep, if (invulnerable) 20 else 0) }
        runCatching { ep.javaClass.getField("noDamageTicks").setInt(ep, if (invulnerable) 20 else 0) }
        runCatching {
            val abilities = ep.javaClass.getField("abilities").get(ep)
            val flags = mapOf(
                "isInvulnerable" to invulnerable,
                "isFlying" to false,
                "mayfly" to false,
                "canInstantlyBuild" to false
            )
            flags.forEach { (k, v) ->
                runCatching { abilities.javaClass.getField(k).setBoolean(abilities, v) }
            }
            ep.javaClass.getMethod("updateAbilities").invoke(ep)
        }
    }

    private fun setMetaDefaults(ep: Any) {
        runCatching {
            val dw = ep.javaClass.getMethod("getDataWatcher").invoke(ep)
            val serializerRegistry = nms("DataWatcherRegistry")
            val byteSerializer = serializerRegistry.getField("a").get(null)
            val floatSerializer = serializerRegistry.getField("c").get(null)

            val dwoClass = nms("DataWatcherObject")
            val dwoCtor = dwoClass.getConstructor(Int::class.javaPrimitiveType, nms("DataWatcherSerializer"))

            val set = dw.javaClass.methods.first { it.name == "set" && it.parameterTypes.size == 2 }

            // flag byte (index 0) -> 0
            val flag0 = dwoCtor.newInstance(0, byteSerializer)
            set.invoke(dw, flag0, 0.toByte())

            // health (index 6) -> 20f
            val healthObj = dwoCtor.newInstance(6, floatSerializer)
            set.invoke(dw, healthObj, 20.0f)
        }
    }

    private fun firePreLogin() {
        val addr = InetAddress.getLoopbackAddress()
        val async = AsyncPlayerPreLoginEvent(name, addr, uuid)
        val sync = PlayerPreLoginEvent(name, addr, uuid)
        Thread { Bukkit.getPluginManager().callEvent(async) }.start()
        Bukkit.getPluginManager().callEvent(sync)
    }

    private fun getChunkProvider(ep: Any): Any? = runCatching {
        val worldServer = ep.javaClass.getMethod("getWorld").invoke(ep)
        worldServer.javaClass.getMethod("getChunkProviderServer").invoke(worldServer)
    }.getOrNull()

    private fun getPlayerChunkMap(ep: Any): Any? = runCatching {
        val worldServer = ep.javaClass.getMethod("getWorld").invoke(ep)
        worldServer.javaClass.getMethod("getPlayerChunkMap").invoke(worldServer)
    }.getOrNull()

    private fun updateCraftMaps(craftServer: Any, player: Player) {
        runCatching {
            val playersField = craftServer.javaClass.getDeclaredField("players")
            playersField.isAccessible = true
            val map = playersField.get(craftServer) as MutableMap<String, Player>
            map[player.name.lowercase(Locale.getDefault())] = player
        }
        runCatching {
            val uuidField = craftServer.javaClass.getDeclaredField("playersByUUID")
            uuidField.isAccessible = true
            val map = uuidField.get(craftServer) as MutableMap<UUID, Player>
            map[player.uniqueId] = player
        }
    }
}
