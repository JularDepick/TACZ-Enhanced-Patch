# 源码目录结构

[简体中文] |
[[English]](./README_en-US.md)

本目录是模组的功能性源码目录, 根目录的其余内容为辅助性与说明性内容

```
src/main/
├── java/com/taczenhancedpatch/
│   ├── TACZEnhancedPatch.java            # 模组主入口
│   ├── balance/
│   │   ├── GunDamageBalanceHandler.java  # 枪手子弹伤害调节
│   │   └── GunnerDefenseHandler.java     # 枪手受击伤害调节
│   ├── client/
│   │   ├── AutoReloadHandler.java        # 自动换弹
│   │   ├── ConfigAccess.java             # 存档创建者判定
│   │   ├── ConfigShortcutHandler.java    # 配置入口快捷键(原版按键)
│   │   ├── TACZEnhancedPatchClient.java  # 客户端入口
│   │   ├── config/
│   │   │   ├── ConfigHubScreen.java      # 配置入口页
│   │   │   ├── MobListScreen.java        # 持械生物列表页
│   │   │   ├── ModConfigScreen.java      # 配置界面注册
│   │   │   ├── PatchConfigScreen.java    # 参数列表页
│   │   │   └── ScrollBar.java            # 可拖动滚动条
│   │   └── hud/
│   │       └── HeadingRulerOverlay.java  # 方向标尺绘制
│   ├── config/
│   │   ├── ClientConfig.java             # 客户端配置
│   │   ├── ConfigEvents.java             # 配置生命周期与保存
│   │   ├── ConfigOption.java             # 配置项描述
│   │   ├── ConfigOptionGroup.java        # 配置分组与界面顺序
│   │   ├── MobListConfig.java            # 持械生物列表与逐项开关
│   │   ├── MobMiddleConfig.java          # 七个持械分组开关
│   │   └── ServerConfig.java             # 服务端配置与配置项列表
│   ├── debug/
│   │   └── TaczEventProbe.java           # TACZ API 事件探针(默认关闭)
│   ├── entity/
│   │   ├── GunnerGrunt.java              # 持枪小兵实体与装配
│   │   ├── GruntRegistration.java        # 属性, 生成规则与刷怪蛋
│   │   └── GruntRegistry.java            # 实体与物品注册
│   ├── fastmag/
│   │   ├── FastMag.java                  # 快速扩容弹夹标记与倍率
│   │   ├── FastMagCreativeTab.java       # 快速扩容弹夹物品页
│   │   ├── FastMagReloadHandler.java     # 换弹加速
│   │   └── FastMagTooltipHandler.java    # 物品提示
│   ├── gunner/
│   │   ├── GunnerArming.java             # 枪械装配与随机配件, 随机备弹
│   │   ├── GunnerArsenal.java            # 枪手军械库与平衡常量
│   │   ├── GunnerCombat.java             # 枪手索敌与开火逻辑
│   │   ├── GunnerDurability.java         # 枪手生命, 护甲与护甲穿戴
│   │   ├── GunnerEffects.java            # 枪口火光与弹道粒子
│   │   ├── GunnerEligibility.java        # 枪手候选判定, 只读列表开关
│   │   ├── GunnerMarker.java             # 枪手身份标记
│   │   ├── GunnerMobRefreshHandler.java  # 世界加载时刷新生物列表
│   │   ├── GunnerMobRegistry.java        # 全量扫描与分组批量改写
│   │   ├── GunnerRetaliation.java        # 非敌对生物临场武装与反击
│   │   ├── GunnerSpawnHandler.java       # 枪手生成
│   │   └── GunnerState.java              # 枪手瞬时状态
│   ├── hitindicator/
│   │   ├── HitDirectionNotifier.java     # 子弹命中判定与来源方位下发
│   │   └── HitIndicatorOverlay.java      # 受击方向弧块绘制
│   ├── integration/
│   │   ├── TaczApi.java                  # TACZ 公开 API 统一入口
│   │   └── TaczAutoReload.java           # 上游自动换弹开关桥接
│   ├── loot/
│   │   └── BattleLootHandler.java        # 枪手战利品掉落
│   ├── network/
│   │   ├── HitDirectionMessage.java      # 受击方向同步包
│   │   └── PatchNetwork.java             # 网络通道注册
│   ├── util/
│   │   └── UiText.java                   # 界面文本包装
│   └── world/
│       ├── ModFeatures.java              # 世界生成特性注册
│       └── RuinedOutpostFeature.java     # 废弃前哨遗迹生成
└── resources/
    ├── META-INF/mods.toml                # 模组元信息
    ├── pack.mcmeta                       # 资源包元信息
    ├── assets/tacz_enhanced_patch/lang/  # 界面语言文件
    └── data/tacz_enhanced_patch/         # 世界生成与战利品数据
        ├── forge/biome_modifier/         # 群系注入
        ├── loot_tables/chests/           # 宝箱战利品
        └── worldgen/                     # 特性与放置规则
```

## 组织约定

- 新增补丁功能时, 在 `java/com/taczenhancedpatch/` 下按功能建立独立包
- 与 TACZ 的类型交互集中在 `integration/` 包内, 其余代码只使用 `TaczApi` 暴露的抽象类型
- 枪手玩法的可调数值集中在 `gunner/GunnerArsenal.java` 与 `config/ServerConfig.java`
- 客户端偏好放 `config/ClientConfig.java`, 只放不影响服务端判定的项; 影响玩法的参数一律进服务端配置
- 资源文件按 `assets/tacz_enhanced_patch/` 与 `data/tacz_enhanced_patch/` 组织
- 客户端专用代码放在 `client/` 包内, 并通过 `Dist.CLIENT` 限定加载
