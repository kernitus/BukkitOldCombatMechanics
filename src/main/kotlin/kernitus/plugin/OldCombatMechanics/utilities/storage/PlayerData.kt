/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities.storage

import org.bson.Document
import java.util.*

class PlayerData {
    var modesetByWorld: MutableMap<UUID, String?> = HashMap()

    fun setModesetForWorld(worldId: UUID, modeset: String?) {
        modesetByWorld[worldId] = modeset
    }

    fun getModesetForWorld(worldId: UUID): String? {
        return modesetByWorld[worldId]
    }

    companion object {
        fun fromDocument(doc: Document): PlayerData {
            val playerData = PlayerData()
            val modesetByWorldDoc = doc["modesetByWorld"] as Document?
            if (modesetByWorldDoc != null) {
                for ((key, value) in modesetByWorldDoc) {
                    val worldId = UUID.fromString(key)
                    val modeset = value as String
                    playerData.setModesetForWorld(worldId, modeset)
                }
            }
            return playerData
        }
    }
}