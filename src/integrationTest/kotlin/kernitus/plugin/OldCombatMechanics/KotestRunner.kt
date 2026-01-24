/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics

import com.github.ajalt.mordant.TermColors
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
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

@OptIn(ExperimentalKotest::class)
object KotestProjectConfig : AbstractProjectConfig() {
    override val isolationMode = IsolationMode.SingleInstance
    override val concurrentSpecs = 1
    override val concurrentTests = 1
    override val testCaseOrder = TestCaseOrder.Sequential
}

class BukkitMainThreadDispatcher(
    private val plugin: JavaPlugin,
) : CoroutineDispatcher() {
    override fun dispatch(
        context: CoroutineContext,
        block: Runnable,
    ) {
        Bukkit.getScheduler().runTask(plugin, block)
    }
}

class MainThreadDispatcherExtension(
    private val plugin: JavaPlugin,
) : TestCaseExtension {
    override suspend fun intercept(
        testCase: TestCase,
        execute: suspend (TestCase) -> TestResult,
    ): TestResult {
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
        Bukkit.getScheduler().runTaskAsynchronously(
            plugin,
            Runnable {
                try {
                    var hasFailures = false
                    val failureLines = ArrayList<String>(16)

                    fun throwableFromResult(result: TestResult): Throwable? {
                        // Avoid depending on Kotest internals: fetch any Throwable via reflection for cross-version tolerance.
                        val candidateGetters =
                            listOf(
                                "getErrorOrNull",
                                "getCauseOrNull",
                                "getThrowableOrNull",
                                "getFailureOrNull",
                            )
                        for (getter in candidateGetters) {
                            val m = result::class.java.methods.firstOrNull { it.name == getter && it.parameterCount == 0 } ?: continue
                            val t = runCatching { m.invoke(result) }.getOrNull() as? Throwable
                            if (t != null) return t
                        }
                        return null
                    }

                    fun formatFailure(
                        testCase: TestCase,
                        result: TestResult,
                    ): String {
                        val specName = testCase.spec::class.qualifiedName ?: testCase.spec::class.java.name
                        val testName = testCase.displayName
                        val t = throwableFromResult(result)
                        if (t == null) return "$specName, $testName"

                        val message =
                            t.message
                                ?.lineSequence()
                                ?.firstOrNull()
                                ?.trim()
                                .orEmpty()
                        val head = if (message.isNotEmpty()) "${t::class.java.simpleName}: $message" else t::class.java.simpleName
                        val frame = t.stackTrace.firstOrNull()
                        val at = if (frame != null) " (${frame.fileName}:${frame.lineNumber})" else ""
                        return "$specName, $testName -- $head$at"
                    }

                    val listener =
                        object : AbstractTestEngineListener() {
                            override suspend fun testFinished(
                                testCase: TestCase,
                                result: TestResult,
                            ) {
                                if (result.isFailure || result.isError) {
                                    hasFailures = true
                                    if (failureLines.size < 25) {
                                        failureLines.add(formatFailure(testCase, result))
                                    }
                                }
                            }

                            override suspend fun engineFinished(t: List<Throwable>) {
                                val success = t.isEmpty() && !hasFailures
                                TestResultWriter.writeFailureSummary(plugin, failureLines)
                                TestResultWriter.writeAndShutdown(plugin, success)
                            }
                        }

                    val compositeListener =
                        CompositeTestEngineListener(
                            listOf(
                                EnhancedConsoleTestEngineListener(TermColors()),
                                listener,
                            ),
                        )

                    TestEngineLauncher()
                        .withListener(compositeListener)
                        .withProjectConfig(KotestProjectConfig)
                        .withClasses(
                            ConfigMigrationIntegrationTest::class,
                            ModesetRulesIntegrationTest::class,
                            DisableOffhandIntegrationTest::class,
                            DisableOffhandReflectionIntegrationTest::class,
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
                            AttackCooldownTrackerIntegrationTest::class,
                            PlayerRegenIntegrationTest::class,
                            FishingRodVelocityIntegrationTest::class,
                            SwordSweepIntegrationTest::class,
                            PacketCancellationIntegrationTest::class,
                            EnderpearlCooldownIntegrationTest::class,
                            ChorusFruitIntegrationTest::class,
                            CustomWeaponDamageIntegrationTest::class,
                            ToolDamageTooltipIntegrationTest::class,
                            SwordBlockingIntegrationTest::class,
                            ConsumableComponentIntegrationTest::class,
                            PaperSwordBlockingDamageReductionIntegrationTest::class,
                            AttackRangeIntegrationTest::class,
                        ).launch()
                } catch (e: Throwable) {
                    plugin.logger.severe("Failed to execute Kotest runner: ${e.message}")
                    TestResultWriter.writeAndShutdown(plugin, false, e)
                }
            },
        )
    }
}
