# Source Layout

[[简体中文]](./README.md) |
[English]

This directory holds the functional source code of the mod, while the rest of the repository is auxiliary or documentation content

```
src/main/
├── java/com/taczenhancedpatch/
│   ├── TACZEnhancedPatch.java            # Mod entry point
│   ├── balance/
│   │   ├── GunDamageBalanceHandler.java  # Damage adjustment for gunner bullets
│   │   └── GunnerDefenseHandler.java     # Damage adjustment for hits on gunners
│   ├── client/
│   │   ├── AutoReloadHandler.java        # Automatic reload
│   │   ├── ConfigAccess.java             # World owner check
│   │   ├── ConfigShortcutHandler.java    # Config entry key binding (vanilla)
│   │   ├── InfiniteMagTooltipHandler.java # Infinite magazine tooltip
│   │   ├── TACZEnhancedPatchClient.java  # Client entry point
│   │   ├── config/
│   │   │   ├── ConfigHubScreen.java      # Configuration entry screen
│   │   │   ├── MobListScreen.java        # Armed mob list screen
│   │   │   ├── ModConfigScreen.java      # Configuration screen registration
│   │   │   ├── PatchConfigScreen.java    # Parameter list screen
│   │   │   └── ScrollBar.java            # Draggable scroll bar
│   │   ├── hud/
│   │   │   └── HeadingRulerOverlay.java  # Heading ruler rendering
│   │   └── render/
│   │       ├── GruntRenderer.java        # Armed grunt renderer
│   │       ├── TankModel.java            # Tank model
│   │       └── TankRenderer.java         # Tank elite renderer
│   ├── config/
│   │   ├── ClientConfig.java             # Client configuration
│   │   ├── ConfigEvents.java             # Configuration lifecycle and saving
│   │   ├── ConfigOption.java             # Configuration option descriptor
│   │   ├── ConfigOptionGroup.java        # Configuration groups and UI order
│   │   ├── MobListConfig.java            # Armed mob list with per-entry switches
│   │   ├── MobMiddleConfig.java          # Seven armed group switches
│   │   └── ServerConfig.java             # Server configuration and option list
│   ├── debug/
│   │   └── TaczEventProbe.java           # TACZ API event probe (disabled by default)
│   ├── entity/
│   │   ├── GunnerGrunt.java              # Armed grunt entity and arming
│   │   ├── GruntRegistration.java        # Attributes, spawn rules and spawn egg
│   │   ├── GruntRegistry.java            # Entity and item registration
│   │   ├── TankElite.java                # Tank elite entity and shelling
│   │   ├── TankRegistration.java         # Attributes, spawn rules and spawn egg
│   │   └── TankRegistry.java             # Entity and item registration
│   ├── fastmag/
│   │   ├── FastMag.java                  # Fast mag marker and reload ratios
│   │   ├── FastMagCreativeTab.java       # Fast mag creative tab
│   │   ├── FastMagReloadHandler.java     # Reload acceleration
│   │   └── FastMagTooltipHandler.java    # Item tooltip
│   ├── gunner/
│   │   ├── GunnerArming.java             # Gun arming with random attachments and ammo
│   │   ├── GunnerArsenal.java            # Gunner arsenal and balance constants
│   │   ├── GunnerCombat.java             # Gunner targeting and firing logic
│   │   ├── GunnerDurability.java         # Gunner health, armor and armor equipping
│   │   ├── GunnerEffects.java            # Muzzle flash and tracer particles
│   │   ├── GunnerEligibility.java        # Gunner eligibility from list switches
│   │   ├── GunnerMarker.java             # Gunner identity marker
│   │   ├── GunnerMobRefreshHandler.java  # Mob list refresh on world load
│   │   ├── GunnerMobRegistry.java        # Full scan and bulk group rewriting
│   │   ├── GunnerRetaliation.java        # Improvised arming and retaliation for non-hostile mobs
│   │   ├── GunnerSpawnHandler.java       # Gunner spawn assignment
│   │   └── GunnerState.java              # Transient gunner state
│   ├── hitindicator/
│   │   ├── HitDirectionNotifier.java     # Bullet hit check and source direction delivery
│   │   └── HitIndicatorOverlay.java      # Incoming fire arc rendering
│   ├── integration/
│   │   ├── TaczApi.java                  # Single access point for the TACZ public API
│   │   └── TaczAutoReload.java           # Upstream automatic reload bridge
│   ├── item/
│   │   ├── InfiniteMag.java              # Infinite magazine marker and name
│   │   ├── InfiniteMagHandler.java       # Magazine and dummy ammo upkeep
│   │   └── PatchCreativeTab.java         # Patch item tab
│   ├── loot/
│   │   ├── BattleLootHandler.java        # Gunner loot drops
│   │   └── GruntLootHandler.java         # Armed grunt loot
│   ├── network/
│   │   ├── HitDirectionMessage.java      # Hit direction sync packet
│   │   └── PatchNetwork.java             # Network channel registration
│   ├── util/
│   │   └── UiText.java                   # UI text wrapper
│   └── world/
│       ├── ModFeatures.java              # World generation feature registration
│       └── RuinedOutpostFeature.java     # Ruined outpost generation
└── resources/
    ├── META-INF/mods.toml                # Mod metadata
    ├── pack.mcmeta                       # Resource pack metadata
    ├── assets/tacz_enhanced_patch/lang/  # UI language files
    └── data/tacz_enhanced_patch/         # World generation and loot data
        ├── forge/biome_modifier/         # Biome injection
        ├── loot_tables/chests/           # Chest loot
        ├── loot_tables/entities/         # Entity loot
        └── worldgen/                     # Features and placement rules
```

## Conventions

- Add a dedicated package under `java/com/taczenhancedpatch/` for each new patch feature
- Keep all TACZ type references inside the `integration/` package, and let other code use only the abstraction exposed by `TaczApi`
- Keep gunner balance values in `gunner/GunnerArsenal.java` and `config/ServerConfig.java`
- Keep client preferences in `config/ClientConfig.java`; anything that affects gameplay belongs in the server configuration
- Organize resources under `assets/tacz_enhanced_patch/` and `data/tacz_enhanced_patch/`
- Put client-only code in the `client/` package and load it only for `Dist.CLIENT`
