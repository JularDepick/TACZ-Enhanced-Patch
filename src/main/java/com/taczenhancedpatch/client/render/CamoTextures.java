package com.taczenhancedpatch.client.render;

import com.taczenhancedpatch.TACZEnhancedPatch;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/*
 * 遗迹生物的迷彩贴图。
 * 三种遗迹生物各有四套迷彩, 生成时按实体 UUID 取一套,
 * 因此同一只生物永远是同一套, 存档与重连后也不会变, 不需要额外同步字段。
 */
public final class CamoTextures {
    private static final String[] CAMO = {"camo_green", "camo_desert", "camo_urban", "camo_snow"};

    private CamoTextures() {
    }

    public static ResourceLocation of(String base, Entity entity) {
        int index = Math.floorMod(entity.getUUID().hashCode(), CAMO.length);
        return new ResourceLocation(TACZEnhancedPatch.MOD_ID + ":textures/entity/" + base + "_" + CAMO[index] + ".png");
    }
}
