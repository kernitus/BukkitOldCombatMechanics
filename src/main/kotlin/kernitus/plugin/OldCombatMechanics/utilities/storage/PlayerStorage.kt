/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities.storage

import kernitus.plugin.OldCombatMechanics.OCMMain
import org.bson.*
import org.bson.codecs.*
import org.bson.codecs.configuration.CodecRegistries
import org.bson.codecs.configuration.CodecRegistry
import org.bson.io.BasicOutputBuffer
import org.bukkit.Bukkit
import org.bukkit.scheduler.BukkitTask
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import java.util.logging.Level

/**
 * Stores data associated with players to disk, persisting across server restarts.
 */
object PlayerStorage {
    private var plugin: OCMMain? = null
    private var dataFilePath: Path? = null
    private var documentCodec: DocumentCodec? = null
    private var data: Document? = null
    private val saveTask = AtomicReference<BukkitTask?>()
    private var codecRegistry: CodecRegistry? = null

    fun initialise(plugin: OCMMain) {
        PlayerStorage.plugin = plugin
        dataFilePath = Paths.get(plugin.dataFolder.toString() + File.separator + "players.bson")

        codecRegistry = CodecRegistries.fromRegistries(
            CodecRegistries.fromCodecs(DocumentCodec()),  // Explicitly provide a DocumentCodec
            CodecRegistries.fromCodecs(PlayerDataCodec()),
            CodecRegistries.fromProviders(BsonValueCodecProvider(), ValueCodecProvider()) // For BSON values
        )

        documentCodec = DocumentCodec(codecRegistry)

        data = loadData()

        saveTask.set(null)
    }

    private fun loadData(): Document {
        if (Files.notExists(dataFilePath)) return Document()

        try {
            val data = Files.readAllBytes(dataFilePath)
            val reader: BsonReader = BsonBinaryReader(ByteBuffer.wrap(data))
            return documentCodec!!.decode(reader, DecoderContext.builder().build())
        } catch (e: IOException) {
            plugin!!.logger.log(Level.SEVERE, "Error loading player data", e)
        }
        return Document()
    }

    fun scheduleSave() {
        // Schedule a task for later, if there isn't one already scheduled
        saveTask.compareAndSet(
            null,
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin!!, Runnable {
                instantSave()
                saveTask.set(null)
            }, 2400L) // Save after 2 minutes
        )
    }

    fun instantSave() {
        val outputBuffer = BasicOutputBuffer()
        val writer: BsonWriter = BsonBinaryWriter(outputBuffer)
        documentCodec!!.encode(writer, data, EncoderContext.builder().isEncodingCollectibleDocument(true).build())
        writer.flush()

        try {
            Files.write(dataFilePath, outputBuffer.toByteArray())
        } catch (e: IOException) {
            plugin!!.logger.log(Level.SEVERE, "Error saving player data", e)
        } finally {
            outputBuffer.close()
        }
    }

    @JvmStatic
    fun getPlayerData(uuid: UUID): PlayerData {
        val playerDoc = data!![uuid.toString()] as Document?
        if (playerDoc == null) {
            val playerData = PlayerData()
            setPlayerData(uuid, playerData)
            scheduleSave()
            return playerData
        }
        val bsonDocument: BsonDocument = BsonDocumentWrapper(playerDoc, documentCodec)
        return codecRegistry!!.get(PlayerData::class.java)
            .decode(bsonDocument.asBsonReader(), DecoderContext.builder().build())
    }

    @JvmStatic
    fun setPlayerData(uuid: UUID, playerData: PlayerData) {
        // Create a BsonDocumentWriter to hold the encoded data
        val writer = BsonDocumentWriter(BsonDocument())

        // Get the PlayerDataCodec from the CodecRegistry
        val playerDataCodec = codecRegistry!!.get(PlayerData::class.java) as PlayerDataCodec

        // Encode the PlayerData object to the writer
        playerDataCodec.encode(writer, playerData, EncoderContext.builder().isEncodingCollectibleDocument(true).build())

        // Retrieve the BsonDocument
        val bsonDocument = writer.document

        // Convert the BsonDocument to a Document
        val document = Document()
        bsonDocument.forEach { (key: String, value: BsonValue) ->
            document[key] = if (value.isDocument) Document(value.asDocument()) else value
        }

        // Put the Document into your data map
        data!![uuid.toString()] = document
    }
}