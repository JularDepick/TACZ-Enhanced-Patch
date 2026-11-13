# 源码目录结构

[简体中文] |
[[English]](./README_en-US.md)

本目录是模组的功能性源码目录, 根目录的其余内容为辅助性与说明性内容

```
src/main/
├── ruins/                                 # 遗迹建筑模型(*.schematic, 构建时并入资源包)
├── java/com/taczenhancedpatch/
│   ├── TACZEnhancedPatch.java            # 模组主入口
│   ├── balance/
│   │   ├── GunDamageBalanceHandler.java  # 枪手子弹伤害调节
│   │   └── GunnerDefenseHandler.java     # 枪手受击伤害调节
│   ├── client/
│   │   ├── AutoReloadHandler.java        # 自动换弹
│   │   ├── ConfigAccess.java             # 存档创建者判定
│   │   ├── ConfigShortcutHandler.java    # 配置入口快捷键(原版按键)
│   │   ├── InfiniteMagTooltipHandler.java # 无限弹匣提示
│   │   ├── TACZEnhancedPatchClient.java  # 客户端入口
│   │   ├── config/
│   │   │   ├── MobListScreen.java        # 生物持械配置页
│   │   │   ├── ModConfigScreen.java      # 配置界面注册
│   │   │   ├── PatchConfigScreen.java    # 配置页(客户端与服务端双页签)
│   │   │   └── ScrollBar.java            # 可拖动滚动条
│   │   ├── hud/
│   │   │   └── HeadingRulerOverlay.java  # 方向标尺绘制
│   │   └── render/
│   │       ├── ArmedHumanoidLayer.java    # 非人形持械的人形骨架图层
│   │       ├── ArmedShapeAttach.java      # 给原版渲染器挂载该图层
│   │       ├── GruntRenderer.java        # 持枪小兵渲染
│   │       ├── TankModel.java            # 坦克模型
│   │       └── TankRenderer.java         # 坦克精英怪渲染
│   ├── config/
│   │   ├── ClientConfig.java             # 客户端配置
│   │   ├── ConfigEvents.java             # 配置生命周期与保存
│   │   ├── ConfigOption.java             # 配置项描述(开关, 数值与文本)
│   │   ├── ConfigOptionGroup.java        # 配置分组与界面顺序
│   │   ├── MobListConfig.java            # 生物持械配置与运行期缓存
│   │   ├── MobMiddleConfig.java          # 七个持械分组开关
│   │   └── ServerConfig.java             # 服务端配置与配置项列表
│   ├── debug/
│   │   └── TaczEventProbe.java           # TACZ API 事件探针(默认关闭)
│   ├── entity/
│   │   ├── GunnerGrunt.java              # 持枪小兵实体与装配
│   │   ├── GruntRegistration.java        # 属性, 生成规则与刷怪蛋
│   │   ├── GruntRegistry.java            # 实体与物品注册
│   │   ├── TankElite.java                # 坦克精英怪实体与炮击
│   │   ├── TankRegistration.java         # 属性, 生成规则与刷怪蛋
│   │   └── TankRegistry.java             # 实体与物品注册
│   ├── faction/
│   │   ├── FactionCombat.java             # 共享敌对与免伤
│   │   ├── FactionData.java               # 阵营与归属持久化
│   │   ├── Factions.java                  # 阵营关系判定与颜色阵营
│   │   ├── RuinBanner.java                # 旗帜放置, 保护与纪念品
│   │   ├── RuinBehavior.java              # 领地行为
│   │   ├── RuinCombatStats.java           # 击杀与伤害统计
│   │   ├── RuinExperience.java            # 离线经验补发
│   │   ├── RuinGarrison.java              # 补兵与夺旗状态机
│   │   ├── RuinLoot.java                  # 夺旗战利品与经验换算
│   │   ├── RuinRout.java                  # 溃军标记与战力缩放
│   │   ├── RuinSites.java                 # 遗迹站点与旗帜与统计字段
│   │   ├── RuinStats.java                 # 战斗统计持久化
│   │   └── RuinTerritory.java             # 领地几何与数量统计
│   ├── fastmag/
│   │   ├── FastMag.java                  # 快速扩容弹匣标记, 名称后缀与换弹比例
│   │   ├── FastMagCreativeTab.java       # 快速扩容弹匣物品页
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
│   ├── item/
│   │   ├── InfiniteMag.java              # 无限弹匣标记与名称
│   │   ├── InfiniteMagHandler.java       # 弹匣与虚拟备弹维持
│   │   └── PatchCreativeTab.java         # 补丁物品页
│   ├── loot/
│   │   ├── BattleLootHandler.java        # 枪手战利品掉落
│   │   └── GruntLootHandler.java         # 持枪小兵战利品
│   ├── network/
│   │   ├── HitDirectionMessage.java      # 受击方向同步包
│   │   └── PatchNetwork.java             # 网络通道注册
│   ├── power/
│   │   ├── CombatPower.java               # 战力量化与掉落翻倍系数
│   │   └── PowerTracker.java              # 战斗峰值战力记录
│   ├── util/
│   │   └── UiText.java                   # 界面文本包装
│   ├── villager/
│   │   ├── ArmedVillager.java            # 中立持械村民状态与交易数据
│   │   ├── VillagerArmingHandler.java    # 生成判定与额外经验
│   │   ├── VillagerRetaliation.java      # 玩家与生物的反击判定
│   │   └── VillagerTradeHandler.java     # 交易结算与商品重建
│   └── world/
│       ├── ModFeatures.java              # 世界生成特性注册
│       ├── RuinedOutpostFeature.java     # 废弃前哨遗迹生成
│       └── schematic/
│           ├── LegacyBlocks.java         # 旧版方块 ID 到现代方块状态映射
│           ├── RuinPasteHandler.java     # 分帧粘贴调度与容器摆放
│           ├── RuinSchematic.java        # 建筑数据形态
│           ├── SchematicCatalog.java     # 建筑目录扫描与缓存
│           ├── SchematicPaster.java      # 按区块粘贴
│           └── SchematicReader.java      # .schematic 解析
└── resources/
    ├── META-INF/mods.toml                # 模组元信息
    ├── pack.mcmeta                       # 资源包元信息
    ├── assets/tacz_enhanced_patch/
    │   ├── lang/                         # 界面语言文件
    │   ├── models/item/                  # 刷怪蛋等物品模型
    │   └── textures/entity/              # 生物贴图
    └── data/tacz_enhanced_patch/         # 世界生成与战利品数据
        ├── forge/biome_modifier/         # 群系注入
        ├── loot_tables/chests/           # 宝箱战利品
        ├── loot_tables/entities/         # 生物战利品
        └── worldgen/                     # 特性与放置规则
```

## 组织约定

- 新增补丁功能时, 在 `java/com/taczenhancedpatch/` 下按功能建立独立包
- 与 TACZ 的类型交互集中在 `integration/` 包内, 其余代码只使用 `TaczApi` 暴露的抽象类型
- 枪手玩法的可调数值集中在 `gunner/GunnerArsenal.java` 与 `config/ServerConfig.java`
- 客户端偏好放 `config/ClientConfig.java`, 只放不影响服务端判定的项; 影响玩法的参数一律进服务端配置
- 资源文件按 `assets/tacz_enhanced_patch/` 与 `data/tacz_enhanced_patch/` 组织
- 客户端专用代码放在 `client/` 包内, 并通过 `Dist.CLIENT` 限定加载
