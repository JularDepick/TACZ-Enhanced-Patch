<div align="center">

# TACZ-Enhanced-Patch

[![Version](https://img.shields.io/badge/Version-0.1.0-green)](https://github.com/JularDepick/TACZ-Enhanced-Patch/tree/v0.1.0)
[![Copyright](https://img.shields.io/badge/Copyright-JularDepick-0066AA)](./COPYRIGHT)
[![License](https://img.shields.io/badge/License-Apache--2.0-red)](./LICENSE)

[[简体中文]](./README.md) |
[English]

</div>

An enhancement patch mod for TACZ (Timeless and Classics Zero), delivered as a standalone addon that interacts with TACZ only through its public API and events, without modifying TACZ source code

---

## Requirements

| Item | Requirement |
|:---:|:---:|
| Minecraft | 1.20.1 |
| Mod loader | Forge 47.x |
| Dependency | TACZ 1.1.8 or later |

## Project Status

The project is currently an initialization skeleton with build configuration, mod entry points, common configuration and a TACZ API event probe; concrete patches will be added as modules

## Development Environment

| Baseline component | Version |
|:---:|:---:|
| Java | JDK17 |
| Gradle | 8.14.3 |
| Minecraft | 1.20.1 |
| Forge | 47.4.23 |
| TACZ | 1.1.8-hotfix2 |

## Build

```bat
:: Build the mod artifact
gradlew.bat build

:: Launch the development client
gradlew.bat runClient

:: Launch the development server
gradlew.bat runServer
```

Artifact name: `TACZEnhancedPatch-v<mod version>-mc-<MC version>-forge.jar`

The TACZ dependency is fetched from CurseForge by the build script, so the first build requires network access

## Contributing

See [CONTRIBUTING_en-US.md](./CONTRIBUTING_en-US.md)

## License

This project is licensed under the [Apache-2.0 License](./LICENSE)

## Copyright

Copyright (c) 2026 JularDepick

See [COPYRIGHT](./COPYRIGHT)

## Links

- GitHub: https://github.com/JularDepick/TACZ-Enhanced-Patch
