<div align="center">

# TACZ-Enhanced-Patch (TACZ 增强补丁)

[![Version](https://img.shields.io/badge/Version-0.1.0-green)](https://github.com/JularDepick/TACZ-Enhanced-Patch/tree/v0.1.0)
[![Copyright](https://img.shields.io/badge/Copyright-JularDepick-0066AA)](./COPYRIGHT)
[![License](https://img.shields.io/badge/License-Apache--2.0-red)](./LICENSE)

[简体中文] |
[[English]](./README_en-US.md)

</div>

面向 TACZ (Timeless and Classics Zero) 的增强补丁模组, 以独立模组形式提供, 仅通过 TACZ 公开 API 与事件交互, 不修改 TACZ 源码

---

## 前置要求

| 项 | 要求 |
|:---:|:---:|
| Minecraft | 1.20.1 |
| 模组加载器 | Forge 47.x |
| 前置模组 | TACZ 1.1.8 及以上 |

## 项目状态

当前为项目初始化骨架, 已包含构建配置, 模组入口, 通用配置与 TACZ API 事件探针; 具体补丁功能按模块形式后续加入

## 开发构建环境

| 基线组件 | 版本 |
|:---:|:---:|
| Java | JDK17 |
| Gradle | 8.14.3 |
| Minecraft | 1.20.1 |
| Forge | 47.4.23 |
| TACZ | 1.1.8-hotfix2 |

## 构建

```bat
:: 构建模组产物
gradlew.bat build

:: 启动开发客户端
gradlew.bat runClient

:: 启动开发服务端
gradlew.bat runServer
```

构建产物命名: `TACZEnhancedPatch-v<模组版本>-mc-<MC版本>-forge.jar`

前置模组 TACZ 由构建脚本从 CurseForge 自动获取, 首次构建需要网络访问

## 贡献指南

详见 [CONTRIBUTING.md](./CONTRIBUTING.md)

## 许可证

本项目基于 [Apache-2.0 许可证](./LICENSE) 开源

## 版权信息

Copyright (c) 2026 JularDepick

详见 [COPYRIGHT](./COPYRIGHT)

## 相关链接

- GitHub: https://github.com/JularDepick/TACZ-Enhanced-Patch
