/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities

/** Owns only this plugin's packet resources. Failed capabilities are not retried on reload. */
class PacketLifecycle<L>(
    private val backend: Backend<L>,
    private val report: java.util.function.Consumer<String>,
) {
    interface Backend<L> {
        fun acquire()

        fun load()

        fun initialise()

        fun register(listener: L)

        fun unregister(listener: L)

        fun terminate()
    }

    enum class State { NEW, ACQUIRED, LOADED, READY, FAILED, STOPPED }

    @Volatile
    var state = State.NEW
        private set
    private var acquired = false
    private val owned = java.util.IdentityHashMap<L, String>()
    private val failed = mutableSetOf<String>()
    val isReady: Boolean get() = state == State.READY

    @Synchronized
    fun isActive(listener: L): Boolean = isReady && owned[listener]?.let { it !in failed } == true

    @Synchronized
    fun load() {
        if (state != State.NEW) return
        attempt("packet load") {
            backend.acquire()
            acquired = true
            state = State.ACQUIRED
            backend.load()
            state = State.LOADED
        }
    }

    @Synchronized
    fun initialise() {
        if (state != State.LOADED) return
        attempt("packet initialisation") {
            backend.initialise()
            state = State.READY
        }
    }

    @Synchronized
    fun update(
        feature: String,
        listener: L,
        enabled: Boolean,
    ) {
        if (!isReady || feature in failed) return
        if (enabled && !owned.containsKey(listener)) {
            // Registration may acquire a resource before throwing; retain its identity for cleanup.
            owned[listener] = feature
            attempt(feature) { backend.register(listener) }
        } else if (!enabled && owned.containsKey(listener)) {
            attempt(feature) {
                backend.unregister(listener)
                owned.remove(listener)
            }
        }
    }

    @Synchronized
    fun close() {
        if (state == State.STOPPED) return
        state = State.STOPPED
        owned.keys.toList().forEach { listener ->
            attempt("packet listener cleanup") { backend.unregister(listener) }
        }
        owned.clear()
        if (acquired) {
            acquired = false
            attempt("packet termination") { backend.terminate() }
        }
    }

    private fun attempt(
        feature: String,
        action: () -> Unit,
    ) {
        try {
            action()
        } catch (failure: Exception) {
            degrade(feature, failure)
        } catch (failure: LinkageError) {
            degrade(feature, failure)
        }
    }

    private fun degrade(
        feature: String,
        failure: Throwable,
    ) {
        if (feature == "packet load" || feature == "packet initialisation") state = State.FAILED
        if (failed.add(feature)) {
            val affected =
                if (state == State.FAILED) {
                    "attack sound suppression, sweep particle suppression and packet client-version detection"
                } else {
                    feature
                }
            report.accept(
                "PacketEvents degraded: $feature (${failure.javaClass.simpleName}: ${failure.message}). " +
                    "Affected features: $affected. " +
                    "Other combat features remain active; failed operations will not be retried on reload.",
            )
        }
    }
}
