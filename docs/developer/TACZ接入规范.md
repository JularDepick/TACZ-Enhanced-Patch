# TACZ 接入规范

本模组是 TACZ 的独立补丁模组, 通过前置模组方式加载, 不包含也不修改 TACZ 源码

## 基本原则

- 只允许调用 TACZ 公开 API 包 `com.tacz.guns.api` 内的类型, 以及这些 API 方法签名中直接返回或接收的类型
- 只允许通过 TACZ 发布的 API 事件与其交互
- 禁止修改 TACZ 源码, 禁止对 TACZ 类编写 Mixin, 访问转换或核心模组
- 禁止把 TACZ 的代码或资源打包进本模组产物

## 允许的交互面

| 交互面 | 说明 |
|:---:|:---:|
| `com.tacz.guns.api.TimelessAPI` | 枪械, 配件, 弹药与方块的索引查询 |
| `com.tacz.guns.api.item` | 枪械, 配件与弹药物品的 NBT 访问接口 |
| `com.tacz.guns.api.item.builder` | 构建枪械与配件物品 |
| `com.tacz.guns.api.event` | 射击, 换弹, 伤害, 击杀等事件 |
| `com.tacz.guns.api.modifier` | 配件属性修改值的扩展 |
| `com.tacz.guns.api.resource` | 数据驱动资源加载 |

## 禁止的做法

| 做法 | 原因 |
|:---:|:---:|
| 对 `com.tacz.guns` 下的类编写 Mixin | 属于侵入式开发, 与原模组实现强耦合 |
| 使用访问转换打开 TACZ 类成员 | 同上, 且前置版本变化后容易失效 |
| 反射调用 TACZ 内部实现类 | 内部实现不保证稳定, 升级前置后易崩溃 |
| 在产物中打包 TACZ 的类或资源 | 破坏前置模组的独立性, 并带来许可风险 |

## 事件订阅

- TACZ 的 API 事件发布在 Forge 事件总线上, 使用 `MinecraftForge.EVENT_BUS` 订阅
- 订阅类使用 `@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID)` 自动注册
- 需要同时处理两侧事件时, 必须判断 `getLogicalSide()`
- 取消事件会改变原模组行为, 必须在配置中提供开关, 默认不取消

## 版本适配

- 对 TACZ 的类型引用集中在 `src/main/java/com/taczenhancedpatch/integration/TaczApi.java`
- 前置版本区间在 `src/main/resources/META-INF/mods.toml` 的 `tacz` 依赖中声明
- 构建依赖版本与 CurseForge 文件编号在 `build.gradle` 头部集中定义
- 升级前置模组时, 先更新上述三处, 再回归验证事件与 API 签名

## 可选依赖

- 与第三方模组的兼容代码必须先判断模组是否加载, 使用 `ModList.get().isLoaded("modid")`
- 兼容代码放在独立包内, 避免主流程引用可选依赖的类型

## 调试

- 通用配置项 `debugEvents` 打开后, 事件探针会把 TACZ API 事件写入日志
- 事件探针只读, 不修改事件状态
