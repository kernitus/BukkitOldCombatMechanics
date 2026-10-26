/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class PacketLifecycleTest :
    StringSpec({
        listOf("acquire", "load", "initialise", "register", "unregister", "terminate").forEach { failure ->
            "contains $failure failure through reload and repeated shutdown" {
                val backend = RecordingBackend(failure)
                val warnings = mutableListOf<String>()
                val lifecycle = PacketLifecycle(backend, warnings::add)
                val listener = Any()
                lifecycle.load()
                lifecycle.initialise()
                lifecycle.update("sounds", listener, true)
                lifecycle.update("sounds", listener, false)
                repeat(3) {
                    lifecycle.load()
                    lifecycle.initialise()
                    lifecycle.update("sounds", listener, true)
                }
                lifecycle.close()
                lifecycle.close()
                lifecycle.state shouldBe PacketLifecycle.State.STOPPED
                backend.calls.count { it == "acquire" } shouldBe 1
                backend.calls.count { it == "terminate" } shouldBe if (failure == "acquire") 0 else 1
                // Failed unregister gets one final cleanup attempt before backend termination.
                backend.calls.count { it == failure } shouldBe if (failure == "unregister") 2 else 1
                warnings.size shouldBe if (failure == "unregister") 2 else 1
                warnings.all { "Affected features:" in it } shouldBe true
            }
        }
        "owns each listener once and preserves unrelated consumers" {
            val backend = RecordingBackend(null)
            val lifecycle = PacketLifecycle(backend) { error(it) }
            val first = Any()
            val second = Any()
            lifecycle.load()
            lifecycle.initialise()
            repeat(3) { lifecycle.update("sounds", first, true) }
            lifecycle.update("particles", second, false)
            backend.calls.count { it == "register" } shouldBe 1
            backend.calls.count { it == "unregister" } shouldBe 0
            lifecycle.isActive(first) shouldBe true
            lifecycle.update("sounds", first, false)
            lifecycle.isActive(first) shouldBe false
            lifecycle.close()
            backend.calls.count { it == "unregister" } shouldBe 1
        }
    })

private class RecordingBackend(
    private val failure: String?,
) : PacketLifecycle.Backend<Any> {
    val calls = mutableListOf<String>()

    private fun call(stage: String) {
        calls += stage
        if (stage ==
            failure
        ) {
            throw NoClassDefFoundError("injected $stage")
        }
    }

    override fun acquire() = call("acquire")

    override fun load() = call("load")

    override fun initialise() = call("initialise")

    override fun register(listener: Any) = call("register")

    override fun unregister(listener: Any) = call("unregister")

    override fun terminate() = call("terminate")
}
