package com.taczenhancedpatch;

import com.mojang.logging.LogUtils;
import com.taczenhancedpatch.config.CommonConfig;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;

/**
 * 模组主入口。
 * 本模组以独立补丁模组形式提供, 只通过 TACZ 公开 API 与事件与其交互。
 */
@Mod(TACZEnhancedPatch.MOD_ID)
public final class TACZEnhancedPatch {
    public static final String MOD_ID = "tacz_enhanced_patch";
    public static final String MOD_NAME = "TACZ Enhanced Patch";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TACZEnhancedPatch() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, CommonConfig.SPEC);
        LOGGER.info("{} initialized", MOD_NAME);
    }
}
