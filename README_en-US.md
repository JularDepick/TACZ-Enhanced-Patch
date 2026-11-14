<div align="center">

# TACZ-Enhanced-Patch

[![Version](https://img.shields.io/badge/Version-0.2.0-green)](https://github.com/JularDepick/TACZ-Enhanced-Patch/tree/v0.2.0)
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
| Java | 17 or newer, required to run the game |
| Installation | Required on both client and server |

## Contents

### Gunners

| Mechanic | Description |
|:---:|:---|
| Armed spawning | Hostile mobs may carry TACZ guns based on difficulty, non-humanoid ones such as creepers included, with no new entity types |
| Armed appearance | Non-humanoid mobs gain a humanoid skeleton layer with the gun drawn in its hand, so an armed one is obvious at a glance |
| Return fire | Any armed mob that gets hit locks onto the attacker, and can be limited to hostile targets so allies do not fight each other |
| Improvised arming | Passive, neutral and friendly mobs may grab a random gun below the configured health threshold and turn on whatever hurt them; humanoid and non-humanoid chances are configured separately |
| Ammo and attachments | Every gun rolls its own attachments and limited ammo, then falls back to its vanilla weapon once dry to give players a window |
| Toughness | Regular gunners gain tiered health and roll each of the four armor pieces independently from leather up to netherite, while improvised mobs only get the gun |
| Loot | Kills always drop matching ammunition scaled by the reserve left, plus a chance at the exact gun, a random attachment and high tier ammo |
| Power and drops | Killing any armed mob multiplies its vanilla drops by one plus the mob power over the killer power, using the peak power of the fight and capped by config |

### Armed grunts

| Mechanic | Description |
|:---:|:---|
| Neutral spawning | Spawns as part of ruin garrisons, attacks nothing on its own and only flees when hit; faction guards are the exception |
| Always armed | Carries a random gun from the moment it spawns, ignoring the gunner master switch and the mob list |
| Supply drops | Kills always drop matching ammo, with a chance at the gun and an attachment, plus random food and scrap in random amounts |
| Appearance | A zombie villager body with a hand-drawn camouflage texture, complete with a head, limbs and facial features, holding a visible gun |
| Ruin guards | Guards bind to the ruin faction, fight back in melee inside their territory and ride tanks as a combined unit |
| Attributes | Health and armor follow difficulty presets at a quarter of the tank and ignore mod config |
### Ruins

| Mechanic | Description |
|:---:|:---|
| Building blueprints | The building itself is a WorldEdit .schem file, either under `src/main/ruins/` or in the `tacz_enhanced_patch_ruins` folder of the world server config directory, one is picked at random per ruin; built-in files must be .schem while the external folder also accepts legacy .schematic |
| Framed pasting | Blocks are written chunk by chunk across ticks and the space under the building is filled with a stone foundation, so a large ruin appears as the player approaches |
| Terrain fitting | The site requires a surface variation of at most two blocks around its center, never generates at or below sea level, and keeps a configurable straight-line distance (640 blocks by default) from existing ruins |
| Chests and supply crates | The loot chest is placed at a covered spot inside the building; supply crates are scattered across the floors by chance and hold guns, ammo, armor and food with nothing guaranteed |
| Banner and factions | A vanilla banner marks the building center and its color is the faction, so same-colored ruins are allied while different colors may ally or turn hostile |
| Territory and reinforcement | A cylinder centred on the banner height whose height equals its horizontal diameter, with the radius fitted to the building; the garrison is reinforced at a fixed interval until it reaches the threshold |
| Capture | Players inside the core area start a shared countdown while the garrison is thin, leaving the core for more than five seconds aborts it while the countdown runs backwards in the meantime, and the garrison strength decides the outcome; every start, abort, failure and success reports the concrete reason and numbers; surviving guards become routed troops with reduced combat power |
| Capture loot | A supply chest appears in the core area, with ammo and weapons converted from the kill tally plus experience for each participant |
| Reward chest | Always holds a matching armor set and a sword, each piece has a fifty percent chance of strength-scaled enchantments, plus ender pearls, a chance at eyes of ender and bread |
| Banner souvenir | After a successful capture the banner can be broken and dropped, named after its color and coordinates, and its tooltip records the ruin coordinates plus every participant |
| Ruin compass | Crafted from an emerald next to a compass; right-click records the nearest ruin and reports the distance, direction and bearing, while the needle keeps pointing at it |
| Garrison readout | While inside a ruin the top right of the screen shows the remaining garrison percentage, refreshed every 20 ticks and coloured by how dangerous the number is |
| Top notice | After the first five seconds the capture countdown switches to a single centred line at the top, placed below the heading ruler readout and moved up when the ruler is off |

### Tank elite

| Mechanic | Description |
|:---:|:---|
| Elite shelling | A rarely spawning tank-shaped monster that guards its territory when it belongs to a faction, otherwise retaliates only, and fires periodic shells with far larger explosions than a ghast fireball |
| Imposing bulk | A 3.4 x 2.4 hitbox with a custom model whose tracks sit on the ground and whose hull and turret stack upwards, immune to fire and fully knockback resistant |
| Loot | Drops iron, copper and gunpowder; cooldown and cannon range are configurable while health and power follow difficulty presets |
| Combined unit | Up to four grunts ride on the shoulders, members share a health and armor bonus derived from their total health, and a faction tank revives factionless up to three times |
| Player driving | A factionless tank can be mounted, aimed with the crosshair, driven with WASD and fired with the space bar |
### Armed neutral villagers

| Mechanic | Description |
|:---:|:---|
| Spawning | Villagers roll for a gun on spawn, fifty percent by default, covering natural spawns, breeding and curing, with wandering traders excluded |
| Stance | Never attacks first; retaliates against a player only when the health crosses the threshold, and against mobs once health drops below ninety percent |
| Trading | Uses the vanilla trade screen with emeralds; goods come from the guns and ammo it carries, with a total of twice the carried amount |
| Discount | Once the backpack is empty the remaining quota sells at half price and is conjured by the system |
| Degrading | An empty backpack turns the villager back into a vanilla villager and voids the discounted quota |

### Fast extended magazines

| Mechanic | Description |
|:---:|:---|
| Item tab | A dedicated creative tab lists fast variants of the upstream extended mags, reusing the same items without registering new item IDs and appending a fast magazine suffix to the original name |
| Reload speed | Reload time = base time × missing ammo ratio × 50%, 40% or 30% for ammo capacity III to I, with the animation sped up as well |

### Patch item tab and infinite magazine

| Mechanic | Description |
|:---:|:---|
| Patch item tab | A TACZ Enhanced Patch creative tab collecting loose items and spawn eggs |
| Infinite magazine | Built on the upstream level III extended mag, inheriting its icon and attributes, consuming no ammo from the magazine or inventory, with capacity labelled as the infinity symbol |
### Client features

| Feature | Description |
|:---:|:---|
| Incoming fire indicator | When hit by a gun, a soft-edged arc on screen points to the source, up to three directions at once, off by default |
| Heading ruler | A horizontal compass tape flush with the top edge, nine tenths of the screen wide, showing cardinal directions and a bare bearing number, off by default |
| Ruler color | The accent color is plain red by default and can be set to any 6-digit hexadecimal RGB value in the client config |

### Configuration and permissions

| Item | Description |
|:---:|:---|
| Client configuration | Local preferences such as automatic reload, the incoming fire indicator, the heading ruler and its color, stored per player |
| Server configuration | 68 parameters grouped into debug, ruins, armed neutral villagers, basics, retaliation, spawning, targeting, ammo, toughness, loot, armed grunt and tank elite, with pure-button and switch groups listed before numeric ones and synced from the server |
| Configuration screen | Opens the config screen directly, with tabs at the top to switch between client and server; the server tab only appears for the world owner |
| Shortcut | Shift + C opens the config screen by default; it is a standard key binding, so it can be rebound or cleared in the vanilla controls |
| Mob arming config | The first row of the server page scans every mob into one switchable list, shows each mob's name and entity ID, and lets group toggles flip those switches in bulk |

## Gameplay highlights

- Gunner ammo is limited, so forcing a reload or a dry weapon is the main tactic
- Hitting an armed mob makes it return fire at once, so ambushes need a way out
- Think before hitting passive mobs: below half health they may pick up a gun, and both the chance and the threshold are configurable
- High-tier guns are rare and much tougher to kill, so armor-piercing rounds and headshots are the fast answer
- Ruin guards are reinforced over time, so clear them before looting; supply crates sit on the floors along the way
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
- Building blueprint format reference: https://github.com/EngineHub/WorldEdit