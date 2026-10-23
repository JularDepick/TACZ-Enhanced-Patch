# Source Layout

[[简体中文]](./README.md) |
[English]

This directory holds the functional source code of the mod, while the rest of the repository is auxiliary or documentation content

```
src/main/
├── java/com/taczenhancedpatch/
│   ├── TACZEnhancedPatch.java          # Mod entry point
│   ├── balance/
│   │   └── GunDamageBalanceHandler.java # Damage adjustment for gunner bullets
│   ├── client/
│   │   ├── TACZEnhancedPatchClient.java # Client entry point
│   │   └── config/
│   │       ├── CommonConfigScreen.java  # Configuration screen
│   │       └── ModConfigScreen.java     # Configuration screen registration
│   ├── config/
│   │   ├── CommonConfig.java           # Common configuration and option list
│   │   └── ConfigEvents.java           # Configuration lifecycle and saving
│   ├── debug/
│   │   └── TaczEventProbe.java         # TACZ API event probe (disabled by default)
│   ├── gunner/
│   │   ├── GunnerArsenal.java          # Gunner arsenal and balance constants
│   │   ├── GunnerCombat.java           # Gunner targeting and firing logic
│   │   ├── GunnerEligibility.java      # Gunner candidate scan over registered entity types
│   │   ├── GunnerMarker.java           # Gunner identity marker
│   │   ├── GunnerSpawnHandler.java     # Gunner spawn assignment
│   │   └── GunnerState.java            # Transient gunner state
│   ├── integration/
│   │   └── TaczApi.java                # Single access point for the TACZ public API
│   ├── loot/
│   │   └── BattleLootHandler.java      # Gunner loot drops
│   └── world/
│       ├── ModFeatures.java            # World generation feature registration
│       └── RuinedOutpostFeature.java   # Ruined outpost generation
└── resources/
    ├── META-INF/mods.toml              # Mod metadata
    ├── pack.mcmeta                     # Resource pack metadata
    └── data/tacz_enhanced_patch/       # World generation and loot data
        ├── forge/biome_modifier/       # Biome injection
        ├── loot_tables/chests/         # Chest loot
        └── worldgen/                   # Features and placement rules
```

## Conventions

- Add a dedicated package under `java/com/taczenhancedpatch/` for each new patch feature
- Keep all TACZ type references inside the `integration/` package, and let other code use only the abstraction exposed by `TaczApi`
- Keep gunner balance values in `gunner/GunnerArsenal.java` and `config/CommonConfig.java`
- Organize resources under `assets/tacz_enhanced_patch/` and `data/tacz_enhanced_patch/`
- Put client-only code in the `client/` package and load it only for `Dist.CLIENT`
