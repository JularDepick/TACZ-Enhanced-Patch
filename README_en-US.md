<div align="center">

# TACZ-Enhanced-Patch

[![Version](https://img.shields.io/badge/Version-0.3.0-green)](https://github.com/JularDepick/TACZ-Enhanced-Patch/tree/v0.3.0)
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
| Neutral spawning | Spawns as part of ruin garrisons, attacks nothing on its own and only flees when hit |
| Always armed | Carries a random gun from the moment it spawns, ignoring the gunner master switch and the mob list |
| Supply drops | Kills always drop matching ammo, with a chance at the gun and an attachment, plus random food and scrap in random amounts |
| Appearance | A zombie villager body with a hand-drawn camouflage texture, complete with a head, limbs and facial features, holding a visible gun |
| Camouflage | Four camo textures, green, desert, urban and snow, one picked per individual at spawn and kept for its whole life |
| Ruin guards | Guards bind to the ruin faction, fight back in melee inside their territory and ride tanks as a combined unit |
| Attributes | Health and armor follow difficulty presets at a quarter of the tank and ignore mod config |
### Ruins

| Mechanic | Description |
|:---:|:---|
| Building blueprints | The building itself is a WorldEdit .schem file, either the built-in blueprints shipped with the mod or the ones in the `tacz_enhanced_patch_ruins` folder of the world server config directory, one is picked at random per ruin; built-in files must be .schem while the external folder also accepts legacy .schematic |
| Built with chunks | Ruins are vanilla structures: the structure set picks the site and the blocks are written while chunks generate, so nothing runs on the server tick; parts spanning several chunks appear as those chunks generate |
| Terrain fitting | The site requires a surface variation of at most two blocks around its center and never generates at or below sea level; sites sit on a 34 by 34 chunk grid with at most one ruin per cell and a minimum of eight chunks between neighbouring cells |
| Interruption recovery | A chunk is not saved until it finishes generating, so a crash or an early exit simply regenerates it from the seed with the same result; site data is materialized from the structure start when a player comes near, with every value derived deterministically from the seed |
| Chests and supply crates | The loot chest is placed at a covered spot inside the building; supply crates are scattered across the floors by chance and hold guns, ammo, armor and food with nothing guaranteed |
| Banner and factions | A vanilla banner marks the building center and its color is the faction, so same-colored ruins are allied while different colors may ally or turn hostile |
| Territory and reinforcement | A cylinder centred on the banner height whose height equals its horizontal diameter, with the radius fitted to the building; the garrison is reinforced at a fixed interval until it reaches the threshold |
| Dead ruins | A captured ruin stops reinforcing, stops reporting entry and capture, and stops producing supply crates, yet still occupies its cell of the placement grid |
| Capture | Players inside the core area start a shared countdown while the garrison is thin, leaving the core for more than five seconds aborts it while the countdown runs backwards in the meantime, and the garrison strength decides the outcome; every start, abort, failure and success reports the concrete reason and numbers; surviving guards become routed troops with reduced combat power |
| Capture loot | A supply chest appears in the core area, with ammo and weapons converted from the kill tally plus experience for each participant |
| Reward chest | Always holds a matching armor set and a sword, each piece has a fifty percent chance of strength-scaled enchantments, plus ender pearls, a chance at eyes of ender and bread |
| Banner souvenir | After a successful capture the banner can be broken and dropped, named after its color and coordinates, and its tooltip records the ruin coordinates plus every participant |
| Ruin compass | Crafted from an emerald next to a compass; right-click queries the nearest ruin and reports the distance, coordinates, building name and generation state, while the needle keeps pointing at it; sites are indexed from the world seed, so ruins whose chunks do not exist yet are still found, and the index grows as players move and queries run |
| Garrison readout | While inside a ruin the top right of the screen shows the remaining garrison percentage, refreshed every 20 ticks and coloured by how dangerous the number is; it moves down for potion effects and stacks above the grenade panel |
| Top notice | After the first five seconds the capture countdown switches to a single centred line at the top, placed below the heading ruler readout and moved up when the ruler is off |

### Tank elite

| Mechanic | Description |
|:---:|:---|
| Elite shelling | A rarely spawning tank-shaped monster that guards its territory when it belongs to a faction, otherwise retaliates only, and fires periodic shells with far larger explosions than a ghast fireball |
| Imposing bulk | A 3.4 x 2.4 hitbox with a custom model whose tracks sit on the ground and whose hull and turret stack upwards, immune to fire and fully knockback resistant |
| Camouflage | The same four camo textures as the grunt, one picked per individual at spawn |
| Loot | Drops iron, copper and gunpowder; cooldown and cannon range are configurable while health and power follow difficulty presets |
| Combined unit | Up to four grunts ride on the hull roof, members share a health and armor bonus derived from their total health, a raised maximum also tops up the current health by the same amount, and a faction tank revives factionless up to three times |
| Player driving | A factionless tank can be mounted, aimed with the crosshair, driven with WASD and fired with the space bar |
### Armed neutral villagers

| Mechanic | Description |
|:---:|:---|
| Spawning | Villagers roll for a gun on spawn, fifty percent by default, covering natural spawns, breeding and curing, with wandering traders excluded |
| Stance | Never attacks first; retaliates against a player only when the health crosses the threshold, and against mobs once health drops below ninety percent |
| Trading | Uses the vanilla trade screen with emeralds; goods come from the guns and ammo it carries, with a total of twice the carried amount |
| Discount | Once the backpack is empty the remaining quota sells at half price and is conjured by the system |
| Degrading | An empty backpack turns the villager back into a vanilla villager and voids the discounted quota |

### Grenades

| Mechanic | Description |
|:---:|:---|
| Single item | One grenade item for the whole mod with a stack size of 64; its six properties live on the stack itself and show in the tooltip |
| Colors | Sixteen vanilla banner colors plus four camouflage tones, affecting only the item tint and the readouts, cycling with overflow |
| 3D appearance | The grenade uses a three-dimensional item model and the color only tints the item texture; the projectile is oriented by its flight direction and no longer follows the camera |
| Charged throw | Hold the right button to charge and release to throw; charging caps at one second, the hand pose switches to a bow draw and the field of view narrows with the charge |
| Launch speed | Linear in charge between the server configured bounds, four to sixteen blocks per second by default and adjustable to four to sixty-four |
| Ballistics | Motion is handed to the vanilla projectile logic, so gravity and drag use the game's own values while the position syncs every tick |
| Sticky | When enabled the grenade stops on its first collision, resting on a block or following a mob or player until the fuse runs out |
| Adaptive bounce | When sticky is off the bounce follows the block hardness, harder blocks bounce less, and a low normal speed comes to rest |
| Six properties | Nine fixed keys switch fuse, power, targets, sticky, effects and color while holding it; paired keys stop at the bounds |
| Fuse | Counted from the moment the right button goes down, either three to ten seconds or impact; an impact fuse detonates on the first block or mob it touches |
| Explosion | Power zero to sixteen maps to a zero to sixteen block radius and zero to sixty-four points of center damage, falling off linearly with distance; the kill volume is an oblate spheroid whose vertical radius is two thirds of the horizontal one, and damage plus knockback expand outwards four blocks per tick instead of resolving instantly |
| Armor penetration | Forty percent for grenades and sixty percent for mortar shells, not configurable; the pierced part skips armor points only, so enchantment protection still applies |
| Knockback | Displacement equals half the horizontal radius, strongest at the center and zero at the rim, independent of the target tier |
| Block damage | The break radius is the kill radius plus a wave edge of zero to a quarter of it; power decays linearly to half at the edge and only blocks whose hardness does not exceed the decayed power are destroyed |
| Targets | Eight combinations: no damage, terrain only, players only, mobs only, no terrain, no players, no mobs, all |
| Panel and countdown | The right side lists grenade, fuse, power, targets, sticky, effects and color; after a throw the two newest fuses are shown side by side above the hotbar, while impact grenades have no countdown and are not shown |
| Crafting | Iron ingots in the four corners, gunpowder in a cross, string in the center, four grenades per craft |

### Mortar

| Mechanic | Description |
|:---:|:---|
| Block | One block tall and must rest on a solid block; the base is a thin plate with a short pillar while the barrel is adjustable, and losing its support drops the whole mortar like an anvil, landing with its state intact |
| State carried | Yaw, pitch and caliber are stored on the block and on the dropped item, so placing it again keeps the last tuning and the tooltip lists all three |
| Angles | Yaw is measured in world heading, zero to three hundred sixty, in the same convention as the heading ruler; pitch runs zero to ninety and a fresh mortar starts at forty-five |
| Loading and firing | Right-click with a mortar shell to load, right-click a loaded mortar to fire one second later, left-click to eject the shell |
| Armor penetration | Mortar shells pierce sixty percent of armor, sharing the same explosion resolution as grenades |
| Aiming | Empty hand left-click raises yaw by five degrees, sneaking left-click raises pitch; empty hand right-click enters driving |
| Driving | Yaw follows the view, A and D turn the player and drag the yaw along, W and S adjust pitch, Z and X change caliber, R and space fire, sneak exits |
| Caliber | Sixty to two hundred forty millimetres, mapped linearly to sixty to two hundred forty blocks per second |
| Panel | The left side lists heading, pitch, caliber, range and flight time with their keys, two decimals throughout, and the heading is shown in world bearing |
| Remote | Bind or unbind with the remote item, then right-click air to control it; several remotes may bind the same mortar |

### Phantom Elite

| Mechanic | Description |
|:---:|:---|
| Body | A new flying entity reusing the vanilla phantom model and flight, textured in the same camouflage palette as the grunt and the tank |
| Camouflage | Four camo textures, one picked per individual at spawn |
| Patrol | Circles alternately along an inner and an outer ring of the ruin territory, keeping eight to sixteen blocks above the ground of the target column; individuals without a ruin circle their home at the same cruise height |
| Alert | Notices survival players or hostile faction mobs entering the territory, sounds an alarm and reports the target to the rest of the garrison |
| Throwing | Throws players-only grenades at players and mobs-only grenades at hostile mobs, with no bounce and random charge and fuse |
| Role | Counts toward the garrison base and is rolled during reinforcement at three grunts to one tank to one phantom |
| Loot | Drops one or two grenades, with looting raising the count slightly |

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
| Health bars | A bar plus current and maximum health above every mob and player, green when healthy and red when low, on by default; only mobs within 16 blocks of the player are shown and any mob hidden behind blocks is skipped; drawn at the end of the level pass without depth testing so clouds and other mobs never cover them |
| Ruler color | The accent color is plain red by default and can be set to any 6-digit hexadecimal RGB value in the client config |

### Configuration and permissions

| Item | Description |
|:---:|:---|
| Client configuration | Local preferences such as automatic reload, the incoming fire indicator, mob health bars, the heading ruler and its color, stored per player |
| Server configuration | 76 parameters adjustable in the screen plus 5 keys that live only in the config file, grouped into debug, ruins, armed neutral villagers, grenade and phantom, basics, retaliation, spawning, targeting, ammo, toughness, loot, armed grunt and tank elite, with pure-button and switch groups listed before numeric ones and synced from the server; the per-ruin size caps live in a separate world generation config file bound to the save and cannot be changed in game |
| Configuration screen | Opens the config screen directly, with tabs at the top to switch between client and server; the server tab only appears for the world owner |
| Shortcut | Shift + C opens the config screen by default; it is a standard key binding, so it can be rebound or cleared in the vanilla controls |
| Mob arming config | The first row of the server page scans every mob into one switchable list, shows each mob's name and entity ID, and lets group toggles flip those switches in bulk |

## Gameplay highlights

- Gunner ammo is limited, so forcing a reload or a dry weapon is the main tactic
- Hitting an armed mob makes it return fire at once, so ambushes need a way out
- Think before hitting passive mobs: below half health they may pick up a gun, and both the chance and the threshold are configurable
- High-tier guns are rare and much tougher to kill, so armor-piercing rounds and headshots are the fast answer
- Ruin guards are reinforced over time, so clear them before looting; supply crates sit on the floors along the way
- A grenade can be set to hit terrain only or players only, so pick the target mode before throwing in tight spaces
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