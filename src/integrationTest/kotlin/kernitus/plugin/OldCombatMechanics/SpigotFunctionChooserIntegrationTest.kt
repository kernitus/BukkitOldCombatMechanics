/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kernitus.plugin.OldCombatMechanics.utilities.reflection.SpigotFunctionChooser
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalKotest::class)
class SpigotFunctionChooserIntegrationTest :
    StringSpec({
        "compatibility failures choose fallback and stay cached" {
            val compatibilityFailures =
                listOf<Throwable>(
                    NoSuchMethodError("missing API method"),
                    NoClassDefFoundError("missing API class"),
                    AbstractMethodError("abstract API method"),
                    IncompatibleClassChangeError("binary incompatibility"),
                    CompatibilityUnsupportedOperationException("compatibility fallback approved"),
                )

            compatibilityFailures.forEach { throwable ->
                val apiCalls = AtomicInteger(0)
                val fallbackCalls = AtomicInteger(0)

                val chooser =
                    SpigotFunctionChooser.apiCompatCall<String, Any, String>(
                        { _, _ ->
                            apiCalls.incrementAndGet()
                            throw throwable
                        },
                        { _, _ ->
                            fallbackCalls.incrementAndGet()
                            "fallback"
                        },
                    )

                chooser.apply("target", Any()) shouldBe "fallback"
                chooser.apply("target", Any()) shouldBe "fallback"
                apiCalls.get() shouldBe 1
                fallbackCalls.get() shouldBe 2
            }
        }

        "ordinary logic failures are surfaced instead of choosing fallback" {
            val fallbackCalls = AtomicInteger(0)
            val chooser =
                SpigotFunctionChooser.apiCompatCall<String, Any, String>(
                    { _, _ -> throw IllegalStateException("business logic failure") },
                    { _, _ ->
                        fallbackCalls.incrementAndGet()
                        "fallback"
                    },
                )

            shouldThrow<IllegalStateException> {
                chooser.apply("target", Any())
            }
            fallbackCalls.get() shouldBe 0
        }

        "null pointer failures are surfaced instead of choosing fallback" {
            val fallbackCalls = AtomicInteger(0)
            val chooser =
                SpigotFunctionChooser.apiCompatCall<String, Any, String>(
                    { _, _ -> throw NullPointerException("unexpected null") },
                    { _, _ ->
                        fallbackCalls.incrementAndGet()
                        "fallback"
                    },
                )

            shouldThrow<NullPointerException> {
                chooser.apply("target", Any())
            }
            fallbackCalls.get() shouldBe 0
        }

        "generic unsupported operation with incompatible wording is surfaced" {
            val fallbackCalls = AtomicInteger(0)
            val chooser =
                SpigotFunctionChooser.apiCompatCall<String, Any, String>(
                    { _, _ -> throw UnsupportedOperationException("incompatible state for this action") },
                    { _, _ ->
                        fallbackCalls.incrementAndGet()
                        "fallback"
                    },
                )

            shouldThrow<UnsupportedOperationException> {
                chooser.apply("target", Any())
            }
            fallbackCalls.get() shouldBe 0
        }
    })

private class CompatibilityUnsupportedOperationException(
    message: String,
) : UnsupportedOperationException(message)
