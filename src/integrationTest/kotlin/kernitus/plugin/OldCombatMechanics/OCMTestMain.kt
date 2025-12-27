/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics

import kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import java.lang.reflect.InvocationTargetException
import java.util.logging.Level

class OCMTestMain : JavaPlugin() {
    override fun onEnable() {
        logger.info("Enabled OCMTest plugin")

        // Initialise player data storage
        val ocm = Bukkit.getPluginManager().getPlugin("OldCombatMechanics") as OCMMain
        PlayerStorage.initialise(ocm)

        val javaVersion = detectJavaVersion()
        logger.info("Detected Java $javaVersion for integration tests")

        System.setProperty("kotest.framework.classpath.scanning.autoscan.disable", "true")
        runKotest()
    }

    private fun runKotest() {
        try {
            val runnerClass = Class.forName("kernitus.plugin.OldCombatMechanics.KotestRunner")
            val runMethod = runnerClass.getMethod("run", JavaPlugin::class.java)
            runMethod.invoke(null, this)
        } catch (e: InvocationTargetException) {
            logger.log(Level.SEVERE, "Failed to launch Kotest runner.", e.targetException ?: e)
            TestResultWriter.writeAndShutdown(this, false)
        } catch (e: Throwable) {
            logger.log(Level.SEVERE, "Failed to launch Kotest runner.", e)
            TestResultWriter.writeAndShutdown(this, false)
        }
    }

    private fun detectJavaVersion(): Int {
        val version = System.getProperty("java.specification.version") ?: return 0
        return if (version.startsWith("1.")) {
            version.substringAfter("1.").toIntOrNull() ?: 0
        } else {
            version.toIntOrNull() ?: 0
        }
    }
}
