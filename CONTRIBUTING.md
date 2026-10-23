<div align="center">

# 贡献指南

[简体中文] |
[[English]](./CONTRIBUTING_en-US.md)

</div>

欢迎为 TACZ Enhanced Patch 提交问题与代码贡献

## 贡献规范

### 非侵入式开发

- 只允许通过 TACZ 公开 API 与事件与其交互
- 禁止修改 TACZ 源码, 禁止对 TACZ 类编写 Mixin 或访问转换
- 具体约束见 [TACZ接入规范](./docs/developer/TACZ接入规范.md)

### 补丁模块

- 单个补丁应保持可独立开关, 默认行为与未安装本模组时一致
- 新增补丁时同步补充配置项说明与开发者文档

## 贡献方式

### 1. 提交 Issue

- [新建 Issue](https://github.com/JularDepick/TACZ-Enhanced-Patch/issues/new)
- 描述问题现象, 复现步骤与涉及的游戏环境

### 2. 贡献代码

- [Fork 本项目](https://github.com/JularDepick/TACZ-Enhanced-Patch/fork)
- 在 fork 分支完成修改
- [创建 PR](https://github.com/JularDepick/TACZ-Enhanced-Patch/compare)
- 等待审核
