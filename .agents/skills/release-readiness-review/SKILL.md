---
name: release-readiness-review
description: Use for GitHub release, Hangar, CurseForge/BukkitDev upload, licence, asset naming, supported-version, and workflow readiness checks; do not use for day-to-day feature implementation, test authoring, or PR prose only.
---

# Release Readiness Review

Use this skill before changing or reviewing release workflows, publishing metadata, supported game versions, licence notes, and final release assets.

## When to use

- Editing `.github/workflows/*release*` or publishing-related Gradle configuration.
- Checking Hangar, CurseForge/BukkitDev, or GitHub release upload behaviour.
- Reviewing licence implications of shaded dependencies such as PacketEvents.
- Updating supported Minecraft versions, `gradle.properties` release metadata, or asset names.
- Preparing a release-readiness checklist for maintainers.

## When not to use

- Do not use for ordinary implementation or test selection; use the feature-specific skill.
- Do not use for PR description wording unless release notes are the main task; use `pr-draft-summary`.
- Do not use for module config behaviour unless it affects packaging or documented release defaults.

## Current release notes

- GitHub release asset uses stable filename `OldCombatMechanics.jar` without a version suffix.
- CurseForge upload uses the same path.
- Bukkit-compatible CurseForge game-version entries use `1:<version>` prefixes so type-1 Bukkit versions are selected.
- Hangar publish configuration expects `HANGAR_API_TOKEN`.
- README licence note: source remains MPL-2.0; pre-built jars bundling PacketEvents are distributed under GPLv3; builds without PacketEvents can remain MPL-2.0.
- `gradle.properties` game versions currently include 1.21.11 down to 1.21.1, plus 1.21, ahead of existing entries.

## Review checklist

1. Confirm secrets are referenced by environment variable or GitHub secret name, never hard-coded.
2. Confirm the built jar path and uploaded asset path match.
3. Confirm supported game versions map to the publishing platform’s expected identifiers.
4. Confirm shaded dependency licence implications are reflected in release notes or README where needed.
5. Confirm workflow changes do not alter plugin runtime behaviour.
6. Prefer dry, static checks unless the user explicitly requests a release run.

## Release note template

```markdown
### Release readiness
- Asset: `OldCombatMechanics.jar`
- Platforms checked: GitHub / Hangar / CurseForge
- Version metadata checked:
- Licence notes checked:
- Secrets touched: none / list variable names only
- Remaining manual steps:
```

## References

- Broader historical notes: `../integration-test-verification/references/relocated-agents-notes.md`.
