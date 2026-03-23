# OldCombatMechanics API

The OldCombatMechanics API allows other plugins to manage per-player module overrides at runtime, without touching the global `config.yml`. Overrides persist across sessions and are resolved on top of the global configuration when determining a module's effective state for a player.

---

## Getting the API instance
```java
OldCombatMechanicsAPI api = Bukkit.getServicesManager()
    .getRegistration(OldCombatMechanicsAPI.class)
    .getProvider();
```

> Always null-check the result — the API is only available if OldCombatMechanics is loaded and enabled.

---

## Thread safety

All methods are thread-safe. State-change notifications are automatically dispatched to the main thread when called from an async context.

---

## Module names

All methods that accept a `moduleName` parameter expect a value from the set returned by [`getModuleNames()`](#getmodulenames). Passing an unknown name throws `IllegalArgumentException`.

---

## Methods

### `forceEnableModuleForPlayer`

Forces a module on for a player, overriding the global config. Persists until explicitly cleared.
```java
void forceEnableModuleForPlayer(UUID playerId, String moduleName)
void forceEnableModuleForPlayer(Player player, String moduleName)
```

**Throws:** `IllegalArgumentException` if `moduleName` is unknown.
```java
api.forceEnableModuleForPlayer(player, "old-attack-speed");
```

---

### `forceDisableModuleForPlayer`

Forces a module off for a player, overriding the global config. Persists until explicitly cleared.
```java
void forceDisableModuleForPlayer(UUID playerId, String moduleName)
void forceDisableModuleForPlayer(Player player, String moduleName)
```

**Throws:** `IllegalArgumentException` if `moduleName` is unknown.
```java
api.forceDisableModuleForPlayer(player.getUniqueId(), "old-damage");
```

---

### `clearModuleOverrideForPlayer`

Clears the override for a single module, reverting the player to the global config for that module. No-op if no override is set.
```java
void clearModuleOverrideForPlayer(UUID playerId, String moduleName)
void clearModuleOverrideForPlayer(Player player, String moduleName)
```

**Throws:** `IllegalArgumentException` if `moduleName` is unknown.
```java
api.clearModuleOverrideForPlayer(player, "old-attack-speed");
```

---

### `clearAllModuleOverridesForPlayer`

Clears all module overrides for a player at once, reverting them to the global config for every module. No-op if no overrides are set.
```java
void clearAllModuleOverridesForPlayer(UUID playerId)
void clearAllModuleOverridesForPlayer(Player player)
```
```java
api.clearAllModuleOverridesForPlayer(player);
```

---

### `getModuleOverrideForPlayer`

Returns the current override state for a single module for a player. Returns `PlayerModuleOverride.DEFAULT` if no override is set.
```java
PlayerModuleOverride getModuleOverrideForPlayer(UUID playerId, String moduleName)
PlayerModuleOverride getModuleOverrideForPlayer(Player player, String moduleName)
```

**Throws:** `IllegalArgumentException` if `moduleName` is unknown.
```java
PlayerModuleOverride override = api.getModuleOverrideForPlayer(player, "old-attack-speed");
if (override == PlayerModuleOverride.FORCE_ENABLED) {
    player.sendMessage("Forced ON");
} else if (override == PlayerModuleOverride.FORCE_DISABLED) {
    player.sendMessage("Forced OFF");
} else {
    player.sendMessage("Following global config");
}
```

---

### `getModuleOverridesForPlayer`

Returns all active overrides for a player as a map of module name to `PlayerModuleOverride`. Only modules with a non-`DEFAULT` override are included.
```java
Map<String, PlayerModuleOverride> getModuleOverridesForPlayer(UUID playerId)
Map<String, PlayerModuleOverride> getModuleOverridesForPlayer(Player player)
```
```java
Map<String, PlayerModuleOverride> overrides = api.getModuleOverridesForPlayer(player);
overrides.forEach((module, override) ->
    player.sendMessage(module + ": " + override)
);
```

---

### `setModuleOverridesForPlayer`

Sets multiple module overrides for a player at once. Entries with `PlayerModuleOverride.DEFAULT` are treated as clears. All module names are validated before any state is changed.
```java
void setModuleOverridesForPlayer(UUID playerId, Map<String, PlayerModuleOverride> overrides)
void setModuleOverridesForPlayer(Player player, Map<String, PlayerModuleOverride> overrides)
```

**Throws:** `IllegalArgumentException` if any module name is unknown.
```java
Map<String, PlayerModuleOverride> overrides = new HashMap<>();
overrides.put("old-attack-speed", PlayerModuleOverride.FORCE_ENABLED);
overrides.put("old-damage",       PlayerModuleOverride.FORCE_ENABLED);
overrides.put("old-knockback",    PlayerModuleOverride.FORCE_DISABLED);
api.setModuleOverridesForPlayer(player, overrides);
```

---

### `isModuleEnabledForPlayer`

Returns whether a module is effectively active for a player, accounting for both the global config and any per-player override. If the player is offline, falls back to checking the override state directly.
```java
boolean isModuleEnabledForPlayer(UUID playerId, String moduleName)
boolean isModuleEnabledForPlayer(Player player, String moduleName)
```

**Throws:** `IllegalArgumentException` if `moduleName` is unknown.
```java
if (api.isModuleEnabledForPlayer(player, "old-attack-speed")) {
    // module is active for this player
}
```

---

### `hasAnyOverrideForPlayer`

Returns whether a player has any non-default override set.
```java
boolean hasAnyOverrideForPlayer(UUID playerId)
boolean hasAnyOverrideForPlayer(Player player)
```
```java
if (api.hasAnyOverrideForPlayer(player)) {
    player.sendMessage("You have active overrides.");
}
```

---

### `getModuleNames`

Returns the names of all modules that support per-player overrides. Only names in this set are valid inputs for the other API methods.
```java
Set<String> getModuleNames()
```
```java
for (String name : api.getModuleNames()) {
    player.sendMessage(name);
}
```

---

## `PlayerModuleOverride`

| Value            | Description                                            |
|------------------|--------------------------------------------------------|
| `DEFAULT`        | No override set; the player follows the global config. |
| `FORCE_ENABLED`  | Module is forced on regardless of the global config.   |
| `FORCE_DISABLED` | Module is forced off regardless of the global config.  |

---

## Example
```java
public class ArenaCombatManager {

    private final OldCombatMechanicsAPI api;

    private static final Map<String, PlayerModuleOverride> ARENA_OVERRIDES;
    static {
        ARENA_OVERRIDES = new HashMap<>();
        ARENA_OVERRIDES.put("old-attack-speed", PlayerModuleOverride.FORCE_ENABLED);
        ARENA_OVERRIDES.put("old-damage",       PlayerModuleOverride.FORCE_ENABLED);
        ARENA_OVERRIDES.put("old-knockback",    PlayerModuleOverride.FORCE_ENABLED);
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