package com.taczenhancedpatch.config;

import java.util.List;

/**
 * 配置分组。
 * 界面按分组标题归类展示参数, 分组顺序即界面顺序, 组内顺序即展示顺序。
 */
public record ConfigOptionGroup(String label, List<ConfigOption> options) {
}
