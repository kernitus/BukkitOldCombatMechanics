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

package kernitus.plugin.OldCombatMechanics.utilities.storage;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import org.bson.*;
import org.bson.codecs.*;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.io.BasicOutputBuffer;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

/**
 * Stores data associated with players to disk, persisting across server restarts.
 */
public class PlayerStorage {

    private static OCMMain plugin;
    private static Path dataFilePath;
    private static DocumentCodec documentCodec;
    private static Document data;
    private static final AtomicReference<BukkitTask> saveTask = new AtomicReference<>();
    private static CodecRegistry codecRegistry;

    public static void initialise(OCMMain plugin) {
        PlayerStorage.plugin = plugin;
        dataFilePath = Paths.get(plugin.getDataFolder() + File.separator + "players.bson");

        codecRegistry = CodecRegistries.fromRegistries(
                CodecRegistries.fromCodecs(new DocumentCodec()), // Explicitly provide a DocumentCodec
                CodecRegistries.fromCodecs(new PlayerDataCodec()),
                CodecRegistries.fromProviders(new BsonValueCodecProvider(), new ValueCodecProvider()) // For BSON values
        );

        PlayerStorage.documentCodec = new DocumentCodec(codecRegistry);

        data = loadData();

        saveTask.set(null);
    }

    private static Document loadData() {
        if (Files.notExists(dataFilePath)) return new Document();

        try {
            byte[] data = Files.readAllBytes(dataFilePath);
            final BsonReader reader = new BsonBinaryReader(ByteBuffer.wrap(data));
            return documentCodec.decode(reader, DecoderContext.builder().build());
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Error loading player data", e);
        }
        return new Document();
    }

    public static void scheduleSave() {
        // Schedule a task for later, if there isn't one already scheduled
        saveTask.compareAndSet(null,
                Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
                    instantSave();
                    saveTask.set(null);
                }, 2400L) // Save after 2 minutes
        );
    }

    public static void instantSave() {
        final BasicOutputBuffer outputBuffer = new BasicOutputBuffer();
        final BsonWriter writer = new BsonBinaryWriter(outputBuffer);
        documentCodec.encode(writer, data, EncoderContext.builder().isEncodingCollectibleDocument(true).build());
        writer.flush();

        try {
            Files.write(dataFilePath, outputBuffer.toByteArray());
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Error saving player data", e);
        } finally {
            outputBuffer.close();
        }
    }

    public static PlayerData getPlayerData(UUID uuid) {
        final Document playerDoc = (Document) data.get(uuid.toString());
        if (playerDoc == null) {
            final PlayerData playerData = new PlayerData();
            setPlayerData(uuid, playerData);
            scheduleSave();
            return playerData;
        }
        final BsonDocument bsonDocument = new BsonDocumentWrapper<>(playerDoc, documentCodec);
        return codecRegistry.get(PlayerData.class).decode(bsonDocument.asBsonReader(), DecoderContext.builder().build());
    }

    public static void setPlayerData(UUID uuid, PlayerData playerData) {
        // Create a BsonDocumentWriter to hold the encoded data
        BsonDocumentWriter writer = new BsonDocumentWriter(new BsonDocument());

        // Get the PlayerDataCodec from the CodecRegistry
        PlayerDataCodec playerDataCodec = (PlayerDataCodec) codecRegistry.get(PlayerData.class);

        // Encode the PlayerData object to the writer
        playerDataCodec.encode(writer, playerData, EncoderContext.builder().isEncodingCollectibleDocument(true).build());

        // Retrieve the BsonDocument
        BsonDocument bsonDocument = writer.getDocument();

        // Convert the BsonDocument to a Document
        Document document = new Document();
        bsonDocument.forEach((key, value) -> document.put(key, value.isDocument() ? new Document(value.asDocument()) : value));

        // Put the Document into your data map
        data.put(uuid.toString(), document);
    }
}