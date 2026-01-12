/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics

import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.logging.Level

object TestResultWriter {
    @JvmStatic
    fun writeAndShutdown(plugin: JavaPlugin, success: Boolean, error: Throwable? = null) {
        try {
            val resultFile = File(plugin.dataFolder, "test-results.txt")
            resultFile.parentFile.mkdirs()
            resultFile.writeText(if (success) "PASS" else "FAIL")
            plugin.logger.info("Test result written to ${resultFile.absolutePath}")
        } catch (e: Exception) {
            plugin.logger.log(Level.SEVERE, "Failed to write test results file.", e)
        }

        if (error != null) {
            plugin.logger.log(Level.SEVERE, "Integration tests failed.", error)
        }

        Bukkit.shutdown()
    }

    @JvmStatic
    fun writeFailureSummary(plugin: JavaPlugin, lines: List<String>) {
        try {
            val file = File(plugin.dataFolder, "test-failures.txt")
            file.parentFile.mkdirs()
            file.writeText(lines.joinToString(separator = "\n", postfix = if (lines.isEmpty()) "" else "\n"))
        } catch (e: Exception) {
            plugin.logger.log(Level.SEVERE, "Failed to write test failures file.", e)
        }
    }
}
