#!/usr/bin/env python3
"""Generate aggregate download statistics for the README badge.

The script writes two files into the requested output directory:

* ``downloads.json``: a Shields endpoint JSON document.
* ``download-stats.json``: detailed per-source values and warnings.

Each source is collected independently. Transient source failures do not make
the whole run unusable; when previous stats are supplied, the last known value
for that source is reused and marked as such. Without previous stats, failed or
skipped sources are excluded from the aggregate total and clearly reported.
"""

import argparse
import datetime
import json
import os
import re
import sys
from urllib.error import HTTPError, URLError
from urllib.parse import quote
from urllib.request import Request, urlopen


DEFAULT_REPOSITORY = "kernitus/BukkitOldCombatMechanics"
DEFAULT_OUTPUT = "build/download-stats"

# Non-GitHub source map. Keep endpoint choices visible because these providers
# have different stability guarantees:
#
# * Hangar: public PaperMC Hangar v1 project API. The project payload currently
#   exposes aggregate downloads in ``stats.downloads``.
# * BukkitDev/CurseForge: official CurseForge Core API search by slug. This is
#   preferred over page scraping but requires a ``CURSEFORGE_API_KEY`` secret.
# * Spigot: Spiget's public JSON API is used first because Spigot does not
#   publish an official download-count API. If Spiget is unavailable, the
#   resource page is fetched with conservative headers and parsed in one isolated
#   fallback function.
SOURCES = {
    "github": {
        "label": "GitHub Releases",
        "repository": DEFAULT_REPOSITORY,
        "api": "https://api.github.com/repos/{repository}/releases",
    },
    "hangar": {
        "label": "Hangar",
        "project": "kernitus/OldCombatMechanics",
        "api": "https://hangar.papermc.io/api/v1/projects/{project}",
    },
    "curseforge": {
        "label": "BukkitDev/CurseForge",
        "slug": "oldcombatmechanics",
        "api": "https://api.curseforge.com/v1/mods/search?gameId=1&slug={slug}",
    },
    "spigot": {
        "label": "Spigot",
        "resource_id": "19510",
        "api": "https://api.spiget.org/v2/resources/{resource_id}",
        "page": "https://www.spigotmc.org/resources/{resource_id}/",
    },
}


class SourceError(Exception):
    """A download source could not be collected."""


def utc_now_iso():
    return (
        datetime.datetime.now(datetime.timezone.utc)
        .replace(microsecond=0)
        .isoformat()
        .replace("+00:00", "Z")
    )


def request_json(url, headers=None, timeout=20):
    request = Request(url, headers=headers or {})
    with urlopen(request, timeout=timeout) as response:
        return json.loads(response.read().decode("utf-8"))


def request_text(url, headers=None, timeout=20):
    request = Request(url, headers=headers or {})
    with urlopen(request, timeout=timeout) as response:
        return response.read().decode("utf-8", "replace")


def github_headers():
    headers = {
        "Accept": "application/vnd.github+json",
        "User-Agent": "BukkitOldCombatMechanics-download-stats",
        "X-GitHub-Api-Version": "2022-11-28",
    }
    token = os.environ.get("GITHUB_TOKEN") or os.environ.get("GH_TOKEN")
    if token:
        headers["Authorization"] = "Bearer " + token
    return headers


def fetch_github_downloads(timeout):
    repository = os.environ.get("GITHUB_REPOSITORY", DEFAULT_REPOSITORY)
    total = 0
    page = 1
    while True:
        url = SOURCES["github"]["api"].format(repository=quote(repository, safe="/"))
        url += "?per_page=100&page={0}".format(page)
        releases = request_json(url, headers=github_headers(), timeout=timeout)
        if not isinstance(releases, list):
            raise SourceError("GitHub releases response was not a list")
        for release in releases:
            for asset in release.get("assets", []):
                total += int(asset.get("download_count") or 0)
        if len(releases) < 100:
            break
        page += 1
    return total


def fetch_hangar_downloads(timeout):
    project = SOURCES["hangar"]["project"]
    url = SOURCES["hangar"]["api"].format(project=quote(project, safe="/"))
    payload = request_json(url, headers={"User-Agent": "BukkitOldCombatMechanics-download-stats"}, timeout=timeout)
    stats = payload.get("stats") or {}
    value = stats.get("downloads")
    if value is None:
        raise SourceError("Hangar response did not include stats.downloads")
    return int(value)


def fetch_curseforge_downloads(timeout):
    api_key = os.environ.get("CURSEFORGE_API_KEY") or os.environ.get("CF_API_KEY")
    if not api_key:
        raise SourceError("CURSEFORGE_API_KEY is not set")
    slug = SOURCES["curseforge"]["slug"]
    url = SOURCES["curseforge"]["api"].format(slug=quote(slug, safe=""))
    payload = request_json(
        url,
        headers={
            "Accept": "application/json",
            "User-Agent": "BukkitOldCombatMechanics-download-stats",
            "x-api-key": api_key,
        },
        timeout=timeout,
    )
    matches = payload.get("data") or []
    if not matches:
        raise SourceError("CurseForge API returned no project for slug oldcombatmechanics")
    first = matches[0]
    value = first.get("downloadCount")
    if value is None:
        raise SourceError("CurseForge project did not include downloadCount")
    return int(value)


def parse_spigot_downloads(html):
    patterns = [
        r'"downloadCount"\s*:\s*"?([0-9][0-9,]*)"?',
        r'itemprop=["\']interactionCount["\'][^>]*content=["\']UserDownloads:([0-9][0-9,]*)["\']',
        r'Downloads\s*</[^>]+>\s*<[^>]+>\s*([0-9][0-9,]*)',
        r'Downloads[^0-9]{0,80}([0-9][0-9,]*)',
    ]
    for pattern in patterns:
        match = re.search(pattern, html, re.IGNORECASE | re.DOTALL)
        if match:
            return int(match.group(1).replace(",", ""))
    raise SourceError("Spigot page did not contain a recognised download count")


def fetch_spigot_downloads(timeout):
    resource_id = SOURCES["spigot"]["resource_id"]
    api_url = SOURCES["spigot"]["api"].format(resource_id=quote(resource_id, safe=""))
    try:
        payload = request_json(
            api_url,
            headers={"User-Agent": "BukkitOldCombatMechanics-download-stats"},
            timeout=timeout,
        )
        value = payload.get("downloads")
        if value is None:
            raise SourceError("Spiget response did not include downloads")
        return int(value)
    except (HTTPError, URLError, ValueError, KeyError, TypeError, SourceError):
        pass

    url = SOURCES["spigot"]["page"].format(resource_id=quote(resource_id, safe=""))
    html = request_text(
        url,
        headers={
            "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language": "en-GB,en;q=0.8",
            "User-Agent": "BukkitOldCombatMechanics-download-stats/1.0 (+https://github.com/kernitus/BukkitOldCombatMechanics)",
        },
        timeout=timeout,
    )
    return parse_spigot_downloads(html)


FETCHERS = {
    "github": fetch_github_downloads,
    "hangar": fetch_hangar_downloads,
    "curseforge": fetch_curseforge_downloads,
    "spigot": fetch_spigot_downloads,
}


def load_previous_stats(path=None, url=None, timeout=10):
    if path:
        with open(path, "r") as handle:
            return json.load(handle)
    if url:
        return request_json(url, headers={"User-Agent": "BukkitOldCombatMechanics-download-stats"}, timeout=timeout)
    return None


def previous_source(previous_stats, source_name):
    if not previous_stats:
        return None
    sources = previous_stats.get("sources") or {}
    source = sources.get(source_name)
    if not isinstance(source, dict):
        return None
    value = source.get("value")
    if isinstance(value, int):
        return source
    return None


def collect_source(name, timeout, previous_stats):
    metadata = SOURCES[name]
    try:
        value = FETCHERS[name](timeout)
        return {
            "label": metadata["label"],
            "status": "ok",
            "value": int(value),
            "warning": None,
        }
    except (HTTPError, URLError, ValueError, KeyError, TypeError, SourceError) as exc:
        previous = previous_source(previous_stats, name)
        warning = str(exc)
        if previous:
            return {
                "label": metadata["label"],
                "status": "reused",
                "value": int(previous["value"]),
                "warning": "reused previous value after collection failure: " + warning,
            }
        return {
            "label": metadata["label"],
            "status": "failed",
            "value": None,
            "warning": warning,
        }


def compact_total(value):
    value = int(value)
    units = [(1000000000, "B"), (1000000, "M"), (1000, "k")]
    for threshold, suffix in units:
        if value >= threshold:
            compact = float(value) / threshold
            if compact >= 100 or compact.is_integer():
                return "{0:.0f}{1}".format(compact, suffix)
            return "{0:.1f}{1}".format(compact, suffix)
    return str(value)


def write_json(path, payload):
    with open(path, "w") as handle:
        json.dump(payload, handle, indent=2, sort_keys=True)
        handle.write("\n")


def build_outputs(previous_stats, timeout):
    sources = {}
    for name in ("github", "spigot", "curseforge", "hangar"):
        sources[name] = collect_source(name, timeout, previous_stats)

    total = sum(source["value"] for source in sources.values() if isinstance(source.get("value"), int))
    failed = [name for name, source in sources.items() if source["status"] == "failed"]
    reused = [name for name, source in sources.items() if source["status"] == "reused"]
    generated_at = utc_now_iso()

    stats = {
        "updated_at": generated_at,
        "total": total,
        "sources": sources,
        "warnings": {
            "failed_sources": failed,
            "reused_sources": reused,
        },
    }
    badge = {
        "schemaVersion": 1,
        "label": "downloads",
        "message": compact_total(total),
        "color": "blue" if not failed else "yellow",
    }
    return badge, stats


def parse_args(argv):
    parser = argparse.ArgumentParser(description="Generate aggregate download-count badge JSON.")
    parser.add_argument("--output", default=DEFAULT_OUTPUT, help="Directory for downloads.json and download-stats.json")
    parser.add_argument("--previous", help="Path to a previous download-stats.json file")
    parser.add_argument(
        "--previous-url",
        default=os.environ.get("PREVIOUS_DOWNLOAD_STATS_URL"),
        help="URL of a previous download-stats.json file to reuse on partial failures",
    )
    parser.add_argument("--timeout", type=int, default=20, help="Per-request timeout in seconds")
    return parser.parse_args(argv)


def main(argv=None):
    args = parse_args(argv or sys.argv[1:])
    previous_stats = None
    previous_warning = None
    try:
        previous_stats = load_previous_stats(args.previous, args.previous_url, timeout=args.timeout)
    except (HTTPError, URLError, ValueError, OSError) as exc:
        previous_warning = "previous stats unavailable: " + str(exc)

    badge, stats = build_outputs(previous_stats, args.timeout)
    if previous_warning:
        stats.setdefault("warnings", {})["previous_stats"] = previous_warning

    if not os.path.isdir(args.output):
        os.makedirs(args.output)
    write_json(os.path.join(args.output, "downloads.json"), badge)
    write_json(os.path.join(args.output, "download-stats.json"), stats)

    if all(source["status"] == "failed" for source in stats["sources"].values()):
        print("Generated JSON, but every download source failed", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
