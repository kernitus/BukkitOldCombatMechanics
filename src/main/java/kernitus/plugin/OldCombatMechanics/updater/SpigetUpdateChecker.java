/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.updater;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.List;

/**
 * Checks <a href="https://spiget.org">Spiget</a> for updates.
 */
public class SpigetUpdateChecker {

    private static final String USER_AGENT = "OldCombatMechanics";
    private static final String VERSIONS_URL = "https://api.spiget.org/v2/resources/19510/versions?size=15000";
    private static final String UPDATES_URL = "https://api.spiget.org/v2/resources/19510/updates?size=15000";
    private static final String UPDATE_URL = "https://www.spigotmc.org/resources/oldcombatmechanics.19510/update?update=";
    private String latestVersion = "";

    /**
     * Returns whether an update is available.
     *
     * @return true if an update is available
     */
    public boolean isUpdateAvailable() {
        try {
            final List<VersionPojo> versions = getVersions(VERSIONS_URL);

            if (versions.isEmpty()) return false;

            final VersionPojo currentVersion = versions.get(versions.size() - 1);
            latestVersion = currentVersion.getName();

            return VersionChecker.shouldUpdate(latestVersion);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Returns the URL for the update.
     *
     * @return URL for the update
     */
    public String getUpdateURL() {
        try {
            final List<VersionPojo> versions = getVersions(UPDATES_URL);

            if (versions.isEmpty()) return "Error getting update URL";

            final VersionPojo currentVersion = versions.get(versions.size() - 1);

            return UPDATE_URL + currentVersion.getId();
        } catch (Exception e) {
            return "Error getting update URL";
        }
    }

    /**
     * Returns the latest found version. Only populated after a call to {@link #isUpdateAvailable()}.
     *
     * @return the latest found version
     */
    public String getLatestVersion() {
        return latestVersion;
    }

    /**
     * Returns all versions.
     *
     * @param urlString the url to read the json from
     * @return a list with all found versions
     */
    private List<VersionPojo> getVersions(String urlString) {
        try {
            final InputStreamReader reader = fetchPage(urlString);

            final Type pojoType = new TypeToken<List<VersionPojo>>() {}.getType();

            final List<VersionPojo> parsedVersions = new Gson().fromJson(reader, pojoType);

            if (parsedVersions == null) {
                System.err.println("JSON was at EOF when checking for spiget updates!");
                return Collections.emptyList();
            }

            return parsedVersions;
        } catch (JsonSyntaxException | IOException e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    private InputStreamReader fetchPage(String urlString) throws IOException {
        final URL url = new URL(urlString);
        final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.addRequestProperty("User-Agent", USER_AGENT);

        final InputStream inputStream = connection.getInputStream();
        return new InputStreamReader(inputStream);
    }

    /**
     * A pojo for a version returned by Spiget.
     */
    private static class VersionPojo {
        // Created by GSON
        @SuppressWarnings("unused")
        private String name;
        @SuppressWarnings("unused")
        private String id;

        /**
         * The name. Might be null, if this was an update request.
         *
         * @return the name of this version
         */
        String getName() {
            return name;
        }

        /**
         * The id of this version.
         *
         * @return the id of this version
         */
        String getId() {
            return id;
        }
    }

}
