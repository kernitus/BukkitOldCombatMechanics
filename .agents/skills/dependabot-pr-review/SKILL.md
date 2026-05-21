---
name: dependabot-pr-review
description: Use for Dependabot PRs, dependency bumps, Gradle or Maven dependency updates, GitHub Actions updates, dependency changelog/licence/release-note review, JVM/classfile checks, and validation recommendations.
---

# Dependabot PR Review

Use this skill when reviewing dependency or workflow version updates. Keep the review evidence-based, scoped to the changed dependency, and explicit about what must be checked before merge.

## Cross-load related skills

- Use `integration-test-verification` for integration-test matrix selection, compact failure triage, Kotest registration, or any full integration log hand-off.
- Use `compatibility-strategy` for Java 8, Bukkit/Paper/Spigot, NMS/reflection, PacketEvents, fake-player, or API compatibility concerns.
- Use `release-readiness-review` for licence, shaded dependency, release-note, publishing, or asset-impact questions.

## Review workflow

1. Identify the update manager, dependency scope, touched files, and whether the dependency is production, compile-only, test-only, integration-test-only, or GitHub Actions.
2. Read the PR diff and the upstream release notes, changelog, migration guide, and security notes for every updated version range.
3. Check licence changes and whether the dependency ships in the release jar, shaded jar, or integration-test jar.
4. Check JVM bytecode, classfile version, toolchain, and Gradle module metadata against main Java 8 support and the integration-test Java bands.
5. Check Bukkit/Paper/Spigot compatibility when the dependency is server-facing, API-adjacent, PacketEvents-adjacent, or affects plugin loading.
6. Check shading, relocation, service files, transitive dependency, and duplicate class risks, especially for anything bundled into the plugin jar.
7. For GitHub Actions bumps, check Node/runtime changes, runner image requirements, permission model changes, and deprecated input/output behaviour.
8. Inspect CI status and rerun the narrowest relevant validation where appropriate; prefer static checks for documentation-only or metadata-only bumps.
9. Root and user-facing agents must not open `build/integration-test-logs/*.log` directly. If compact summaries are insufficient, delegate full integration log inspection to a subagent.

## Validation expectations

- Do not skip, weaken, disable, or version-gate tests to make a dependency update pass.
- Match validation to risk: build metadata can often use wrapper/config checks; runtime libraries may need unit or integration tests; server-facing changes may need selected Paper matrix runs.
- Prefer checking the produced dependency graph or shaded jar contents when packaging impact is plausible.

## Verdicts

- `merge`: low-risk update, relevant notes reviewed, CI/validation is green, and no follow-up is needed.
- `merge after checks`: likely safe, but wait for named CI jobs, dependency graph checks, or narrow validation.
- `hold`: compatibility, licence, packaging, JVM, server API, or validation concerns need investigation or code changes.
- `close/ignore`: update is incompatible, not useful for this branch, duplicates another update, or should be deferred by policy.

## Review output template

```markdown
### Dependency review
- Scope: production / compile-only / test-only / integration-test-only / GitHub Actions
- Release notes checked:
- Licence/packaging impact:
- JVM and server compatibility:
- Shading/duplicate class risk:
- Validation:
- Verdict: merge / merge after checks / hold / close/ignore
```
