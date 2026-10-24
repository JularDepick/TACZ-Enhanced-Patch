package com.taczenhancedpatch.item;

import com.taczenhancedpatch.TACZEnhancedPatch;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 补丁物品注册。
 * 遗迹指南针复用原版指南针的贴图与模型结构, 只把贴图整体改成绿色, 不新增渲染代码。
 */
public final class PatchItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, TACZEnhancedPatch.MOD_ID);

    public static final RegistryObject<Item> RUIN_COMPASS = ITEMS.register("ruin_compass",
            () -> new RuinCompassItem(new Item.Properties().stacksTo(1)));

    private PatchItems() {
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
