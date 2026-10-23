# 源码目录结构

[简体中文] |
[[English]](./README_en-US.md)

本目录是模组的功能性源码目录, 根目录的其余内容为辅助性与说明性内容

```
src/main/
├── java/com/taczenhancedpatch/
│   ├── TACZEnhancedPatch.java          # 模组主入口
│   ├── balance/
│   │   └── GunDamageBalanceHandler.java # 枪手子弹伤害调节
│   ├── client/
│   │   └── TACZEnhancedPatchClient.java # 客户端入口
│   ├── config/
│   │   └── CommonConfig.java           # 通用配置与枪手配置
│   ├── debug/
│   │   └── TaczEventProbe.java         # TACZ API 事件探针(默认关闭)
│   ├── gunner/
│   │   ├── GunnerArsenal.java          # 枪手军械库与平衡常量
│   │   ├── GunnerCombat.java           # 枪手索敌与开火逻辑
│   │   ├── GunnerMarker.java           # 枪手身份标记
│   │   ├── GunnerSpawnHandler.java     # 枪手生成
│   │   └── GunnerState.java            # 枪手瞬时状态
│   ├── integration/
│   │   └── TaczApi.java                # TACZ 公开 API 统一入口
│   ├── loot/
│   │   └── BattleLootHandler.java      # 枪手战利品掉落
│   └── world/
│       ├── ModFeatures.java            # 世界生成特性注册
│       └── RuinedOutpostFeature.java   # 废弃前哨遗迹生成
└── resources/
    ├── META-INF/mods.toml              # 模组元信息
    ├── pack.mcmeta                     # 资源包元信息
    └── data/tacz_enhanced_patch/       # 世界生成与战利品数据
        ├── forge/biome_modifier/       # 群系注入
        ├── loot_tables/chests/         # 宝箱战利品
        └── worldgen/                   # 特性与放置规则
```

## 组织约定

- 新增补丁功能时, 在 `java/com/taczenhancedpatch/` 下按功能建立独立包
- 与 TACZ 的类型交互集中在 `integration/` 包内, 其余代码只使用 `TaczApi` 暴露的抽象类型
- 枪手玩法的可调数值集中在 `gunner/GunnerArsenal.java` 与 `config/CommonConfig.java`
- 资源文件按 `assets/tacz_enhanced_patch/` 与 `data/tacz_enhanced_patch/` 组织
- 客户端专用代码放在 `client/` 包内, 并通过 `Dist.CLIENT` 限定加载
