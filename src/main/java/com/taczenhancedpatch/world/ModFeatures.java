package com.taczenhancedpatch.world;

import com.taczenhancedpatch.TACZEnhancedPatch;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 世界生成特性注册表。
 */
public final class ModFeatures {
    public static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(ForgeRegistries.FEATURES, TACZEnhancedPatch.MOD_ID);

    public static final RegistryObject<Feature<?>> RUINED_OUTPOST =
            FEATURES.register("ruined_outpost", () -> new RuinedOutpostFeature(NoneFeatureConfiguration.CODEC));

    private ModFeatures() {
    }
}
