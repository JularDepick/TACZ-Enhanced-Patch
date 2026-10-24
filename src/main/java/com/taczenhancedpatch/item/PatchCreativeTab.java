package com.taczenhancedpatch.item;

import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.entity.GruntRegistry;
import com.taczenhancedpatch.entity.TankRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * 补丁物品页。
 * 集中存放本模组那些零散的, 不值得单独成页的物品, 目前是无限弹夹与两个生物的刷怪蛋;
 * 快速扩容弹夹有独立物品页, 不在此列。
 */
public final class PatchCreativeTab {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TACZEnhancedPatch.MOD_ID);

    public static final RegistryObject<CreativeModeTab> PATCH_TAB = TABS.register("patch",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.tacz_enhanced_patch.patch"))
                    .withTabsBefore(new ResourceLocation(TACZEnhancedPatch.MOD_ID + ":fast_extended_mag"))
                    .icon(PatchCreativeTab::iconStack)
                    .displayItems((parameters, output) -> {
                        ItemStack infinite = InfiniteMag.create();
                        if (!infinite.isEmpty()) {
                            output.accept(infinite);
                        }
                        output.accept(GruntRegistry.SPAWN_EGG.get());
                        output.accept(TankRegistry.SPAWN_EGG.get());
                    })
                    .build());

    private PatchCreativeTab() {
    }

    private static ItemStack iconStack() {
        ItemStack icon = InfiniteMag.create();
        return icon.isEmpty() ? new ItemStack(GruntRegistry.SPAWN_EGG.get()) : icon;
    }
}
