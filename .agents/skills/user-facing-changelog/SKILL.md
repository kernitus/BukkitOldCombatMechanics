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
