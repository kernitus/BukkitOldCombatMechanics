---
name: commit-preparation
description: Supports preparing commits, pre-commit hooks, Spotless, formatting or linting, validation before commit, staging files, and writing conventional commits.
---

# Commit Preparation

Use this skill whenever an agent is preparing a repository state for commit, validating changes before commit, handling pre-commit hooks, or choosing staging and commit-message details.

## Required checks before committing

- Inspect `git status`, `git diff`, and recent commits before committing.
- Run `./gradlew spotlessCheck` before committing.
- Run `./gradlew check` before committing, unless a narrower validation is clearly justified for the change and that justification is reported in chat.
- Run relevant integration tests for behaviour, compatibility, module, or bug-fix changes.
- Use `integration-test-verification` to choose an integration-test matrix and to triage integration-test failures.
- Never skip, weaken, disable, or version-gate tests to make validation pass. Fix the root cause or report the failure as a blocker unless the user explicitly approves a coverage reduction.

## Staging and commit messages

- Stage only intended files, and re-check the staged diff before committing.
- Use conventional commits.
- Keep commit messages subject-only unless the user explicitly asks for a body.
- Make the subject release-note-friendly and comprehensible without the type prefix or body.
- For issue, ticket, or bug-report driven work, include the ticket number in the commit subject, such as `fix(modesets): correct stale stored world modesets (#865)`.

## Reporting

- Keep validation findings in chat unless committing is explicitly requested.
- If validation is narrowed, skipped, blocked, or failing, report the reason and any remaining risk before committing.
