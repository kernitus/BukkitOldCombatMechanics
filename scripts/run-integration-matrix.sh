#!/bin/sh
set -eu

usage() {
  cat <<'USAGE'
Usage: scripts/run-integration-matrix.sh [-v versions] [-s spec-filter] [-t test-filter] [--] [extra Gradle args...]

Wraps ./gradlew integrationTest using repository-relative paths.

Options:
  -v, --versions LIST      Comma-separated Minecraft versions, for example 1.19.2,1.21.11
  -s, --spec-filter TEXT   Kotest spec filter passed as -Dkotest.filter.specs=TEXT
  -t, --test-filter TEXT   Kotest test filter passed as -Dkotest.filter.tests=TEXT
  -h, --help               Show this help text

This command may start Paper servers. For a non-invasive check, use
scripts/validate-module-assignments.sh or scripts/summarise-integration-results.sh.
USAGE
}

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
versions=
spec_filter=
test_filter=

while [ "$#" -gt 0 ]; do
  case "$1" in
    -v|--versions)
      [ "$#" -ge 2 ] || { printf '%s\n' "Missing value for $1" >&2; usage >&2; exit 2; }
      versions=$2
      shift 2
      ;;
    -s|--spec-filter)
      [ "$#" -ge 2 ] || { printf '%s\n' "Missing value for $1" >&2; usage >&2; exit 2; }
      spec_filter=$2
      shift 2
      ;;
    -t|--test-filter)
      [ "$#" -ge 2 ] || { printf '%s\n' "Missing value for $1" >&2; usage >&2; exit 2; }
      test_filter=$2
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    --)
      shift
      break
      ;;
    -*)
      printf '%s\n' "Unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
    *)
      break
      ;;
  esac
done

set -- integrationTest "$@"
[ -z "$versions" ] || set -- "$@" "-PintegrationTestVersions=$versions"
[ -z "$spec_filter" ] || set -- "$@" "-Dkotest.filter.specs=$spec_filter"
[ -z "$test_filter" ] || set -- "$@" "-Dkotest.filter.tests=$test_filter"

printf '%s\n' "Repository: $repo_root"
printf '%s\n' "Running: ./gradlew $*"
cd "$repo_root"
exec ./gradlew "$@"
