<div align="center">

# TACZ-Enhanced-Patch

[![Version](https://img.shields.io/badge/Version-0.1.0-green)](https://github.com/JularDepick/TACZ-Enhanced-Patch/tree/v0.1.0)
[![Copyright](https://img.shields.io/badge/Copyright-JularDepick-0066AA)](./COPYRIGHT)
[![License](https://img.shields.io/badge/License-Apache--2.0-red)](./LICENSE)

[English] |
[简体中文](./README.md)

</div>

A Minecraft mod that tightens combat around TACZ as a standalone patch, turning armed hostile mobs into the main source of both threat and supply, and adding the ground-level gameplay the base mod leaves out

---

## Requirements

| Item | Requirement |
|:---:|:---:|
| Minecraft | 1.20.1 |
| Mod loader | Forge 47.4.23 or newer |
| Upstream mod | TACZ 1.1.8 or newer |
| Installation | Required on both client and server |

## Contents

### Gunners

| Mechanic | Description |
|:---:|:---|
| Armed spawning | Hostile mobs may carry TACZ guns based on difficulty, non-humanoid ones such as creepers included, with no new entity types |
| Return fire | Any armed mob that gets hit locks onto the attacker, and can be limited to hostile targets so allies do not fight each other |
| Improvised arming | Passive, neutral and friendly mobs may grab a random gun below half health and turn on whatever hurt them |
| Ammo and attachments | Every gun rolls its own attachments and limited ammo, then falls back to its vanilla weapon once dry to give players a window |
| Toughness | Regular gunners gain tiered health and roll each of the four armor pieces independently from leather up to netherite, while improvised mobs only get the gun |
| Loot | Kills always drop matching ammunition scaled by the reserve left, plus a chance at the exact gun, a random attachment and high tier ammo |

### Ruins

| Mechanic | Description |
|:---:|:---|
| Ruined outpost | An 11 by 11 collapsed outpost generates in the wild, with a chest of ammunition and attachments and a spawner that produces guards |
| Terrain fitting | The site requires an even surface, fills low spots, shaves high ones, and never generates at or below sea level |

### Client features

| Feature | Description |
|:---:|:---|
| Incoming fire indicator | When hit by a gun, a soft-edged arc on screen points to the source, up to three directions at once, off by default |
| Heading ruler | A horizontal compass tape flush with the top edge, nine tenths of the screen wide, showing cardinal directions and a bare bearing number, off by default |

### Configuration and permissions

| Item | Description |
|:---:|:---|
| Client configuration | Local preferences such as automatic reload, the incoming fire indicator and the heading ruler, stored per player |
| Server configuration | 28 parameters grouped into basics, retaliation, spawning, targeting, ammo, toughness, loot, ruins and debug, with switches listed before numeric values inside each group and synced from the server |
| Configuration screen | Opening it first offers a client or a server entry, and the server entry only appears for the world owner |
| Shortcut | C opens the mod config entry screen by default; it is a standard key binding, so it can be rebound or cleared in the vanilla controls |
| Armed mob list | The server page scans every mob into one switchable list and lets group toggles flip those switches in bulk, deciding only which mobs may carry guns |

## Gameplay highlights

- Gunner ammo is limited, so forcing a reload or a dry weapon is the main tactic
- Hitting an armed mob makes it return fire at once, so ambushes need a way out
- Think before hitting passive mobs: below half health they may pick up a gun, and both the chance and the threshold are configurable
- High-tier guns are rare and much tougher to kill, so armor-piercing rounds and headshots are the fast answer
- Ruin guards come from a spawner plus the gunner system, so clear the guards before looting
- Disabling the gunner master switch returns everything to vanilla and leaves saves untouched

## Configuration

Every server parameter belongs to the world owner and is written to disk as soon as it is changed in the screen; the client only keeps local preferences such as automatic reload and the incoming fire indicator. Runtime file locations and the full parameter list are in the [developer documentation](./docs/developer/README.md)

## Compatibility

- Requires 1.20.1 and Forge 47.x, with TACZ 1.1.8 or newer as the upstream mod
- Interacts with TACZ only through its public API and events, never modifying its source or injecting mixins
- Saves still load when the mod is disabled or removed, because gunner state lives in entity persistent data
- Coexists with spawning mods: it only reads spawn events and fills the main hand, without changing other mods spawn rules

## More documentation

| Document | Purpose |
|:---:|:---:|
| [Developer documentation](./docs/developer/README.md) | Building, running and the TACZ integration rules |
| [Source layout](./src/README_en-US.md) | Source and resource structure with the responsibility of each package |

## Contributing

See [CONTRIBUTING.md](./CONTRIBUTING_en-US.md)

## Copyright

Copyright &copy; 2026 JularDepick

See [COPYRIGHT](./COPYRIGHT)

## License

This repository is licensed under the [Apache-2.0 License](./LICENSE)

## Links

- CurseForge mod page: https://www.curseforge.com/minecraft/mc-mods/TACZ-Enhanced-Patch
- Modrinth mod page: https://modrinth.com/mod/TACZ-Enhanced-Patch
- TACZ mod: https://www.curseforge.com/minecraft/mc-mods/timeless-and-classics-zero
