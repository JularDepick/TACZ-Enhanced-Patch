package com.taczenhancedpatch.integration;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 上游自动换弹的开关桥接。
 * 上游在客户端配置里维护自动换弹开关, 换弹行为由上游自身实现;
 * 这里只按字段名读取并置位该开关, 不引用上游的内部类型, 也不复制其换弹逻辑。
 */
public final class TaczAutoReload {
    private static final String CONFIG_CLASS = "com.tacz.guns.config.client.KeyConfig";
    private static final String FIELD = "AUTO_RELOAD";

    private TaczAutoReload() {
    }

    /* 读取上游开关, 上游缺失或配置未加载时按关闭处理 */
    public static boolean upstreamEnabled() {
        Object value = upstreamValue();
        if (value instanceof ForgeConfigSpec.BooleanValue flag) {
            try {
                return Boolean.TRUE.equals(flag.get());
            } catch (IllegalStateException ignored) {
                return false;
            }
        }
        return false;
    }

    /* 打开上游开关, 已经打开时不重复写入 */
    public static void enableUpstream() {
        if (upstreamEnabled()) {
            return;
        }
        Object value = upstreamValue();
        if (value instanceof ForgeConfigSpec.BooleanValue flag) {
            try {
                flag.set(Boolean.TRUE);
            } catch (IllegalStateException ignored) {
            }
        }
    }

    private static Object upstreamValue() {
        try {
            Class<?> type = Class.forName(CONFIG_CLASS);
            return type.getField(FIELD).get(null);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }
}
