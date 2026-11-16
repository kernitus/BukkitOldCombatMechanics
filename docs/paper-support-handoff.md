# Paper support documentation handoff

Local draft only. No wiki, listing, release body or publication has been changed.

## Release boundary

Checked on 16 November 2026: GitHub lists v2.8.0 (26 October 2026) as the latest published release. It is the first stable release requiring Paper. Version 2.7.0 is the last release advertising Spigot support; that claim does not establish that every feature works on every Spigot version. Release Please PR #994 prepares 2.8.1.

## Copy for the wiki and download listings

Version 2.8.0 onwards requires Paper or a compatible fork such as Purpur with the required Paper capabilities. Plain Spigot and CraftBukkit are rejected before configuration or combat modules initialise. The Minecraft range remains 1.9 to latest, with Paper 1.9.4 representing the 1.9 family in validation. Folia support is not claimed.

Use the Java version required by your chosen server. OCM retains Java 8 plugin bytecode; building OCM requires JDK 25. These are separate requirements.

PacketEvents remains bundled. Failures produce bounded warnings naming the affected attack-sound suppression, sweep-particle suppression or sword client detection; unrelated combat features remain available. Modern public APIs are preferred, with lazy legacy adapters retained where equivalent public operations are unavailable.

SpigotMC and BukkitDev remain download venues. Their listings do not imply runtime support for plain Spigot or CraftBukkit from 2.8.0 onwards.

## Maintainer follow-up

- Replace any remaining unreleased wording in the wiki and listings with 2.8.0 as the first stable Paper-only release. The repository README now records this boundary.
- Copy the final curated changelog section into the matching Release Please PR body, retaining its parsed structure and issue-reporting footer. No external edit is authorised by this handoff.
- Prepare the 2.8.1 GitHub asset link, version string, update title and BBCode message for SpigotMC. The asset remains pending until publication.
- Keep Hangar's full Paper range and CurseForge's Bukkit-category version list. No distribution venue or Minecraft version has been removed here.

## Evidence and remaining checks

The [public API audit](../.agents/skills/compatibility-strategy/references/public-api-audit.md) records retained insertion, movement, identity, provenance and blocking adapters. The [client protocol evaluation](../.agents/skills/compatibility-strategy/references/client-protocol-evaluation.md) records why translated client classification still uses PacketEvents.

Real-client sword animation with animation enabled on Paper 26.3 and native, backend-translated and proxy-translated client observations remain unverified. Synthetic packet fixtures and server-side native-use tests cannot establish those results. The earlier Paper 1.12 reload failure is resolved by waiting for native login immunity in the test fixture; its combined targeted selection now reports 95 passed, zero failed and one explicit capability skip. The separate Paper 1.9.4 cooldown and shared-cause sweep numerical failures documented in `AGENTS.md` remain outside this targeted selection. These runs do not establish a clean full suite.
