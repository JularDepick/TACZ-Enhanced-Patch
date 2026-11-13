# 开发者文档

## 运行与构建

| 命令 | 说明 |
|:---:|:---:|
| `gradlew.bat build` | 构建模组产物 |
| `gradlew.bat runClient` | 启动开发客户端 |
| `gradlew.bat runServer` | 启动开发服务端 |
| `gradlew.bat clean` | 清理构建产物 |

构建产物输出到 Gradle 默认的 `build/libs/` 目录, 文件名为 `TACZEnhancedPatch-v<版本>-mc-1.20.1-forge.jar`

开发运行已配置 Mixin 引用表重映射, 以支持上游模组 TACZ 的混入; 服务端冒烟测试可使用 `gradlew.bat runServer`

配置界面从模组列表的配置按钮进入, 不属于快捷键入口

## 遗迹建筑模型

- 建筑源文件放在 `src/main/ruins/`, 只接受 WorldEdit 的 .schematic 格式, 文件名随意, 中文名同样可用
- 构建时 `processResources` 把它们原样并入资源包的 `assets/tacz_enhanced_patch/ruins/`, 不做转换
- 运行期由 `SchematicCatalog` 扫描目录并随机选取, `SchematicReader` 解析, `RuinPasteHandler` 在服务端 tick 分帧粘贴
- 新增或替换建筑只需要放文件后重新构建, 不需要改 Java, 也不需要登记清单
- 旧版数字方块 ID 由 `LegacyBlocks` 映射, 未收录的 ID 退回石头并记一条调试日志

## 开发者文档清单

| 文档 | 用途 |
|:---:|:---:|
| [TACZ接入规范.md](./TACZ接入规范.md) | 与 TACZ 交互时必须遵守的边界, 允许的 API 面, 事件订阅方式与版本适配位置 |
