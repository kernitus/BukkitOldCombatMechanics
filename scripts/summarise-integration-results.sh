#!/bin/sh
set -eu

usage() {
  cat <<'USAGE'
Usage: scripts/summarise-integration-results.sh [-h]

Reports compact integration-test outcomes from run/* plugin result files.
It deliberately does not open build/integration-test-logs/*.log. If compact
results are insufficient, ask explicitly before inspecting full server logs.
USAGE
}

case "${1:-}" in
  -h|--help)
    usage
    exit 0
    ;;
  '') ;;
  *)
    printf '%s\n' "Unknown option: $1" >&2
    usage >&2
    exit 2
    ;;
esac

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
found=0
failed=0

for run_dir in "$repo_root"/run/*; do
  [ -d "$run_dir" ] || continue
  version=${run_dir##*/}
  result_file=$run_dir/plugins/OldCombatMechanicsTest/test-results.txt
  failures_file=$run_dir/plugins/OldCombatMechanicsTest/test-failures.txt

  if [ ! -f "$result_file" ] && [ ! -f "$failures_file" ]; then
    continue
  fi

  found=1
  result="MISSING"
  [ ! -f "$result_file" ] || result=$(tr -d '\r' < "$result_file" | sed '/^[[:space:]]*$/d' | sed -n '1p')

  printf '%s: %s\n' "$version" "$result"
  if [ "$result" != "PASS" ]; then
    failed=1
  fi

  if [ -s "$failures_file" ]; then
    sed 's/^/  /' "$failures_file"
    failed=1
  fi
done

if [ "$found" -eq 0 ]; then
  printf '%s\n' 'No compact integration-test result files found under run/*/plugins/OldCombatMechanicsTest/.' >&2
  exit 1
fi

exit "$failed"
