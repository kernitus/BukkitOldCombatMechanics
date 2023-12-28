/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics.utilities;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import org.bson.*;
import org.bson.codecs.BsonValueCodecProvider;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.DocumentCodec;
import org.bson.codecs.EncoderContext;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.io.BasicOutputBuffer;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Stores data associated with players to disk, persisting across server restarts.
 */
public class PlayerStorage {

    private static OCMMain plugin;
    private static Path dataFilePath;
    private static DocumentCodec documentCodec;
    private static Document data;

    public static void initialise(OCMMain plugin) {
        PlayerStorage.plugin = plugin;
        dataFilePath = Paths.get(plugin.getDataFolder() + File.separator + "players.bson");

        CodecRegistry codecRegistry = CodecRegistries.fromRegistries(
                CodecRegistries.fromCodecs(new DocumentCodec()), // Explicitly provide a DocumentCodec
                CodecRegistries.fromProviders(new BsonValueCodecProvider()) // For BSON values
        );
        PlayerStorage.documentCodec = new DocumentCodec(codecRegistry);

        data = loadData();
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

    public static void saveData() {
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

    public static Document getPlayerData(UUID uuid) {
        Document playerData = (Document) data.get(uuid.toString());
        if (playerData == null){
            playerData = new Document();
            data.put(uuid.toString(), playerData);
        }
        return playerData;
    }

    public static void setPlayerData(UUID uuid, Document playerData) {
        data.put(uuid.toString(), playerData);
    }
}