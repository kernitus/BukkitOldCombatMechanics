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
                // Override testFinished to capture each test result
                override suspend fun testFinished(testCase: TestCase, result: TestResult) {
                    val testName = testCase.name.testName
                    when (result) {
                        is TestResult.Success -> println("Test '$testName' passed.")
                        is TestResult.Failure -> {
                            println("Test '$testName' failed with exception: ${result.errorOrNull?.message}")
                            hasFailures = true
                        }

                        is TestResult.Ignored -> println("Test '$testName' was ignored.")
                        is TestResult.Error -> println("ERROR")
                    }
                }

                // Optionally override testStarted if you want to log when a test starts
                override suspend fun testStarted(testCase: TestCase) {
                    println("Starting test '${testCase.name.testName}'")
                }

                // Override engineFinished to print a summary after all tests
                override suspend fun engineFinished(t: List<Throwable>) {
                    if (t.isNotEmpty() || hasFailures) {
                        println("Test run completed with failures.")
                        t.forEach { throwable ->
                            println("Engine error: ${throwable.message}")
                        }
                    } else {
                        println("All tests passed successfully.")
                    }
                    Bukkit.shutdown()
                }
            }

            // Run tests using TestEngineLauncher with Spec instances
            TestEngineLauncher(listener).withSpecs(InGameTesterIntegrationTest(this@OCMTestMain)).launch()
        })
    }
}
