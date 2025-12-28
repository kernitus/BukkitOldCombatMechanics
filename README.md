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

A performant Spigot/Paper plugin to configure combat-related mechanics for Minecraft 1.9 upwards.

It is extremely modular, so you can use it just to customise weapon, armour damage, gapples, sword blocking and more even if you do not care about attack cooldown. If you do want to remove cooldowns, modesets let you build per-world and per-player presets, which is ideal for minigames and mixed rulesets.

<hr/>

<a href="https://hangar.papermc.io/kernitus/OldCombatMechanics">
    <img src="res/paper.png" alt="Paper" height="100">
</a>
<a href="https://www.spigotmc.org/resources/19510/">
    <img src="res/spigot.png" alt="Spigot" height="100">
</a>
<a href="https://dev.bukkit.org/projects/oldcombatmechanics">
    <img src="res/bukkit.png" alt="Bukkit" height="100">
</a>

## 🧰 Modesets
Modesets are feature-presets and can be configured for your players to choose from. Each modeset can have any combination of the below features enabled, with examples provided to replicate 1.9 vs 1.8 combat. Players can switch between modesets via command, choosing from the modesets allowed for the world they are in.

## ⚙ Configurable Features
Features are grouped in `module`s as listed below, and can be individually configured and disabled. Modules that are fully disabled will have no impact on server performance.

#### ⚔ Combat
- Attack cooldown
- Attack frequency
- Tool damage
- Critical hits
- Player regen

#### 🤺 Armour
- Armour strength
- Armour durability

#### 🛡 Swords & Shields
- Sword blocking
- Shield damage reduction
- Sword sweep
- Sword sweep particles

#### 🌬 Knockback
- Player knockback
- Fishing knockback
- Fishing rod velocity
- Projectile knockback

#### 🧙 Gapples & Potions
- Golden apple crafting & effects
- Potion effects & duration
- Chorus fruit

#### ❌ New feature disabling
- Item crafting
- Offhand
- New attack sounds
- Enderpearl cooldown
- Brewing stand refuel
- Burn delay

## 🔌 Plugin Compatibility
Most plugins will work fine with OCM. Some are explicitly supported, including:
- Placeholder API (see [wiki](https://github.com/kernitus/BukkitOldCombatMechanics/wiki/PlaceholderAPI) for details)

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
