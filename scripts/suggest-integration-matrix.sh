#!/bin/sh
set -eu

usage() {
  cat <<'USAGE'
Usage: scripts/suggest-integration-matrix.sh [-h] [changed-path ...]

Suggests a small integration-test matrix from changed paths. With no paths, it
tries git diff --name-only. Suggestions are conservative hints, not a quality
gate.
USAGE
}

case "${1:-}" in
  -h|--help)
    usage
    exit 0
    ;;
esac

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)

if [ "$#" -eq 0 ]; then
  if command -v git >/dev/null 2>&1 && git -C "$repo_root" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    set -- $(git -C "$repo_root" diff --name-only -- src src/integrationTest build.gradle.kts gradle.properties || true)
  fi
fi

versions="1.19.2,1.21.11"
reason="default modern coverage for uncertain changes"
filters=""

for path in "$@"; do
  case "$path" in
    src/main/resources/config.yml|*config*|*modeset*|*Module*)
      versions="1.12,1.19.2,1.21.11"
      reason="config or module assignment changes benefit from legacy and modern coverage"
      filters="-Dkotest.filter.specs=*Module*,*Config*,*Modeset*"
      ;;
    *Packet*|*packet*|*FakePlayer*|*fake-player*)
      versions="1.19.2,1.21.11"
      reason="packet or fake-player areas need modern Paper/PacketEvents coverage"
      ;;
    *reflection*|*nms*|*legacy*|*1_12*)
      versions="1.12,1.19.2"
      reason="legacy or reflective code should include the Java 8-era path"
      ;;
  esac
done

printf 'Recommended versions: %s\n' "$versions"
printf 'Reason: %s\n' "$reason"
[ -z "$filters" ] || printf 'Suggested filter: %s\n' "$filters"
printf 'Run with: scripts/run-integration-matrix.sh -v %s' "$versions"
[ -z "$filters" ] || printf ' -- %s' "$filters"
printf '\n'
