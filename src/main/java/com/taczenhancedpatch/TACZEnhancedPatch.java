package com.taczenhancedpatch;

import com.mojang.logging.LogUtils;
import com.taczenhancedpatch.client.config.ModConfigScreen;
import com.taczenhancedpatch.config.ClientConfig;
import com.taczenhancedpatch.config.ServerConfig;
import com.taczenhancedpatch.config.WorldgenConfig;
import com.taczenhancedpatch.entity.GruntRegistry;
import com.taczenhancedpatch.entity.PhantomRegistry;
import com.taczenhancedpatch.grenade.GrenadeRegistry;
import com.taczenhancedpatch.entity.TankRegistry;
import com.taczenhancedpatch.fastmag.FastMagCreativeTab;
import com.taczenhancedpatch.item.PatchCreativeTab;
import com.taczenhancedpatch.item.PatchItems;
import com.taczenhancedpatch.mortar.MortarRegistry;
import com.taczenhancedpatch.network.PatchNetwork;
import com.taczenhancedpatch.world.structure.RuinStructures;
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
        context.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        context.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        /* 世界生成上限单独一份文件, 绑定存档, 游戏内不可调 */
        context.registerConfig(ModConfig.Type.SERVER, WorldgenConfig.SPEC, "tacz_enhanced_patch-worldgen.toml");
        PatchNetwork.register();
        RuinStructures.STRUCTURE_TYPES.register(context.getModEventBus());
        RuinStructures.PIECE_TYPES.register(context.getModEventBus());
        RuinStructures.PLACEMENT_TYPES.register(context.getModEventBus());
        FastMagCreativeTab.TABS.register(context.getModEventBus());
        GruntRegistry.register(context.getModEventBus());
        TankRegistry.register(context.getModEventBus());
        PhantomRegistry.register(context.getModEventBus());
        GrenadeRegistry.register(context.getModEventBus());
        PatchItems.register(context.getModEventBus());
        MortarRegistry.register(context.getModEventBus());
        PatchCreativeTab.TABS.register(context.getModEventBus());
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ModConfigScreen.register(context));
        LOGGER.info("{} initialized", MOD_NAME);
    }
}