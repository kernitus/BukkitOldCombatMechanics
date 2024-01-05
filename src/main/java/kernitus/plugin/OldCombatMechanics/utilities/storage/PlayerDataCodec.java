/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics.utilities.storage;

import org.bson.BsonReader;
import org.bson.BsonWriter;
import org.bson.Document;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.DocumentCodec;
import org.bson.codecs.EncoderContext;

import java.util.Map;
import java.util.UUID;

public class PlayerDataCodec implements Codec<PlayerData> {

    @Override
    public void encode(BsonWriter writer, PlayerData value, EncoderContext encoderContext) {
        final Document document = new Document();
        Document modesetByWorldDoc = new Document();
        for (Map.Entry<UUID, String> entry : value.getModesetByWorld().entrySet()) {
            modesetByWorldDoc.put(entry.getKey().toString(), entry.getValue());
        }
        document.put("modesetByWorld", modesetByWorldDoc);
        new DocumentCodec().encode(writer, document, encoderContext);
    }

    @Override
    public Class<PlayerData> getEncoderClass() {
        return PlayerData.class;
    }

    @Override
    public PlayerData decode(BsonReader reader, DecoderContext decoderContext) {
        final Document document = new DocumentCodec().decode(reader, decoderContext);
        return PlayerData.fromDocument(document);
    }
}
