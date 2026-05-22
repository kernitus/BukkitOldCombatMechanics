---
name: github-issue-investigation
description: Use for GitHub issue triage and local investigation, including gh issue workflows, picking a fresh issue, reproducing issue reports, and planning regression coverage.
---

# GitHub Issue Investigation

Use this skill when investigating GitHub issues locally, especially bug reports that may need reproduction and durable regression coverage before any fix is attempted.

## Default posture

- Treat GitHub as read-only by default.
- `gh issue list` and `gh issue view` are allowed for investigation.
- Do not comment, label, assign, close, edit milestones, or otherwise mutate public GitHub state unless the user explicitly asks later.
- Keep findings chat-only by default; do not create plans, state files, or persistent notes unless the user asks.
- Try to reproduce before fixing. Prefer a regression test that can remain in the repository when practical.
- After local findings, let the user decide whether to fix, comment, or abandon the issue.

## When an issue number or URL is provided

Inspect that specific issue with `gh issue view`. Summarise:

- reported behaviour and expected behaviour;
- environment, configuration, server, plugin, or version hints;
- missing information that would affect local reproduction;
- related code paths and existing tests worth checking;
- a local reproduction recommendation, including the narrowest likely test or manual scenario.

Avoid changing GitHub state while doing this. If the issue is clearly a duplicate, support question, feature request, or waiting for reporter information, report that and explain whether local investigation is still worthwhile.

## When no issue is provided

Use `gh issue list` to build a shortlist rather than choosing silently. Present about 3-5 candidate issues with:

- issue number and title;
- why the issue looks actionable;
- likely affected area;
- reproduction feasibility;
- reasons to avoid it, if any.

Do not require or prefer positive labels. Actively avoid issues labelled exactly `awaiting testing` or effectively equivalent, because those are usually awaiting reporter or user verification rather than fresh local investigation.

Prefer issues with observable broken behaviour and enough detail to reproduce. Avoid issues that are obvious duplicates, support questions, feature requests, waiting on reporter information, already assigned or visibly in progress, dependent on unavailable external services, private servers, client-only behaviour, or anything requiring GitHub-side mutation before local investigation can proceed.

## Reproduction and coverage

- Reproduce before implementing a fix where practical.
- Prefer integration tests when the behaviour depends on Bukkit/Paper server behaviour, plugin lifecycle, events, fake players, PacketEvents, or cross-version compatibility.
- Use or trigger `integration-test-verification` for integration-test matrix selection, runs, Kotest registration, compact result triage, and failure investigation.
- Respect the repository rule that root and user-facing agents must not read full `build/integration-test-logs/*.log` files directly; use compact summaries first and delegate log inspection only when needed.
- If integration coverage is not feasible, explain why and propose the next best durable coverage, such as a focused unit-level test, synthetic event test, or documented manual reproduction.

## Routing after investigation

Keep this skill investigation-focused. If the user later requests a fix:

- use `TDD` for repro-test-first work;
- use `Planner` when the fix needs planned multi-step work or broader design decisions;
- use `BuildReview` for small standalone quick changes.

Do not start a fix, comment on GitHub, or create persistent planning artefacts merely because an issue looks actionable.
