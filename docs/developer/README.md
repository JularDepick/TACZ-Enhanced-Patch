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

## 开发者文档清单

| 文档 | 用途 |
|:---:|:---:|
| [TACZ接入规范.md](./TACZ接入规范.md) | 与 TACZ 交互时必须遵守的边界, 允许的 API 面, 事件订阅方式与版本适配位置 |
