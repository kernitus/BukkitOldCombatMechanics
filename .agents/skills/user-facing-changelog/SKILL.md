---
name: user-facing-changelog
description: Use when rewriting CHANGELOG.md, GitHub release notes, or Release Please PR changelog sections into user-facing release notes; do not use for release publishing, assets, licence, or workflow readiness checks.
---

# User-facing Changelog

Use this skill to turn generated or developer-oriented changelog material into concise release notes that explain what users will experience after installing the release. Keep wording accurate, useful, and in British English.

## When to use

- Rewriting `CHANGELOG.md` entries for a release.
- Drafting or polishing GitHub release notes.
- Reworking Release Please PR changelog sections before publishing.
- Converting commit subjects, issue links, or PR notes into user-facing release prose.

## When not to use

- Do not use for release readiness checks, upload assets, licence review, supported-version checks, or publishing workflow validation; use `release-readiness-review` for those tasks.
- Do not use for broad PR descriptions unless the requested output is specifically a release-note or changelog section; use `pr-draft-summary` for reviewer-facing prose.

## Context gathering

- Before rewriting, fetch linked GitHub issues and PRs where available from commit subjects, changelog entries, compare views, or release notes.
- Use explicit GitHub URLs first. If only issue or PR numbers are present, resolve them against `https://github.com/kernitus/BukkitOldCombatMechanics`.
- Read enough issue, PR, and commit context to understand the user-visible problem and final shipped behaviour, but avoid broad repository exploration unless the requested rewrite needs it.
- Preserve useful issue links, especially when an entry fixes a reported bug or closes a user request.

## Release Please pull requests

For this repository, `CHANGELOG.md` is the durable repository record, but the Release Please pull-request body is load-bearing release metadata. After the release PR merges, Release Please parses that body to create the GitHub release. The published GitHub release body then becomes the text passed to CurseForge and Hangar through `github.event.release.body` in `.github/workflows/build-upload-release.yml`.

When rewriting a Release Please release:

1. Edit the generated release section in `CHANGELOG.md` on the Release Please branch.
2. Replace the matching release section in the PR body with the same user-facing prose. A commit that changes `CHANGELOG.md` does not synchronise the PR body automatically.
3. Preserve the PR body's `:robot:` header, horizontal-rule separators, version heading, and final Release Please attribution because its structure is parsed by Release Please.
4. Put the standard issue-reporting footer inside the release section, before the final separator, so it reaches the GitHub release and downstream publishing platforms.
5. Make manual changelog and PR-body edits after Release Please's final automatic refresh where practical. A later refresh may overwrite them.
6. Before merging, verify that the release prose in `CHANGELOG.md` and the PR body agrees and that the PR retains its `autorelease: pending` label.

If the user asks only for revised prose, provide the complete replacement PR body and do not mutate GitHub without explicit authorisation.

## Writing rules

- Describe only final shipped user-visible behaviour.
- Do **not** mention interim development churn. If a regression was introduced and fixed between releases and never reached users, omit it entirely or fold it into the final behaviour without saying it regressed.
- Avoid noisy implementation, refactor, build, formatting, dependency, and test-only entries unless they matter to server owners, administrators, plugin integrators, or players.
- Prefer clear categories such as `Highlights`, `Fixes`, `Compatibility`, `Configuration`, `API and integrations`, or `Maintenance` when they help readers scan the release.
- Group related low-level commits into one user-facing entry rather than listing every commit.
- Keep entries concise, concrete, and outcome-focused. Mention affected modules, Minecraft versions, configuration keys, or integrations when that helps users decide whether to update.
- Use British English spelling and phraseology throughout.

## Footer requirement

At the bottom of `CHANGELOG.md` and release-note drafts, include a concise issue-reporting sentence that links the word `GitHub` to the issue tracker:

```markdown
Report issues on [GitHub](https://github.com/kernitus/BukkitOldCombatMechanics/issues).
```

## Quality checklist

- Linked issues and PRs were checked where available.
- The prose avoids unreleased interim churn and describes only shipped behaviour.
- User-relevant categories are clear and not overly fragmented.
- Useful issue links are preserved.
- Test-only, refactor-only, and internal implementation entries are omitted unless user-relevant.
- The issue-reporting footer is present at the bottom when writing changelog or release-note output.
