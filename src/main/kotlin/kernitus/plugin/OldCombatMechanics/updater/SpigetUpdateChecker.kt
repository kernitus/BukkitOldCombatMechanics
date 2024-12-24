/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.updater

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import kernitus.plugin.OldCombatMechanics.utilities.Messenger
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.channels.Channels

/**
 * Checks [Spiget](https://spiget.org) for updates.
 */
class SpigetUpdateChecker {
    /**
     * Returns the latest found version. Only populated after a call to [.isUpdateAvailable].
     *
     * @return the latest found version
     */
    var latestVersion: String? = ""
        private set

    val isUpdateAvailable: Boolean
        /**
         * Returns whether an update is available.
         *
         * @return true if an update is available
         */
        get() {
            try {
                val versions = getVersions(VERSIONS_URL)

                if (versions.isEmpty()) return false

                val currentVersion = versions[versions.size - 1]
                latestVersion = currentVersion.name

                val versionToCheck = latestVersion ?: return false

                return VersionChecker.shouldUpdate(versionToCheck)
            } catch (e: Exception) {
                e.printStackTrace()
                return false
            }
        }

    val updateURL: String
        /**
         * Returns the URL for the update.
         *
         * @return URL for the update
         */
        get() {
            try {
                val versions = getVersions(UPDATES_URL)

                if (versions.isEmpty()) return "Error getting update URL"

                val currentVersion = versions[versions.size - 1]

                return UPDATE_URL + currentVersion.id
            } catch (e: Exception) {
                return "Error getting update URL"
            }
        }

    /**
     * Downloads the latest version of the plugin to the specified location.
     *
     * @param updateFolderFile The location of the server's plugin update folder
     * @param fileName         The name of the JAR file to be updated
     * @return Whether the file was downloaded successfully or not
     */
    fun downloadLatestVersion(updateFolderFile: File, fileName: String): Boolean {
        updateFolderFile.mkdirs() // Create all parent directories if required
        val downloadFile = File(updateFolderFile, fileName)

        try {
            val connection = URL(DOWNLOAD_URL).openConnection() as HttpURLConnection
            connection.addRequestProperty("User-Agent", USER_AGENT)

            try {
                FileOutputStream(downloadFile).use { fileOutputStream ->
                    Channels.newChannel(connection.inputStream).use { readableByteChannel ->
                        fileOutputStream.channel.use { fileChannel ->
                            // Use NIO for better performance
                            fileChannel.transferFrom(readableByteChannel, 0, Long.MAX_VALUE)
                        }
                    }
                }
            } catch (e: Exception) {
                downloadFile.delete() // Remove downloaded file is something went wrong
                throw RuntimeException(e) // Rethrow exception to catch in outer scope
            }
        } catch (e: IOException) {
            Messenger.warn("Tried to download plugin update, but an error occurred")
            e.printStackTrace()
            return false
        }
        return true
    }

    /**
     * Returns all versions.
     *
     * @param urlString the url to read the json from
     * @return a list with all found versions
     */
    private fun getVersions(urlString: String): List<SpigetVersion> {
        try {
            val reader = fetchPage(urlString)

            val pojoType = object : TypeToken<List<SpigetVersion?>?>() {}.type

            val parsedVersions = Gson().fromJson<List<SpigetVersion>>(reader, pojoType)

            if (parsedVersions == null) {
                System.err.println("JSON was at EOF when checking for spiget updates!")
                return emptyList()
            }

            return parsedVersions
        } catch (e: JsonSyntaxException) {
            e.printStackTrace()
            return emptyList()
        } catch (e: IOException) {
            e.printStackTrace()
            return emptyList()
        }
    }

    @Throws(IOException::class)
    private fun fetchPage(urlString: String): InputStreamReader {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.addRequestProperty("User-Agent", USER_AGENT)
        connection.connectTimeout = 10000 // 10 seconds
        connection.readTimeout = 10000 // 10 seconds

        try {
            val status = connection.responseCode
            if (status == HttpURLConnection.HTTP_OK) {
                val inputStream = connection.inputStream
                return InputStreamReader(inputStream)
            } else {
                // Log error or handle other status codes appropriately
                throw IOException("Server returned non-OK status: $status")
            }
        } catch (e: IOException) {
            // Log exception with as much detail as possible
            throw e
        }
    }

    /**
     * A data class for a version returned by Spiget.
     *
     * @property name The name of the version. Might be null if this was an update request.
     * @property id The ID of the version.
     */
    private data class SpigetVersion(
        val name: String? = null, val id: String? = null
    )

    companion object {
        private const val USER_AGENT = "OldCombatMechanics"
        private const val VERSIONS_URL = "https://api.spiget.org/v2/resources/19510/versions?size=15000"
        private const val UPDATES_URL = "https://api.spiget.org/v2/resources/19510/updates?size=15000"
        private const val UPDATE_URL = "https://www.spigotmc.org/resources/oldcombatmechanics.19510/update?update="
        private const val DOWNLOAD_URL = "https://api.spiget.org/v2/resources/19510/download"
    }
}
