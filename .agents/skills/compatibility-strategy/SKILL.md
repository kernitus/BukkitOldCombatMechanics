---
name: compatibility-strategy
description: Use for Java 8 backports, Bukkit/Paper version differences, NMS/reflection, PacketEvents compatibility, fake-player implementation choices, and feature-detection design; do not use for release publishing, PR wording, or simple test-running decisions.
---

# Compatibility Strategy

Use this skill when a change must behave across legacy and modern Bukkit/Paper versions or across different Java runtimes.

## When to use

- Java 8 source compatibility or dependency updates that might introduce newer APIs.
- NMS, CraftBukkit, Paper, PacketEvents, XSeries, or reflection-sensitive changes.
- Fake-player internals, attack delivery, fire ticks, entity ticking, or synthetic network plumbing.
- Decisions about feature detection versus hard-coded Minecraft version checks.
- Fallback behaviour on Spigot, Paper, legacy 1.12/1.9, or modern 1.20.5+ servers.

## When not to use

- Do not use for release asset or publishing checks; use `release-readiness-review`.
- Do not use for config/modeset-only changes unless compatibility affects the implementation; use `module-config-change` first.
- Do not use for PR prose only; use `pr-draft-summary`.

## Required stance

- Prefer direct supported APIs when present.
- Use reflection only as a fallback or compatibility bridge, especially off hot paths.
- Prefer feature detection by class, method, field, component, or capability over hard-coded version numbers.
- Keep Java 8-targeted code free of Java 9+ collection factories, `Stream.toList()`, records, pattern matching, and other newer language/runtime features.
- For NMS access, prefer `utilities.reflection.Reflector` helpers where they fit the problem.
- Avoid hard-coded versioned NMS class names unless no heuristic or API-based path is viable.

## Compatibility checklist

1. Identify the minimum supported Java runtime for the touched source set.
2. Identify whether Bukkit/Paper exposes a direct API for the target behaviour.
3. If an API may be missing or backported, add runtime feature detection.
4. Choose a safe fallback that preserves existing behaviour where feature detection fails.
5. Keep diagnostics actionable when every candidate path fails.
6. Select validation versions that cover the compatibility boundary, usually a legacy version plus a modern Paper version.

## Java 8 reminders

- Use `collect(Collectors.toList())` instead of `Stream.toList()`.
- Use mutable collections wrapped with `Collections.unmodifiable*` where immutable Java 9 factories would otherwise be tempting.
- Avoid records, sealed classes, switch expressions, pattern matching, and `var` in Java source.
- Main build config targets Java 8 via `options.release.set(8)` and Kotlin `jvmTarget = 1.8`.

## Fake-player and NMS reminders

- The primary fake-player path is modern-NMS-oriented and relies on reflection remapping.
- Legacy 1.12 uses versioned NMS and currently needs dedicated or version-aware handling for realistic fake-player tests.
- Fake players are simulated login/network entities, not real network clients.
- PacketEvents may need fake users or explicit client-version seeding in synthetic scenarios.

## References

- Broader test-harness and historical notes: `../integration-test-verification/references/relocated-agents-notes.md`.
