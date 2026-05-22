---
name: module-config-change
description: Use for config.yml, module enablement, modesets, config migration, configurable module assignment, and per-module option changes; do not use for unrelated integration-test triage, release publishing, or PR summaries.
---

# Module Config Change

Use this skill when changing module configuration, defaults, migration, or runtime handling driven by `config.yml`.

## When to use

- Editing bundled `config.yml` defaults or comments.
- Adding or changing module settings such as attack cooldown, attack range, sword blocking, old tool damage, tooltip, potion effects, or durability behaviour.
- Changing `always_enabled_modules`, `disabled_modules`, modesets, or config migration logic.
- Validating reload behaviour after config changes.
- Writing tests around config assignment, migration, or modeset-specific behaviour.

## When not to use

- Do not use for ordinary test execution unless the test is config-specific; use `integration-test-verification`.
- Do not use for Java/NMS compatibility strategy unless the config option depends on runtime feature detection; use `compatibility-strategy` as well.
- Do not use for release workflow edits; use `release-readiness-review`.

## Mandatory configuration rules

- Configurable modules must appear in only one top-level assignment category: `always_enabled_modules`, `disabled_modules`, or the aggregate `modesets` category. The same module may appear in more than one individual modeset because modesets are alternative player modes.
- Duplicate entries are still invalid within the same exact list or individual modeset.
- Internal modules (`modeset-listener`, `attack-cooldown-tracker`, `entity-damage-listener`) are always enabled and must not be listed.
- Reload/enable should fail for invalid module assignment.
- Do not add per-module `enabled:` toggles; the module lists and modesets are authoritative.
- Keep bundled comments clear for server owners and use British English.

## Change checklist

1. Identify whether the change is a default, migration, reload, or runtime behaviour change.
2. Update bundled config comments and defaults together when needed.
3. Preserve existing user config where migration should be non-destructive.
4. Run `scripts/validate-module-assignments.sh` for a quick static check that bundled configurable lists do not conflict across `always_enabled_modules`, `disabled_modules`, and the aggregate `modesets` category; do not duplicate entries within the same exact list or modeset; and do not include internal modules.
5. Add or update integration coverage where practical.
6. If adding an integration spec, register it in `KotestRunner.withClasses(...)`.
7. Validate both enabled and disabled/modeset-scoped behaviour for options that change item state or player state.

## Useful known behaviours

- `old-tool-damage.tooltip.enabled` is enabled by default so players can see configured damage in-game.
- `attack-range` is listed in `disabled_modules` by default and is Paper 1.21.11+ only.
- `sword-blocking.paper-animation` defaults to `true`; when false, sword blocking uses the legacy shield fallback and strips stale Paper consumable state.
- Attack cooldown supports `disable-attack-cooldown.held-item-attack-speeds.<MATERIAL>` with XMaterial/Material matching and restores vanilla attack speed when disabled.

## Validation template

```text
Config area:
Default changed:
Migration needed:
Enabled-path validation:
Disabled/modeset validation:
KotestRunner registration needed:
```

## References

- Historical integration and module notes: `../integration-test-verification/references/relocated-agents-notes.md`.
