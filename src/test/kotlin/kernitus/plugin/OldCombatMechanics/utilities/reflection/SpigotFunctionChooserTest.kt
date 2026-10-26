/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities.reflection

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class SpigotFunctionChooserTest :
    StringSpec({
        "successful public action runs once per call without fallback discovery" {
            var actions = 0
            var discoveries = 0
            val chooser =
                SpigotFunctionChooser.apiCompatLazyCall<String, Any, Int>(
                    { _, _ -> ++actions },
                    {
                        discoveries++
                        error("Fallback must stay undiscovered")
                    },
                )
            chooser.apply("target", Any()) shouldBe 1
            chooser.apply("target", Any()) shouldBe 2
            discoveries shouldBe 0
        }
        "reflective chooser accepts an absent fallback while public action succeeds" {
            val chooser =
                SpigotFunctionChooser.apiCompatReflectionCall<String, Any, String>(
                    { target, _ -> target },
                    String::class.java,
                    "missingMethod",
                )
            chooser.apply("public", Any()) shouldBe "public"
        }
        "missing public method discovers fallback once" {
            var attempts = 0
            var discoveries = 0
            val chooser =
                SpigotFunctionChooser.apiCompatLazyCall<String, Any, String>(
                    { _, _ ->
                        attempts++
                        throw NoSuchMethodError()
                    },
                    {
                        discoveries++
                        java.util.function.BiFunction { _, _ -> "fallback" }
                    },
                )
            repeat(2) { chooser.apply("target", Any()) shouldBe "fallback" }
            attempts shouldBe 1
            discoveries shouldBe 1
        }
        "failed discovery is cached" {
            var discoveries = 0
            val failure = IllegalStateException("missing fallback")
            val chooser =
                SpigotFunctionChooser.apiCompatLazyCall<String, Any, String>(
                    { _, _ -> throw NoSuchMethodError() },
                    {
                        discoveries++
                        throw failure
                    },
                )
            repeat(2) { shouldThrow<IllegalStateException> { chooser.apply("target", Any()) } shouldBe failure }
            discoveries shouldBe 1
        }
        "ordinary errors after successful selection retain their original type" {
            var actions = 0
            val chooser =
                SpigotFunctionChooser.apiCompatCall<String, Any, Int>(
                    { _, _ -> if (++actions == 1) actions else throw IllegalArgumentException("later failure") },
                    { _, _ -> error("Unexpected fallback") },
                )
            chooser.apply("target", Any()) shouldBe 1
            shouldThrow<IllegalArgumentException> { chooser.apply("target", Any()) }
        }

        "ordinary errors wrapping missing methods do not permit fallback" {
            val failure = IllegalStateException("ordinary error", NoSuchMethodError())
            val chooser =
                SpigotFunctionChooser.apiCompatCall<String, Any, Int>(
                    { _, _ -> throw failure },
                    { _, _ -> error("Unexpected fallback") },
                )
            shouldThrow<IllegalStateException> { chooser.apply("target", Any()) } shouldBe failure
        }

        "ordinary errors never discover fallback" {
            var discoveries = 0
            val chooser =
                SpigotFunctionChooser.apiCompatLazyCall<String, Any, String>(
                    { _, _ -> throw IllegalArgumentException("invalid action") },
                    {
                        discoveries++
                        error("Unexpected discovery")
                    },
                )
            repeat(2) { shouldThrow<IllegalArgumentException> { chooser.apply("target", Any()) } }
            discoveries shouldBe 0
        }
    })
