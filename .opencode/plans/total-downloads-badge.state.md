# Total downloads README badge execution state

plan_path: `.opencode/plans/total-downloads-badge.md`
state_path: `.opencode/plans/total-downloads-badge.state.md`
execution_status: done

## User decisions resolved for execution

- Platform identifiers from the current README are authoritative for implementation:
  - Spigot resource: `19510`.
  - BukkitDev slug: `oldcombatmechanics`.
  - Hangar project: `kernitus/OldCombatMechanics`.
- Repository secrets/API keys are acceptable where platform APIs require them.
- GitHub Pages with source set to GitHub Actions is acceptable if it is not already enabled.
- Badge text should use the planned default shape, `downloads | <compact total>`.

## Slice status

- Slice 1: Add the stats generator — completed.
- Slice 2: Add the GitHub Actions Pages workflow — completed.
- Slice 3: Add the README badge — completed.

## Current target

All planned slices completed.

## Completed validation

- Slice 1 implementation smoke:
  - `python3 .github/scripts/download_stats.py --output build/download-stats-smoke --timeout 10`
  - `python3 -m json.tool build/download-stats-smoke/downloads.json`
  - `python3 -m json.tool build/download-stats-smoke/download-stats.json`
- Slice 1 build review passed with no critical or high findings.
- Slice 2 workflow review: Orca inspected `.github/workflows/update-download-stats.yml` after two empty review-subagent results and confirmed the required schedule/manual triggers, Pages permissions, `github-pages` environment, previous-stats fetch, generator invocation, Pages configure/upload/deploy steps, and safe secret wiring are present.
- Slice 3 build review passed with no critical or high findings; the README badge uses the GitHub Pages Shields endpoint, links to detailed stats, and documents daily aggregate sources.

## Notes

- Orca owns this state file only. Product, workflow, and README edits must be delegated to implementation subagents.
