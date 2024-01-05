/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics.utilities.storage;

import org.bson.Document;

public class PlayerData {
    private String modeset;

    public PlayerData() {
        // Default constructor
    }

    // Getters and setters for each field
    public String getModeset() {
        return modeset;
    }

    public void setModeset(String modeset) {
        this.modeset = modeset;
    }

    public static PlayerData fromDocument(Document doc) {
        final PlayerData playerData = new PlayerData();
        playerData.setModeset(doc.getString("modeset"));
        // Set other fields from the document
        return playerData;
    }
}
