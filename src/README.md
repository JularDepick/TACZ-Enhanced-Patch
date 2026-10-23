# 源码目录结构

[简体中文] |
[[English]](./README_en-US.md)

本目录是模组的功能性源码目录, 根目录的其余内容为辅助性与说明性内容

```
src/main/
├── java/com/taczenhancedpatch/
│   ├── TACZEnhancedPatch.java      # 模组主入口
│   ├── client/
│   │   └── TACZEnhancedPatchClient.java # 客户端入口
│   ├── config/
│   │   └── CommonConfig.java       # 通用配置
│   ├── debug/
│   │   └── TaczEventProbe.java     # TACZ API 事件探针(默认关闭)
│   └── integration/
│       └── TaczApi.java            # TACZ 公开 API 统一入口
└── resources/
    ├── META-INF/mods.toml          # 模组元信息
    └── pack.mcmeta                 # 资源包元信息
```

## 组织约定

- 新增补丁功能时, 在 `java/com/taczenhancedpatch/` 下按功能建立独立包
- 与 TACZ 的类型交互集中在 `integration/` 包内, 其余代码不直接引用 TACZ 类型
- 资源文件按 `assets/tacz_enhanced_patch/` 与 `data/tacz_enhanced_patch/` 组织
- 客户端专用代码放在 `client/` 包内, 并通过 `Dist.CLIENT` 限定加载
