package com.taczenhancedpatch;

import com.mojang.logging.LogUtils;
import com.taczenhancedpatch.client.config.ModConfigScreen;
import com.taczenhancedpatch.config.CommonConfig;
import com.taczenhancedpatch.world.ModFeatures;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
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

    public TACZEnhancedPatch(FMLJavaModLoadingContext context) {
        context.registerConfig(ModConfig.Type.COMMON, CommonConfig.SPEC);
        ModFeatures.FEATURES.register(context.getModEventBus());
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ModConfigScreen.register(context));
        LOGGER.info("{} initialized", MOD_NAME);
    }
}
