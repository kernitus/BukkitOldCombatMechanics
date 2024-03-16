/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics.utilities.storage;

import org.bson.Document;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerData {
    private Map<UUID, String> modesetByWorld;

    public PlayerData() {
        modesetByWorld = new HashMap<>();
    }

    public Map<UUID, String> getModesetByWorld() {
        return modesetByWorld;
    }

    public void setModesetByWorld(Map<UUID, String> modesetByWorld) {
        this.modesetByWorld = modesetByWorld;
    }

    public void setModesetForWorld(UUID worldId, String modeset) {
        modesetByWorld.put(worldId, modeset);
    }

    public @Nullable String getModesetForWorld(UUID worldId) {
        return modesetByWorld.get(worldId);
    }

    public static PlayerData fromDocument(Document doc) {
        final PlayerData playerData = new PlayerData();
        final Document modesetByWorldDoc = (Document) doc.get("modesetByWorld");
        if (modesetByWorldDoc != null) {
            for (Map.Entry<String, Object> entry : modesetByWorldDoc.entrySet()) {
                UUID worldId = UUID.fromString(entry.getKey());
                String modeset = (String) entry.getValue();
                playerData.setModesetForWorld(worldId, modeset);
            }
        }
        return playerData;
    }
}