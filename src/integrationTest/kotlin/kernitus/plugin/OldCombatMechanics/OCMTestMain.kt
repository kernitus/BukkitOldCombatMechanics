package kernitus.plugin.OldCombatMechanics

import io.kotest.common.KotestInternal
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.TestEngineLauncher
import io.kotest.engine.listener.AbstractTestEngineListener
import kotlinx.coroutines.runBlocking
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.plugin.java.JavaPlugin

class OCMTestMain : JavaPlugin() {
    @OptIn(KotestInternal::class)
    override fun onEnable() {
        println("Enabled OCMTest plugin")
        // Create a CoroutineScope to handle suspending functions

        // Variable to track if any tests failed
        var hasFailures = false

        // Implement a test listener to capture test results
        val listener = object : AbstractTestEngineListener() {
            override suspend fun engineFinished(t: List<Throwable>) {
                if (t.isNotEmpty()) {
                    hasFailures = true
                }
            }
        }

        // Run tests using TestEngineLauncher with Spec instances
        TestEngineLauncher(listener).withSpecs(InGameTesterIntegrationTest(this@OCMTestMain)).launch()

        // After tests complete, handle the results on the main server thread
        if (hasFailures) {
            logger.severe("Integration tests failed.")
            // Optionally, you can write to a file or take other actions
        } else {
            logger.info("Integration tests passed.")
        }

        // Shutdown the server if desired
        //Bukkit.shutdown()
    }
}

class InGameTesterIntegrationTest(private val plugin: JavaPlugin) : StringSpec({

    "test in-game functionality" {
        // Access Bukkit API directly
        val server = Bukkit.getServer()
        // Your test logic and assertions here
        val tester = InGameTester(plugin)

        runBlocking {
            Bukkit.getScheduler().scheduleSyncDelayedTask(plugin) {
                try {
                    val world = server.getWorld("world")
                    tester.performTests(server.consoleSender, Location(world, 0.0, 100.0, 0.0))
                } catch (e: Exception) {
                    plugin.logger.severe("ERROR")
                    e.printStackTrace()
                }
                Bukkit.shutdown()
            }
        }
    }
})
