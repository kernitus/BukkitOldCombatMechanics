/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics

import io.kotest.common.KotestInternal
import io.kotest.core.config.AbstractProjectConfig
import io.kotest.core.extensions.TestCaseExtension
import io.kotest.core.spec.IsolationMode
import io.kotest.core.test.TestCase
import io.kotest.engine.TestEngineLauncher
import io.kotest.engine.concurrency.SpecExecutionMode
import io.kotest.engine.concurrency.TestExecutionMode
import io.kotest.engine.listener.AbstractTestEngineListener
import io.kotest.engine.test.TestResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

object KotestProjectConfig : AbstractProjectConfig() {
    override val isolationMode = IsolationMode.SingleInstance
    override val specExecutionMode = SpecExecutionMode.Sequential
    override val testExecutionMode = TestExecutionMode.Sequential
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

object KotestRunner {
    @OptIn(KotestInternal::class)
    @JvmStatic
    fun run(plugin: JavaPlugin) {
        // Schedule test asynchronously to avoid deadlock.
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            try {
                var hasFailures = false

                val listener = object : AbstractTestEngineListener() {
                    override suspend fun testFinished(testCase: TestCase, result: TestResult) {
                        val testName = testCase.name.name
                        when {
                            result.isSuccess -> plugin.logger.info("Test '$testName' passed.")
                            result.isIgnored -> plugin.logger.info("Test '$testName' was ignored.")
                            result.isFailure || result.isError -> {
                                val message = result.errorOrNull?.message
                                    ?: result.reasonOrNull
                                    ?: "Unknown failure"
                                plugin.logger.warning("Test '$testName' failed with exception: $message")
                                hasFailures = true
                            }
                        }
                    }

                    override suspend fun testStarted(testCase: TestCase) {
                        plugin.logger.info("Starting test '${testCase.name.name}'")
                    }

                    override suspend fun engineFinished(t: List<Throwable>) {
                        val success = t.isEmpty() && !hasFailures
                        if (success) {
                            plugin.logger.info("All tests passed successfully.")
                        } else {
                            plugin.logger.warning("Test run completed with failures.")
                            t.forEach { throwable ->
                                plugin.logger.warning("Engine error: ${throwable.message}")
                            }
                        }

                        TestResultWriter.writeAndShutdown(plugin, success)
                    }
                }

                TestEngineLauncher()
                    .withListener(listener)
                    .withProjectConfig(KotestProjectConfig)
                    .withClasses(InGameTesterIntegrationTest::class)
                    .launch()
            } catch (e: Throwable) {
                plugin.logger.severe("Failed to execute Kotest runner: ${e.message}")
                TestResultWriter.writeAndShutdown(plugin, false, e)
            }
        })
    }
}
