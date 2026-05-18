---
name: pr-draft-summary
description: Use when drafting pull-request titles, descriptions, change summaries, risk notes, validation sections, or reviewer handoff text; do not use for implementation design, release publishing checks, or raw test failure triage.
---

# PR Draft Summary

Use this skill to turn completed work into reviewer-facing prose. Keep wording concise, accurate, and in British English.

## When to use

- Drafting a PR title or body.
- Summarising changes after a slice or branch of work.
- Writing reviewer notes, risk notes, validation sections, or follow-up items.
- Turning technical details into a changelog-style summary.

## When not to use

- Do not use before implementation decisions are made; use the relevant implementation skill first.
- Do not use for release readiness unless the requested output is a PR description; use `release-readiness-review` for release checks.
- Do not use to interpret raw integration test failures; use `integration-test-verification`.

## Preferred structure

```markdown
## Summary
- 
- 

## Validation
- 

## Risks / notes
- 
```

## Conventional commit prompt

When asked for a commit message, use conventional commits format, for example:

```text
docs: add repo-local agent skill guidance
```

## Reviewer handoff checklist

- Name the changed area before the mechanism.
- Mention validation commands exactly as run.
- If validation was not run, say why.
- Separate behaviour changes from documentation/configuration changes.
- Avoid overstating coverage; note known gaps plainly.

## Example phrasing

```markdown
## Summary
- Added repo-local agent skills for integration-test verification, compatibility strategy, module config changes, release readiness, and PR summaries.
- Slimmed root `AGENTS.md` to always-on rules plus routing guidance, with historical details relocated into skill references.

## Validation
- Checked skill frontmatter and folder-name matches.
- Confirmed no plugin runtime files changed.

## Risks / notes
- opencode must be restarted before new or changed skills are available in a running session.
```
