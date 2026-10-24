package com.taczenhancedpatch.fastmag;

import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.integration.TaczApi;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * 快速扩容弹夹物品页。
 * 物品页里列出的仍然是上游的扩容弹夹物品, 每一条都在物品栈上打了等级标记并改写显示名,
 * 不新建物品对象, 也不占用新的物品 ID。
 */
public final class FastMagCreativeTab {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TACZEnhancedPatch.MOD_ID);

    public static final RegistryObject<CreativeModeTab> FAST_MAG_TAB = TABS.register("fast_extended_mag",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.tacz_enhanced_patch.fast_extended_mag"))
                    .withTabsBefore(new ResourceLocation("tacz", "extended_mag"))
                    .icon(FastMagCreativeTab::iconStack)
                    .displayItems((parameters, output) -> {
                        for (TaczApi.ExtendedMagEntry entry : TaczApi.extendedMags()) {
                            ItemStack stack = FastMag.create(entry);
                            if (!stack.isEmpty()) {
                                output.accept(stack);
                            }
                        }
                    })
                    .build());

    private FastMagCreativeTab() {
    }

    /* 图标取等级最高的那一件, 没有可用条目时退回空栈 */
    private static ItemStack iconStack() {
        ItemStack icon = ItemStack.EMPTY;
        for (TaczApi.ExtendedMagEntry entry : TaczApi.extendedMags()) {
            ItemStack stack = FastMag.create(entry);
            if (!stack.isEmpty() && (icon.isEmpty() || FastMag.level(stack) > FastMag.level(icon))) {
                icon = stack;
            }
        }
        return icon;
    }
}
