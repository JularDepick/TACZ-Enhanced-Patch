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

## 环境要求

| 项 | 要求 | 说明 |
|:---:|:---:|:---|
| JDK | 17 | 构建脚本把工具链声明为 17, 本机必须能找到该版本 |
| Gradle | 8.14.3 | 随 gradlew 包装器提供, 无需单独安装 |
| 网络 | 可访问镜像 | 首次构建需要解析 ForgeGradle 与上游模组依赖 |

只装了更高版本 JDK 时, 构建会在配置阶段之后失败, 报错形如 `Cannot find a Java installation on your machine matching: {languageVersion=17}`。两种处理方式:

- 安装一个 JDK 17, Gradle 会自动发现
- 或在 `gradle.properties` 增加 `org.gradle.java.installations.paths` 指向已有的 JDK 17 目录

开发运行与产物构建都需要 JDK 17; 只用 `javac` 做语法校验不产生可运行产物

## 遗迹建筑模型

- 建筑源文件放在 `src/main/ruins/`, 只接受 WorldEdit 的 .schematic 格式, 文件名随意, 中文名同样可用
- 构建时 `processResources` 把它们原样并入资源包的 `assets/tacz_enhanced_patch/ruins/`, 不做转换
- 另有外部目录: 存档服务端配置目录下的 `tacz_enhanced_patch_ruins/`, 递归扫描深度上限四层, 与内置模型在选择时平权; 服务端配置项 `worldgen.modelSource` 有三档: 仅内置, 内置与外部, 仅外部, 默认取内置与外部
- 站点保存生成时选定的建筑名, 外部来源带 `external:` 前缀; 建筑被删除或格式不合法时弃用该文件并换一座可用的建筑, 记一条运行日志
- 运行期由 `SchematicCatalog` 扫描目录并随机选取, `SchematicReader` 解析, `RuinPasteHandler` 在服务端 tick 分帧粘贴
- 新增或替换建筑只需要放文件后重新构建, 不需要改 Java, 也不需要登记清单
- 旧版数字方块 ID 由 `LegacyBlocks` 映射, 未收录的 ID 退回石头并记一条调试日志
- 格式与流程参考了 WorldEdit 的 .schematic 读写思路, 解析与粘贴均为本模组自行实现, 不包含其代码也不引入其依赖

## 开发者文档清单

| 文档 | 用途 |
|:---:|:---:|
| [TACZ接入规范.md](./TACZ接入规范.md) | 与 TACZ 交互时必须遵守的边界, 允许的 API 面, 事件订阅方式与版本适配位置 |
