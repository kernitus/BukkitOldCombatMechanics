#!/bin/sh
set -eu

usage() {
  cat <<'USAGE'
Usage: scripts/validate-module-assignments.sh [-h] [config-file]

Best-effort static validation for bundled module assignment lists.
Checks that every listed module is assigned to only one configurable top-level
category: always_enabled_modules, disabled_modules, or the aggregate modesets
category. It also checks duplicate entries within the same exact list or
modeset, and that internal modules are not listed in those configurable
sections. Reusing a module in more than one modeset is allowed because modesets
are alternative player modes.

The default config file is src/main/resources/config.yml, resolved relative to
the repository root. This script does not start servers.
USAGE
}

case "${1:-}" in
  -h|--help)
    usage
    exit 0
    ;;
esac

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
config_file=${1:-"$repo_root/src/main/resources/config.yml"}

python3 - "$config_file" <<'PY'
import re
import sys
from collections import defaultdict
from pathlib import Path

config = Path(sys.argv[1])
if not config.is_file():
    print(f"Config file not found: {config}", file=sys.stderr)
    sys.exit(2)

internal = {"modeset-listener", "attack-cooldown-tracker", "entity-damage-listener"}
assignments = defaultdict(list)
section = None
modeset = None

top_level = re.compile(r"^([A-Za-z0-9_.-]+):\s*(?:#.*)?$")
mode_name = re.compile(r"^  ([A-Za-z0-9_.-]+):\s*(?:#.*)?$")
list_item = re.compile(r"^\s*-\s*['\"]?([^'\"#\s]+)['\"]?\s*(?:#.*)?$")

for number, raw in enumerate(config.read_text(encoding="utf-8").splitlines(), 1):
    top = top_level.match(raw)
    if top:
        section = top.group(1)
        modeset = None
        continue

    if section == "modesets":
        mode = mode_name.match(raw)
        if mode:
            modeset = mode.group(1)
            continue

    item = list_item.match(raw)
    if not item:
        continue

    module = item.group(1)
    if section == "always_enabled_modules":
        category = "always_enabled_modules"
        bucket = "always_enabled_modules"
    elif section == "disabled_modules":
        category = "disabled_modules"
        bucket = "disabled_modules"
    elif section == "modesets" and modeset:
        category = "modesets"
        bucket = f"modesets.{modeset}"
    else:
        continue
    assignments[module].append((category, bucket, number))

errors = []
for module, locations in sorted(assignments.items()):
    if module in internal:
        for _category, bucket, number in locations:
            errors.append(f"internal module listed: {module} in {bucket} at line {number}")
    exact_locations = defaultdict(list)
    category_locations = defaultdict(list)
    for category, bucket, number in locations:
        exact_locations[bucket].append(number)
        category_locations[category].append((bucket, number))
    if len(category_locations) > 1:
        where = ", ".join(
            f"{bucket}:line {number}"
            for _category, bucket, number in locations
        )
        errors.append(f"conflicting module assignment: {module} in {where}")
    for bucket, numbers in sorted(exact_locations.items()):
        if len(numbers) > 1:
            where = ", ".join(f"line {number}" for number in numbers)
            errors.append(f"duplicate module assignment within {bucket}: {module} at {where}")

if errors:
    print("Module assignment validation FAILED:")
    for error in errors:
        print(f"- {error}")
    sys.exit(1)

print(f"Module assignment validation passed for {config}")
print(f"Checked {len(assignments)} assigned module entr{'y' if len(assignments) == 1 else 'ies'}.")
PY
