<!--
     This Source Code Form is subject to the terms of the Mozilla Public
     License, v. 2.0. If a copy of the MPL was not distributed with this
     file, You can obtain one at https://mozilla.org/MPL/2.0/.
-->

<p align="center">
<img src="res/ocm-icon.png" width=320>
<img src="res/ocm-banner.png" width=1000>
</p>

## by kernitus and Rayzr522

Fine‑tune Minecraft combat, movement, and item balance without breaking your server. OldCombatMechanics is a free, open‑source toolkit for Spigot & Paper that lets you mix 1.8‑style snappiness with modern features, per world and per player.

**Why servers pick OCM** ✨
- 🧩 **Modular:** enable only what you need: cooldowns, tool damage, knockback, shields, potions, reach, sounds, more.
- 🚀 **Performant:** lean listeners only enabled as needed; reflection lookups are cached and recurring tasks are minimised (shared where possible) to keep tick time low on busy PvP servers.
- 🗺️ **Modesets:** ship different rules for different worlds or players; perfect for mixed PvP/PvE, minigames, or duels.
- ⏪ **Backwards‑friendly:** runs on Java 8+, supports 1.9 to latest; integrates cleanly with PlaceholderAPI and PacketEvents.
- ✅ **Tested for you:** live integration tests run real Paper servers across multiple versions every build.
- 💸 **Zero cost:** fully open source, optional basic telemetry (bStats only), no paywalls.

**Quick start** ⚡
1. Drop the jar into `plugins/` (Spigot or Paper-derivatives 1.9+).
2. Restart and edit `config.yml` to pick your modules and modesets.
3. Use `/ocm reload` to apply changes instantly.
4. Hand players `/ocm modeset <name>` to let them choose their ruleset.

<p align="center">
  <a href="https://hangar.papermc.io/kernitus/OldCombatMechanics">
    <img src="res/paper.png" alt="Paper" height="100">
  </a>
  <a href="https://www.spigotmc.org/resources/19510/">
    <img src="res/spigot.png" alt="Spigot" height="100">
  </a>
  <a href="https://dev.bukkit.org/projects/oldcombatmechanics">
    <img src="res/bukkit.png" alt="Bukkit" height="100">
  </a>
</p>

<hr/>

## 🧰 Modesets
- Per-player/per-world presets that decide which features are active; each world has an allowed list and a default modeset.
- Let players pick ( `/ocm modeset <name>` ) to run, for example, 1.8-style PvP in an arena world while keeping vanilla rules in survival.

## ⚙ Configurable Features
Features are grouped in `module`s as listed below, and can be individually configured and disabled. Disabled modules will have no impact on server performance.

#### ⚔ Combat
*Tweak timing, damage, and reach.*
- **Attack cooldown:** adjust or remove 1.9+ cooldown
- **Attack frequency:** set global hit delay
- **Tool damage:** pre-1.9 weapon values
- **Attack range (Paper 1.21.11+):** 1.8-style reach
- **Critical hits:** control crit multiplier
- **Player regen:** tune regen rates

#### 🤺 Armour
*Balance defence and wear.*
- **Armour strength:** scale armour protection
- **Armour durability:** change durability loss

#### 🛡 Swords & Shields
*Control block and sweep behaviour.*
- **Sword blocking:** restore old right-click block; on Paper 1.21.2+ we also add the native sword blocking animation via the consumable component
- **Shield damage reduction:** scale shield protection
- **Sword sweep:** enable or disable sweeps
- **Sword sweep particles:** hide or show sweep visuals

#### 🌬 Knockback
*Shape knockback per source.*
- **Player knockback:** adjust PvP knockback
- **Fishing knockback:** fishing-rod knockback
- **Fishing rod velocity:** pull speed
- **Projectile knockback:** arrows and other projectiles

#### 🧙 Gapples & Potions
*Change consumable power.*
- **Golden apple crafting and effects:** notch and normal
- **Potion effects and duration:** old-style values
- **Chorus fruit:** teleport behaviour and range

#### ❌ New feature disabling
*Toggle later-version mechanics.*
- **Item crafting:** block selected recipes
- **Offhand:** disable offhand use
- **New attack sounds:** mute new swing sounds
- **Enderpearl cooldown:** enable or remove cooldown
- **Brewing stand refuel:** alter fuel use
- **Burn delay:** adjust fire tick delay

## 🔌 Compatibility & Testing
- OCM targets Spigot 1.9+ and runs on Java 8 and up.
- We stick to Spigot/Paper APIs for forward compatibility; NMS/reflection is used only when necessary.
- Integration tests boot real servers on 1.9.4, 1.12, 1.19.2, and 1.21.11 each build to verify behaviour.
- Most plugins work fine with OCM. Explicitly tested integrations include PlaceholderAPI (see [wiki](https://github.com/kernitus/BukkitOldCombatMechanics/wiki/PlaceholderAPI)).

## 🧾 Licence
- Source code in this repository is under the Mozilla Public License 2.0 (MPL‑2.0).
- Pre-built jars bundle PacketEvents (GPLv3). Those binary distributions are provided under GPLv3 terms due to the included dependency.
- If you build a jar without PacketEvents, you may distribute that build under MPL‑2.0, subject to its terms.

## ⚡ Development Builds
Oftentimes a particular bug fix or feature has already been implemented, but a new version of OCM has not been released
yet. You can find the most up-to-date version of the plugin
on [Hangar](https://hangar.papermc.io/kernitus/OldCombatMechanics/versions?channel=Snapshot&platform=PAPER).


## 🤝 Contributions
If you are interested in contributing, please [check this page first](.github/CONTRIBUTING.md).
<hr/>


<a href="https://bstats.org/plugin/bukkit/OldCombatMechanics">
    <img src="https://bstats.org/signatures/bukkit/OldCombatMechanics.svg" alt="bStats">
</a>
