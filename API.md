# OldCombatMechanics API

The OldCombatMechanics API allows other plugins to manage per-player module overrides at runtime, without touching the global `config.yml`. Overrides are online-session-only, in-memory state for currently online players. They are cleared when the player quits, when OldCombatMechanics is disabled, or when a plugin explicitly clears them through the API.

Overrides are shared runtime state. If multiple plugins set an override for the same player and module, the last write wins. Any plugin that clears that override clears the same shared state for that player and module.

---

## Getting the API instance

Always null-check the Bukkit service registration before using the API. The service is only available while OldCombatMechanics is loaded and enabled.

```java
RegisteredServiceProvider<OldCombatMechanicsAPI> registration = Bukkit.getServicesManager()
        .getRegistration(OldCombatMechanicsAPI.class);
if (registration == null) {
    return;
}

OldCombatMechanicsAPI api = registration.getProvider();
```

Kotlin callers should use the same service lookup and null-check the registration:

```kotlin
val api = Bukkit.getServicesManager()
    .getRegistration(OldCombatMechanicsAPI::class.java)
    ?.provider
    ?: return
```

---

## Threading

Call API methods from the Bukkit main server thread. The current contract does not guarantee async-safe access to Bukkit `Player` state, module state-change notifications, or future implementation details unless a future method explicitly documents different threading behaviour.

---

## Module names

All methods that accept a `moduleName` parameter expect a value from the set returned by [`getModuleNames()`](#getmodulenames). Only configurable modules are valid. Internal modules, such as listener/helper modules, cannot be overridden and are not returned by `getModuleNames()`.

Passing an unknown or non-configurable module name throws `IllegalArgumentException`.

---

## Effective module precedence

When OldCombatMechanics determines whether a configurable module is enabled for a player, it resolves state in this order:

1. per-player `FORCE_ENABLED` or `FORCE_DISABLED` override;
2. configured disabled modules;
3. configured always-enabled modules;
4. player modeset and world configuration.

`PlayerModuleOverride.DEFAULT` means no per-player override is set, so the configured rules above continue to decide the module state.

---

## Methods

### `forceEnableModuleForPlayer`

Forces a configurable module on for an online player until the override is cleared, the player quits, or OldCombatMechanics is disabled.
```java
void forceEnableModuleForPlayer(Player player, String moduleName)
```

**Throws:** `IllegalArgumentException` if `moduleName` is unknown or non-configurable.
```java
api.forceEnableModuleForPlayer(player, "attack-frequency");
```

Kotlin:
```kotlin
api.forceEnableModuleForPlayer(player, "attack-frequency")
```

---

### `forceDisableModuleForPlayer`

Forces a configurable module off for an online player until the override is cleared, the player quits, or OldCombatMechanics is disabled.
```java
void forceDisableModuleForPlayer(Player player, String moduleName)
```

**Throws:** `IllegalArgumentException` if `moduleName` is unknown or non-configurable.
```java
api.forceDisableModuleForPlayer(player, "sword-blocking");
```

Kotlin:
```kotlin
api.forceDisableModuleForPlayer(player, "sword-blocking")
```

---

### `clearModuleOverrideForPlayer`

Clears the override for a single configurable module, reverting the player to configured module behaviour. No-op if no override is set.
```java
void clearModuleOverrideForPlayer(Player player, String moduleName)
```

**Throws:** `IllegalArgumentException` if `moduleName` is unknown or non-configurable.
```java
api.clearModuleOverrideForPlayer(player, "attack-frequency");
```

Kotlin:
```kotlin
api.clearModuleOverrideForPlayer(player, "attack-frequency")
```

---

### `clearAllModuleOverridesForPlayer`

Clears all module overrides for a player at once, reverting them to configured module behaviour for every module. No-op if no overrides are set.
```java
void clearAllModuleOverridesForPlayer(Player player)
```
```java
api.clearAllModuleOverridesForPlayer(player);
```

Kotlin:
```kotlin
api.clearAllModuleOverridesForPlayer(player)
```

---

### `getModuleOverrideForPlayer`

Returns the current override state for a single configurable module for a player. Returns `PlayerModuleOverride.DEFAULT` if no override is set.
```java
PlayerModuleOverride getModuleOverrideForPlayer(Player player, String moduleName)
```

**Throws:** `IllegalArgumentException` if `moduleName` is unknown or non-configurable.
```java
PlayerModuleOverride override = api.getModuleOverrideForPlayer(player, "sword-blocking");
if (override == PlayerModuleOverride.FORCE_ENABLED) {
    player.sendMessage("Forced on");
} else if (override == PlayerModuleOverride.FORCE_DISABLED) {
    player.sendMessage("Forced off");
} else {
    player.sendMessage("Following configured rules");
}
```

Kotlin:
```kotlin
when (api.getModuleOverrideForPlayer(player, "sword-blocking")) {
    PlayerModuleOverride.FORCE_ENABLED -> player.sendMessage("Forced on")
    PlayerModuleOverride.FORCE_DISABLED -> player.sendMessage("Forced off")
    PlayerModuleOverride.DEFAULT -> player.sendMessage("Following configured rules")
}
```

---

### `getModuleOverridesForPlayer`

Returns all active overrides for a player as a map of module name to `PlayerModuleOverride`. Only modules with a non-`DEFAULT` override are included.
```java
Map<String, PlayerModuleOverride> getModuleOverridesForPlayer(Player player)
```
```java
Map<String, PlayerModuleOverride> overrides = api.getModuleOverridesForPlayer(player);
overrides.forEach((module, override) ->
    player.sendMessage(module + ": " + override)
);
```

Kotlin:
```kotlin
api.getModuleOverridesForPlayer(player).forEach { (module, override) ->
    player.sendMessage("$module: $override")
}
```

---

### `setModuleOverridesForPlayer`

Sets multiple module overrides for a player at once. Entries with `PlayerModuleOverride.DEFAULT` are treated as clears. All module names and override values are validated before any override state changes, so an invalid entry leaves existing overrides unchanged.
```java
void setModuleOverridesForPlayer(Player player, Map<String, PlayerModuleOverride> overrides)
```

**Throws:** `IllegalArgumentException` if any module name is unknown or non-configurable, or if a Java caller supplies a null module name or override value.
```java
Map<String, PlayerModuleOverride> overrides = new HashMap<>();
overrides.put("attack-frequency", PlayerModuleOverride.FORCE_ENABLED);
overrides.put("sword-blocking", PlayerModuleOverride.FORCE_ENABLED);
overrides.put("disable-offhand", PlayerModuleOverride.FORCE_DISABLED);
api.setModuleOverridesForPlayer(player, overrides);
```

Kotlin:
```kotlin
api.setModuleOverridesForPlayer(
    player,
    mapOf(
        "attack-frequency" to PlayerModuleOverride.FORCE_ENABLED,
        "sword-blocking" to PlayerModuleOverride.FORCE_ENABLED,
        "disable-offhand" to PlayerModuleOverride.FORCE_DISABLED,
    ),
)
```

---

### `isModuleEnabledForPlayer`

Returns whether a configurable module is effectively active for a player, accounting for configured rules and any per-player override.
```java
boolean isModuleEnabledForPlayer(Player player, String moduleName)
```

**Throws:** `IllegalArgumentException` if `moduleName` is unknown or non-configurable.
```java
if (api.isModuleEnabledForPlayer(player, "attack-frequency")) {
    // module is active for this player
}
```

Kotlin:
```kotlin
if (api.isModuleEnabledForPlayer(player, "attack-frequency")) {
    // module is active for this player
}
```

---

### `hasAnyOverrideForPlayer`

Returns whether a player has any non-default override set.
```java
boolean hasAnyOverrideForPlayer(Player player)
```
```java
if (api.hasAnyOverrideForPlayer(player)) {
    player.sendMessage("You have active overrides.");
}
```

Kotlin:
```kotlin
if (api.hasAnyOverrideForPlayer(player)) {
    player.sendMessage("You have active overrides.")
}
```

---

### `getModuleNames`

Returns the names of all configurable modules that support per-player overrides. Only names in this set are valid inputs for the other API methods.
```java
Set<String> getModuleNames()
```
```java
for (String name : api.getModuleNames()) {
    player.sendMessage(name);
}
```

Kotlin:
```kotlin
api.getModuleNames().forEach { name ->
    player.sendMessage(name)
}
```

---

## `PlayerModuleOverride`

| Value            | Description                                                                            |
|------------------|----------------------------------------------------------------------------------------|
| `DEFAULT`        | No per-player override; the player follows configured module and modeset rules.        |
| `FORCE_ENABLED`  | Module is forced on for the player before configured disabled modules are considered.  |
| `FORCE_DISABLED` | Module is forced off for the player before configured enabled modules are considered.  |

---

## Java example
```java
public class ArenaCombatManager {

    private final OldCombatMechanicsAPI api;

    private static final Map<String, PlayerModuleOverride> ARENA_OVERRIDES;
    static {
        ARENA_OVERRIDES = new HashMap<>();
        ARENA_OVERRIDES.put("attack-frequency", PlayerModuleOverride.FORCE_ENABLED);
        ARENA_OVERRIDES.put("sword-blocking", PlayerModuleOverride.FORCE_ENABLED);
        ARENA_OVERRIDES.put("disable-offhand", PlayerModuleOverride.FORCE_ENABLED);
    }

    public ArenaCombatManager(OldCombatMechanicsAPI api) {
        this.api = api;
    }

    public void onPlayerJoinArena(Player player) {
        api.setModuleOverridesForPlayer(player, ARENA_OVERRIDES);
    }

    public void onPlayerLeaveArena(Player player) {
        api.clearAllModuleOverridesForPlayer(player);
    }
}
```

## Kotlin example
```kotlin
class ArenaCombatManager(private val api: OldCombatMechanicsAPI) {

    private val arenaOverrides = mapOf(
        "attack-frequency" to PlayerModuleOverride.FORCE_ENABLED,
        "sword-blocking" to PlayerModuleOverride.FORCE_ENABLED,
        "disable-offhand" to PlayerModuleOverride.FORCE_ENABLED,
    )

    fun onPlayerJoinArena(player: Player) {
        api.setModuleOverridesForPlayer(player, arenaOverrides)
    }

    fun onPlayerLeaveArena(player: Player) {
        api.clearAllModuleOverridesForPlayer(player)
    }
}
```
