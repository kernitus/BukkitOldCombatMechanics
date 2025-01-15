package kernitus.plugin.OldCombatMechanics

import io.kotest.common.KotestInternal
import io.kotest.core.config.AbstractProjectConfig
import io.kotest.core.extensions.TestCaseExtension
import io.kotest.core.spec.IsolationMode
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.kotest.engine.TestEngineLauncher
import io.kotest.engine.listener.AbstractTestEngineListener
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

object KotestProjectConfig : AbstractProjectConfig() {
    override val parallelism = 1
    override val isolationMode = IsolationMode.SingleInstance
}


class BukkitMainThreadDispatcher(private val plugin: JavaPlugin) : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        Bukkit.getScheduler().runTask(plugin, block)
    }
}


class MainThreadDispatcherExtension(private val plugin: JavaPlugin) : TestCaseExtension {
    override suspend fun intercept(testCase: TestCase, execute: suspend (TestCase) -> TestResult): TestResult {
        val dispatcher = BukkitMainThreadDispatcher(plugin)
        val newContext = coroutineContext + dispatcher
        return withContext(newContext) {
            execute(testCase)
        }
    }
}

class OCMTestMain : JavaPlugin() {
    @OptIn(KotestInternal::class)
    override fun onEnable() {
        println("Enabled OCMTest plugin")

        // Schedule test asynchronously to avoid deadlock
        // This is because each test is run as a coroutine
        Bukkit.getScheduler().runTaskAsynchronously(this, Runnable {
            // Implement a test listener to capture test results
            var hasFailures = false

            val listener = object : AbstractTestEngineListener() {
                override suspend fun engineFinished(t: List<Throwable>) {
                    if (t.isNotEmpty()) {
                        hasFailures = true
                    }
                }
            }

            // Run tests using TestEngineLauncher with Spec instances
            TestEngineLauncher(listener).withSpecs(InGameTesterIntegrationTest(this@OCMTestMain)).launch()
        })
    }
}

