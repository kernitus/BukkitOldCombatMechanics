/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities.storage

import org.bson.BsonReader
import org.bson.BsonWriter
import org.bson.Document
import org.bson.codecs.Codec
import org.bson.codecs.DecoderContext
import org.bson.codecs.DocumentCodec
import org.bson.codecs.EncoderContext

class PlayerDataCodec : Codec<PlayerData> {
    override fun encode(writer: BsonWriter, value: PlayerData, encoderContext: EncoderContext) {
        val document = Document()
        val modesetByWorldDoc = Document()
        for ((key, value1) in value.modesetByWorld) {
            modesetByWorldDoc[key.toString()] = value1
        }
        document["modesetByWorld"] = modesetByWorldDoc
        DocumentCodec().encode(writer, document, encoderContext)
    }

    override fun getEncoderClass(): Class<PlayerData> {
        return PlayerData::class.java
    }

    override fun decode(reader: BsonReader, decoderContext: DecoderContext): PlayerData {
        val document = DocumentCodec().decode(reader, decoderContext)
        return PlayerData.fromDocument(document)
    }
}
