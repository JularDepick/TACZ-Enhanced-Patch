package com.taczenhancedpatch.util;

import com.taczenhancedpatch.TACZEnhancedPatch;
import net.minecraft.network.chat.Component;

/**
 * 界面文本。
 * 所有玩家可见的文字统一走语言文件, 键名以模组 ID 为前缀, 游戏语言缺失时回退到英文。
 */
public final class UiText {
    private static final String PREFIX = TACZEnhancedPatch.MOD_ID + ".";

    private UiText() {
    }

    public static Component of(String key) {
        return Component.translatable(PREFIX + key);
    }

    public static Component of(String key, Object... args) {
        return Component.translatable(PREFIX + key, args);
    }

    public static String plain(String key) {
        return of(key).getString();
    }
}
