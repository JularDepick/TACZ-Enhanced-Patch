# Source Layout

[[简体中文]](./README.md) |
[English]

This directory holds the functional source code of the mod, while the rest of the repository is auxiliary or documentation content

```
src/main/
├── ruins/                                 # Ruin building blueprints (*.schem, packed into resources at build time)
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
│   │   ├── RuinCompassAngle.java          # Compass needle angle
│   │   ├── TACZEnhancedPatchClient.java  # Client entry point
│   │   ├── TankControlHandler.java        # Driving fire input for the tank
│   │   ├── config/
│   │   │   ├── MobListScreen.java        # Mob arming config screen
│   │   │   ├── ModConfigScreen.java      # Configuration screen registration
│   │   │   ├── PatchConfigScreen.java    # Config screen (client and server tabs)
│   │   │   └── ScrollBar.java            # Draggable scroll bar
│   │   ├── hud/
│   │   │   ├── HeadingRulerOverlay.java  # Heading ruler rendering
│   │   │   ├── RuinGarrisonOverlay.java  # Top right garrison readout
│   │   │   └── RuinNoticeOverlay.java    # Top centred ruin notice
│   │   └── render/
│   │       ├── ArmedHumanoidLayer.java    # Humanoid skeleton layer for non-humanoid armed mobs
│   │       ├── ArmedShapeAttach.java      # Attaches the layer to vanilla renderers
│   │       ├── GruntRenderer.java        # Armed grunt renderer
│   │       ├── TankModel.java            # Tank model
│   │       └── TankRenderer.java         # Tank elite renderer
│   ├── config/
│   │   ├── ClientConfig.java             # Client configuration
│   │   ├── ConfigEvents.java             # Configuration lifecycle and saving
│   │   ├── ConfigOption.java             # Option descriptor (switch, number, text)
│   │   ├── ConfigOptionGroup.java        # Configuration groups and UI order
│   │   ├── MobListConfig.java            # Mob arming config with runtime cache
│   │   ├── MobMiddleConfig.java          # Seven armed group switches
│   │   ├── RuinBlueprintSource.java      # Ruin blueprint source enum
│   │   └── ServerConfig.java             # Server configuration and option list
│   ├── debug/
│   │   └── TaczEventProbe.java           # TACZ API event probe (disabled by default)
│   ├── entity/
│   │   ├── CombatPreset.java             # Health, armor and shell power per difficulty
│   │   ├── GunnerGrunt.java              # Armed grunt entity and arming
│   │   ├── GruntRegistration.java        # Attributes, spawn rules and spawn egg
│   │   ├── GruntRegistry.java            # Entity and item registration
│   │   ├── RideTankGoal.java             # Grunt riding goal for tanks
│   │   ├── TankCrew.java                 # Crew health and armor multipliers
│   │   ├── TankElite.java                # Tank elite entity and shelling
│   │   ├── TankRegistration.java         # Attributes, spawn rules and spawn egg
│   │   └── TankRegistry.java             # Entity and item registration
│   ├── faction/
│   │   ├── FactionCombat.java             # Shared hostility, immunity and target rules
│   │   ├── FactionData.java               # Faction and home persistence
│   │   ├── Factions.java                  # Faction relations and banner colors
│   │   ├── RuinBanner.java                # Banner placement, protection and souvenir
│   │   ├── RuinBehavior.java              # Territory behaviour
│   │   ├── RuinCombatStats.java           # Kill and damage statistics
│   │   ├── RuinExperience.java            # Offline experience delivery
│   │   ├── RuinGarrison.java              # Reinforcement, capture state machine and stats handoff
│   │   ├── RuinLoot.java                  # Capture loot, supply crates and experience
│   │   ├── RuinRout.java                  # Routed marking and combat scaling
│   │   ├── RuinSites.java                 # Ruin sites, banners, statistics and spacing registry
│   │   ├── RuinStats.java                 # Combat statistics persistence
│   │   └── RuinTerritory.java             # Territory geometry, participants and garrison counting
│   ├── fastmag/
│   │   ├── FastMag.java                  # Fast mag marker, name suffix and reload ratios
│   │   ├── FastMagClientAnimator.java    # Reload animation acceleration
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
│   │   ├── GunnerState.java              # Transient gunner state
│   │   ├── MobGroups.java                # Mob grouping and armed classification
│   │   ├── ReloadGuard.java              # Cancels reload when the magazine is full
│   │   └── WeaponFacingHandler.java      # Aim the look direction at the target
│   ├── hitindicator/
│   │   ├── HitDirectionNotifier.java     # Bullet hit check and source direction delivery
│   │   └── HitIndicatorOverlay.java      # Incoming fire arc rendering
│   ├── integration/
│   │   ├── TaczApi.java                  # Single access point for the TACZ public API
│   │   └── TaczAutoReload.java           # Upstream automatic reload bridge
│   ├── item/
│   │   ├── InfiniteMag.java              # Infinite magazine marker and name
│   │   ├── InfiniteMagHandler.java       # Magazine and dummy ammo upkeep
│   │   ├── PatchCreativeTab.java         # Patch item tab
│   │   ├── PatchItems.java               # Patch item registration
│   │   └── RuinCompassItem.java          # Ruin compass behaviour
│   ├── loot/
│   │   ├── BattleLootHandler.java        # Gunner loot drops
│   │   └── GruntLootHandler.java         # Armed grunt loot
│   ├── network/
│   │   ├── HitDirectionMessage.java      # Hit direction sync packet
│   │   ├── PatchNetwork.java             # Network channel registration
│   │   ├── RuinNotice.java               # Ruin notice delivery
│   │   ├── RuinNoticeMessage.java        # Ruin notice sync packet
│   │   ├── RuinStatus.java               # Garrison readout delivery
│   │   ├── RuinStatusMessage.java        # Garrison readout sync packet
│   │   └── TankFireMessage.java          # Player fire packet
│   ├── power/
│   │   ├── CombatPower.java               # Combat power scoring and drop multiplier
│   │   └── PowerTracker.java              # Peak combat power recording
│   ├── runtime/
│   │   └── RuntimeStateCleaner.java      # Clears process wide state when the server stops
│   ├── util/
│   │   └── UiText.java                   # UI text wrapper
│   ├── villager/
│   │   ├── ArmedVillager.java            # Armed villager state and trade data
│   │   ├── VillagerArmingHandler.java    # Spawn roll, stock drops and bonus experience
│   │   ├── VillagerConversionHandler.java # Stock handling on zombification
│   │   ├── VillagerRetaliation.java      # Retaliation rules for players and mobs
│   │   └── VillagerTradeHandler.java     # Trade settlement and offer rebuild
│   └── world/
│       ├── ModFeatures.java              # World generation feature registration
│       ├── RuinedOutpostFeature.java     # Ruined outpost generation
│       └── schematic/
│           ├── LegacyBlocks.java         # Legacy block id mapping (external files only)
│           ├── RuinPasteHandler.java     # Framed paste scheduling and containers
│           ├── RuinSchematic.java        # Building data shape
│           ├── SchematicCatalog.java     # Building catalog scan and cache
│           ├── SchematicCatalogRefreshHandler.java # Rescans blueprints on world load
│           ├── SchematicPaster.java      # Per-chunk pasting
│           └── SchematicReader.java      # .schem parsing
└── resources/
    ├── META-INF/mods.toml                # Mod metadata
    ├── pack.mcmeta                       # Resource pack metadata
    ├── assets/tacz_enhanced_patch/
    │   ├── lang/                         # UI language files
    │   ├── models/item/                  # Item models such as spawn eggs and the compass
    │   ├── textures/entity/              # Entity textures
    │   └── textures/item/                # Item textures and compass frames
    └── data/tacz_enhanced_patch/         # World generation and loot data
        ├── forge/biome_modifier/         # Biome injection
        ├── loot_tables/chests/           # Chest loot
        ├── loot_tables/entities/         # Entity loot
        ├── recipes/                      # Crafting recipes
        └── worldgen/                     # Features and placement rules
```

## Conventions

- Add a dedicated package under `java/com/taczenhancedpatch/` for each new patch feature
- Keep all TACZ type references inside the `integration/` package, and let other code use only the abstraction exposed by `TaczApi`
- Keep gunner balance values in `gunner/GunnerArsenal.java` and `config/ServerConfig.java`
- Keep client preferences in `config/ClientConfig.java`; anything that affects gameplay belongs in the server configuration
- Organize resources under `assets/tacz_enhanced_patch/` and `data/tacz_enhanced_patch/`
- Put client-only code in the `client/` package and load it only for `Dist.CLIENT`
