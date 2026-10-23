# Source Layout

[[简体中文]](./README.md) |
[English]

This directory holds the functional source code of the mod, while the rest of the repository is auxiliary or documentation content

```
src/main/
├── java/com/taczenhancedpatch/
│   ├── TACZEnhancedPatch.java      # Mod entry point
│   ├── client/
│   │   └── TACZEnhancedPatchClient.java # Client entry point
│   ├── config/
│   │   └── CommonConfig.java       # Common configuration
│   ├── debug/
│   │   └── TaczEventProbe.java     # TACZ API event probe (disabled by default)
│   └── integration/
│       └── TaczApi.java            # Single access point for the TACZ public API
└── resources/
    ├── META-INF/mods.toml          # Mod metadata
    └── pack.mcmeta                 # Resource pack metadata
```

## Conventions

- Add a dedicated package under `java/com/taczenhancedpatch/` for each new patch feature
- Keep all TACZ type references inside the `integration/` package, and do not reference TACZ types elsewhere
- Organize resources under `assets/tacz_enhanced_patch/` and `data/tacz_enhanced_patch/`
- Put client-only code in the `client/` package and load it only for `Dist.CLIENT`
