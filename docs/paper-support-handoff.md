# Paper support documentation handoff

Local draft only. No wiki, listing, release body or publication has been changed.

## Release boundary

Checked on 26 October 2026: GitHub lists v2.7.0 (3 October 2026) as the latest published release, and its tagged README advertises Spigot support. The working build is `2.7.1-SNAPSHOT`. The first stable release containing the Paper requirement is unassigned; do not present the snapshot number as a confirmed release number or promise future Spigot releases. Version 2.7.0 is the latest published release advertising Spigot support, not proof that every feature works on every Spigot version.

## Copy for the wiki and download listings

Current development builds require Paper or a compatible fork such as Purpur with the required Paper capabilities. Plain Spigot and CraftBukkit are rejected before configuration or combat modules initialise. The Minecraft range remains 1.9 to latest, with Paper 1.9.4 representing the 1.9 family in validation. Folia support is not claimed.

Use the Java version required by your chosen server. OCM retains Java 8 plugin bytecode; building OCM requires JDK 25. These are separate requirements.

PacketEvents remains bundled. Failures produce bounded warnings naming the affected attack-sound suppression, sweep-particle suppression or sword client detection; unrelated combat features remain available. Modern public APIs are preferred, with lazy legacy adapters retained where equivalent public operations are unavailable.

SpigotMC and BukkitDev remain download venues. Their listings do not imply runtime support for plain Spigot or CraftBukkit in development builds containing this change.

## Maintainer follow-up when a release is assigned

- Replace unreleased wording with the actual first stable Paper-only version in the wiki, README and listings. Confirm the latest preceding Spigot-supporting release at that time.
- Copy the final curated changelog section into the matching Release Please PR body, retaining its parsed structure and issue-reporting footer. No external edit is authorised by this handoff.
- Prepare the version-specific GitHub asset link, version string, update title and BBCode message for SpigotMC only after the release version is known. No ready-to-publish asset URL exists for this unassigned release.
- Keep Hangar's full Paper range and CurseForge's Bukkit-category version list. No distribution venue or Minecraft version has been removed here.

## Evidence and remaining checks

The [public API audit](../.agents/skills/compatibility-strategy/references/public-api-audit.md) records retained insertion, movement, identity, provenance and blocking adapters. The [client protocol evaluation](../.agents/skills/compatibility-strategy/references/client-protocol-evaluation.md) records why translated client classification still uses PacketEvents.

Real-client sword animation with animation enabled on Paper 26.3 and native, backend-translated and proxy-translated client observations remain unverified. Synthetic packet fixtures and server-side native-use tests cannot establish those results. Existing legacy numerical failures remain visible in test evidence and must not be represented as a clean full suite.
