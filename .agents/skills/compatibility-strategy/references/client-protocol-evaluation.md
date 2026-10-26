# Client protocol API evaluation

Evaluated on 26 October 2026 for the Paper API first compatibility work, slice 6. Retain the existing sword-blocking PacketEvents lookup. No deployment has enough observed original-client evidence to select the public replacement yet. This is a bounded research result; the translation matrix has not passed.

## Public capability

[`NetworkClient.getProtocolVersion`](https://jd.papermc.io/paper/26.3/com/destroystokyo/paper/network/NetworkClient.html#getProtocolVersion()) returns an integer protocol identifier, with `-1` representing unknown. `Player` implements this interface. A future public provider can call it independently of PacketEvents readiness; missing API and `-1` must remain unknown, never the server protocol or proof of modern-client support.

The [Paper CraftPlayer implementation](https://github.com/PaperMC/Paper/blob/main/paper-server/src/main/java/org/bukkit/craftbukkit/entity/CraftPlayer.java) reads the connection's `protocolVersion` field, returning `-1` without a connection. Local bytecode inspection of `run/26.3/versions/26.3/paper-26.3.jar` confirms this implementation:

```sh
javap -c -classpath run/26.3/versions/26.3/paper-26.3.jar org.bukkit.craftbukkit.entity.CraftPlayer
```

This establishes the getter's implementation independently of PacketEvents. It does not establish whether a translator changes the field, or whether a proxy forwards the original client protocol. API presence and source inspection cannot settle that question.

## Available evidence and missing fixtures

The repository's `ConsumableComponentIntegrationTest` creates PacketEvents users and sets client-version enums explicitly. Its old, modern and unknown classifications exercise OCM branching. They do not represent actual handshakes or prove translation semantics. `FakePlayer` connections likewise cannot establish a real client's protocol or rendering.

A bounded search of `run`, `build/test-servers` and `scripts` found no ViaVersion or ViaBackwards plugin jar, complete Velocity or BungeeCord proxy, proxy configuration, or playable client fixture. Bundled `velocity-native`, `bungeecord-chat`, Adventure ViaVersion integration and PacketEvents ViaVersion helper classes are libraries, not those fixtures. No client or proxy was provisioned for this research.

| Required connection | Old client observation | Current client observation | Provider comparison |
| --- | --- | --- | --- |
| Direct native connection | Not collected | Not collected | Not established |
| ViaVersion on the backend | Not collected | Not collected | Not established |
| Proxy-side translation | Not collected | Not collected | Not established |

A follow-up must record the actual client, backend, proxy and translator versions, translation location, public getter result and PacketEvents result for every applicable connection. Choose clients on both sides of the existing 1.20.5 animation threshold, with appropriate translation support. An unsupported direct old-client connection is not a successful classification observation. Repeat after disconnect, reconnect and OCM reload; do not infer rendering from protocol numbers.

## Retained semantics and lifecycle

`ModuleSwordBlocking.supportsPaperAnimation` still requires a ready packet backend, available Paper component adapter and enabled animation setting. Its reflective PacketEvents methods are resolved at module construction. It queries the current player manager on each classification and keeps no per-player protocol cache.

| Existing PacketEvents result | Existing animation decision |
| --- | --- |
| Missing backend, resolver, API, manager or threshold | False |
| Known version below 1.20.5 | False |
| Known version at or above 1.20.5 | True |
| Unknown enum with an existing User | False |
| Null version after attempted User fallback | True, established early-login allowance |
| Unknown enum with no User | True, established early-login allowance |
| Lookup throws | False, existing broad catch retained |

The early-login allowances are existing policy, not evidence of a modern client. They remain unchanged to avoid an unverified compatibility change. No public result, including `-1`, is consulted by this route. The existing broad catches have not been presented as a new provider design.

Reload cleans owned item state; quit restores legacy offhand state and strips owned consumable components without requiring a successful protocol lookup. Since OCM stores no client version, there is no new protocol cache to invalidate. Actual PacketEvents disconnect and reconnect freshness remains unverified by real-client observations.

## Focused coverage and limits

Existing `ConsumableComponentIntegrationTest` cases cover old-client shield fallback, unknown version with a User, a missing resolver, modern consumable activation, reload toggles and quit component cleanup. Those classifications and quit events are synthetic. `PacketLifecycleIntegrationTest` injects acquisition, load, initialisation, registration, unregistration and termination failures, then reloads and checks native combat. It does not assert original-client protocol discovery during those failures.

These suites were exercised in earlier slices; this documentation-only slice does not claim a new integration run. The absent public API path has no production call to validate. Public missing-API and `-1` tests, real reconnect observations, and backend/proxy protocol comparisons remain prerequisites for any future provider selection. The earlier Paper 1.12 reload interaction failure was caused by native login immunity in the test fixture. Commit `007f171f` waits for that immunity to expire: all 14 focused interaction cases pass on each of the five validation versions, and the combined Paper 1.12 selection reports 95 passed, zero failed and one explicit capability skip. This fixture correction provides no additional real-client protocol evidence.
