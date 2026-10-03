---
name: release-readiness-review
description: Use for GitHub release, Hangar, CurseForge/BukkitDev upload, Spigot release handoff, licence, asset naming, supported-version, and workflow readiness checks; do not use for day-to-day feature implementation, test authoring, or PR prose only.
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
- Release Please uses the `java` strategy. After each stable release it opens a separate `autorelease: snapshot` pull request that updates the Gradle version to the next `-SNAPSHOT` version. Merge that pull request before expecting development builds to publish to Hangar; while it remains pending, Release Please prioritises it over the next stable release pull request.
- Release Please parses the merged release PR body to create the GitHub release. Keep its release section aligned with `CHANGELOG.md`; use `user-facing-changelog` for the wording and synchronisation procedure.
- The published GitHub release body is passed to CurseForge and Hangar through `github.event.release.body`.
- CurseForge upload uses the same path.
- Bukkit-compatible CurseForge game-version entries use `1:<version>` prefixes so type-1 Bukkit versions are selected.
- `gradle.properties` keeps platform metadata separate. `paperVersions` is the Hangar range and `gameVersions` is the exact CurseForge Bukkit-version list.
- Hangar supports ranges and wildcards. Keep `paperVersions` aligned with the complete supported Paper range; its current value is `1.9-26.2`.
- Before merging every Release Please pull request, compare `gameVersions` with the current stable Bukkit versions offered by CurseForge. Include every supported stable subversion and exclude every entry labelled `Snapshot`.
- Hangar publish configuration expects `HANGAR_API_TOKEN`.
- README licence note: source remains MPL-2.0; pre-built jars bundling PacketEvents are distributed under GPLv3; builds without PacketEvents can remain MPL-2.0.
- `gameVersions` currently covers every stable Bukkit version from 1.9 through 26.2.

## Review checklist

1. Confirm secrets are referenced by environment variable or GitHub secret name, never hard-coded.
2. Confirm the built jar path and uploaded asset path match.
3. Before the Release Please pull request is merged, review `paperVersions` against the supported Paper range and review `gameVersions` against every exact stable Bukkit version offered by CurseForge. Confirm that no CurseForge entry labelled `Snapshot` is present.
4. Confirm shaded dependency licence implications are reflected in release notes or README where needed.
5. Confirm workflow changes do not alter plugin runtime behaviour.
6. Prefer dry, static checks unless the user explicitly requests a release run.

## Release note template

```markdown
### Release readiness
- Asset: `OldCombatMechanics.jar`
- Platforms checked: GitHub / Hangar / CurseForge
- Supported game versions checked: Hangar range; CurseForge exact list
- Version metadata checked:
- Licence notes checked:
- Secrets touched: none / list variable names only
- Remaining manual steps:
```

## Final step: Spigot update details

When preparing or completing a release, finish by providing all four fields for Spigot's **Post Resource Update** form together. Also provide them when the user requests a Spigot release handoff. Do not add this handoff to unrelated workflow or licence questions.

- **Direct download URL:** Use the version-specific GitHub release asset URL for `OldCombatMechanics.jar`, such as `https://github.com/kernitus/BukkitOldCombatMechanics/releases/download/v<VERSION>/OldCombatMechanics.jar`. Resolve the actual tag and asset from the intended release; do not use a moving latest-release URL. Verify the asset exists before presenting it as ready. If publishing has not completed, label the URL as pending and still prepare the remaining fields.
- **Version String:** Use the stable release version without the tag's leading `v`. Do not copy the next development `-SNAPSHOT` version from the working branch.
- **Update Title:** Provide a concise title containing the version and the main user-visible changes.
- **Message:** Provide one fenced `bbcode` block ready to paste into the editor's BBCode mode. Convert the matching curated release notes using `[b]`, `[list]`, `[*]` and `[url=...]` tags. Preserve material compatibility limitations and useful issue links; omit Release Please's robot header and attribution. End with a link to the full GitHub release and `Report issues on [url=https://github.com/kernitus/BukkitOldCombatMechanics/issues]GitHub[/url].`

Use the published release body as the source, or the matching curated changelog section when publication is pending. Resolve material discrepancies before calling the message ready. Follow `user-facing-changelog` when rewriting the prose, retaining British English. Keep the handoff in chat unless the user requests a file. Preparing these fields does not authorise website submission.

## References

- Broader historical notes: `../integration-test-verification/references/relocated-agents-notes.md`.
