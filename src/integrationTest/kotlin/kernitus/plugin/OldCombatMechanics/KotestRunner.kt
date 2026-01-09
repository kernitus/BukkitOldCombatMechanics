/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics

import io.kotest.common.ExperimentalKotest
import io.kotest.common.KotestInternal
import io.kotest.core.config.AbstractProjectConfig
import io.kotest.core.extensions.TestCaseExtension
import io.kotest.core.spec.IsolationMode
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestCaseOrder
import io.kotest.core.test.TestResult
import io.kotest.engine.TestEngineLauncher
import io.kotest.engine.listener.AbstractTestEngineListener
import io.kotest.engine.listener.CompositeTestEngineListener
import io.kotest.engine.listener.EnhancedConsoleTestEngineListener
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import com.github.ajalt.mordant.TermColors
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

@OptIn(ExperimentalKotest::class)
object KotestProjectConfig : AbstractProjectConfig() {
    override val isolationMode = IsolationMode.SingleInstance
    override val concurrentSpecs = 1
    override val concurrentTests = 1
    override val testCaseOrder = TestCaseOrder.Sequential
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
                        if (result.isFailure || result.isError) {
                            hasFailures = true
                        }
                    }

                    override suspend fun engineFinished(t: List<Throwable>) {
                        val success = t.isEmpty() && !hasFailures
                        TestResultWriter.writeAndShutdown(plugin, success)
                    }
                }

                val compositeListener = CompositeTestEngineListener(
                    listOf(
                        EnhancedConsoleTestEngineListener(TermColors()),
                        listener
                    )
                )

                TestEngineLauncher()
                    .withListener(compositeListener)
                    .withProjectConfig(KotestProjectConfig)
                    .withClasses(
                        ConfigMigrationIntegrationTest::class,
                        ModesetRulesIntegrationTest::class,
                        DisableOffhandIntegrationTest::class,
                        InGameTesterIntegrationTest::class,
                        CopperToolsIntegrationTest::class,
                        OldPotionEffectsIntegrationTest::class,
                        InvulnerabilityDamageIntegrationTest::class,
                        FireAspectOverdamageIntegrationTest::class,
                        OldCriticalHitsIntegrationTest::class,
                        OldToolDamageMobIntegrationTest::class,
                        WeaponDurabilityIntegrationTest::class,
                        GoldenAppleIntegrationTest::class,
                        OldArmourDurabilityIntegrationTest::class,
                        PlayerKnockbackIntegrationTest::class,
                        SwordSweepIntegrationTest::class,
                        ChorusFruitIntegrationTest::class,
                        CustomWeaponDamageIntegrationTest::class
                    )
                    .launch()
            } catch (e: Throwable) {
                plugin.logger.severe("Failed to execute Kotest runner: ${e.message}")
                TestResultWriter.writeAndShutdown(plugin, false, e)
            }
        })
    }
}
