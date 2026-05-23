# OldCombatMechanics API

The OldCombatMechanics API exposes both configured modesets and temporary per-player module overrides. Prefer modesets for arena and minigame combat modes, such as offering separate 1.8-style and modern PvP experiences. Modesets are named configured bundles, selected per player and world, and changes made through `setModesetForPlayer` use the normal player-data persistence.

Module overrides are different: they are temporary API-only per-player exceptions for individual modules, intended for narrow runtime cases where a plugin needs to force one module on or off for one currently online player. Overrides are online-session-only, in-memory state. They are cleared when the player quits, when OldCombatMechanics is disabled, or when a plugin explicitly clears them through the API. Module overrides are not a replacement for configured modesets.

| Use case | Modesets | Module overrides |
| --- | --- | --- |
| Arena/minigame combat style, such as 1.8-style versus modern PvP | Preferred: configure named bundles and select them per player/world | Not intended for this |
| Persistence | Uses normal player-data persistence when changed through `setModesetForPlayer` | Online-session-only, in-memory |
| Scope | Whole configured bundle of module choices | Individual module exception for one player |
| Configuration source | Server configuration and allowed world modesets | API-only runtime state |

Overrides are shared runtime state. If multiple plugins set an override for the same player and module, the last write wins. Any plugin that clears that override clears the same shared state for that player and module.

The API also exposes configured modesets, world-allowed modesets, the player's stored modeset for their current world, modeset changes, and Bukkit events for module override and modeset changes.

---

## Dependency setup

There is currently no separate slim API artefact. Compile against the same OldCombatMechanics plugin jar that you deploy or test with, and mark it as compile-only/provided so your plugin does not shade or bundle OCM.

Gradle Kotlin DSL with a local jar:

```kotlin
dependencies {
    compileOnly(files("libs/OldCombatMechanics.jar"))
    compileOnly("org.spigotmc:spigot-api:1.21.11-R0.1-SNAPSHOT")
}
```

Maven with a local or system-provided jar:

```xml
<dependency>
    <groupId>kernitus.plugin</groupId>
    <artifactId>OldCombatMechanics</artifactId>
    <version>your-ocm-version</version>
    <scope>provided</scope>
</dependency>
```

If you use a local Maven repository or an internal repository manager, publish the OCM jar there and keep the dependency `provided`/`compileOnly`.

Do not use OCM internal packages. Public API classes live under:

```java
import kernitus.plugin.OldCombatMechanics.api.OldCombatMechanicsAPI;
import kernitus.plugin.OldCombatMechanics.api.PlayerModesetChangeEvent;
import kernitus.plugin.OldCombatMechanics.api.PlayerModuleOverride;
import kernitus.plugin.OldCombatMechanics.api.PlayerModuleOverrideChangeEvent;
```

---

## `plugin.yml` load order

Use `depend` when your plugin cannot enable or function without OCM:

```yaml
depend: [OldCombatMechanics]
```

Use `softdepend` when OCM integration is optional and your plugin can run without it:

```yaml
softdepend: [OldCombatMechanics]
```

With `softdepend`, always treat the API service as optional and disable only your OCM-specific integration if the service is absent.

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

### `getModesetNames`

Returns all configured modeset names in config iteration order.
```java
Set<String> getModesetNames()
```
```java
for (String modeset : api.getModesetNames()) {
    player.sendMessage(modeset);
}
```

---

### `getAllowedModesets`

Returns the modesets allowed in a world as an unmodifiable defensive copy.
```java
Set<String> getAllowedModesets(World world)
```

Iteration order is meaningful: it follows the configured world list when present, the `worlds.__default__` list for worlds without their own list, or the configured modeset order when no world list applies.

There is no public API for a default or fallback modeset. If you need to present a default choice, use your own policy, such as the player's stored modeset when present or the first entry from `getAllowedModesets(world)`.

```java
Set<String> allowed = api.getAllowedModesets(player.getWorld());
for (String modeset : allowed) {
    player.sendMessage("Allowed here: " + modeset);
}
```

---

### `getModesetForPlayer`

Returns the stored modeset for the player in their current world, or `null` when no player-specific modeset has been stored yet.
```java
String getModesetForPlayer(Player player)
```
```java
String modeset = api.getModesetForPlayer(player);
if (modeset == null) {
    player.sendMessage("No modeset selected for this world.");
} else {
    player.sendMessage("Current modeset: " + modeset);
}
```

---

### `setModesetForPlayer`

Stores the modeset for the player in their current world and fires `PlayerModesetChangeEvent` with reason `API` when the stored value changes.
```java
void setModesetForPlayer(Player player, String modesetName)
```

The input name is normalised before validation and storage. Unknown modesets, or modesets not allowed in the player's current world, throw `IllegalArgumentException`.

Modeset changes made through the API follow normal player data persistence semantics: OCM stores the selected modeset in player data and schedules the usual save. They are not session-only overrides.

```java
if (api.getAllowedModesets(player.getWorld()).contains("arena")) {
    api.setModesetForPlayer(player, "arena");
}
```

---

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

## Events

Register event listeners with Bukkit as usual. Events are fired after OCM changes the stored API state and before it reapplies module state for the affected player.

### `PlayerModesetChangeEvent`

Fired when a player's stored modeset changes. Reasons are `API`, `COMMAND`, `WORLD_CHANGE`, and `JOIN`.

```java
@EventHandler
public void onModesetChange(PlayerModesetChangeEvent event) {
    Player player = event.getPlayer();
    String previous = event.getPreviousModeset();
    String current = event.getNewModeset();

    player.sendMessage("Modeset changed from " + previous + " to " + current
            + " because of " + event.getReason());
}
```

`getPreviousModeset()` and `getNewModeset()` can return `null` when no modeset was previously stored or no replacement is available.

### `PlayerModuleOverrideChangeEvent`

Fired when a player's per-player module override changes.

```java
@EventHandler
public void onModuleOverrideChange(PlayerModuleOverrideChangeEvent event) {
    if (event.getNewOverride() == PlayerModuleOverride.DEFAULT) {
        event.getPlayer().sendMessage(event.getModuleName() + " is following configured rules again.");
        return;
    }

    event.getPlayer().sendMessage(event.getModuleName() + " changed from "
            + event.getPreviousOverride() + " to " + event.getNewOverride());
}
```

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
