package com.taczenhancedpatch.villager;

import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.config.ServerConfig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 中立持械村民的生成判定。
 * 村民进入世界时判定一次, 结果写入持久化数据, 之后无论区块重载还是重新进入存档都不再重掷;
 * 未持械的村民永远不会转化为持械, 繁殖与治愈产生的新村民同样走本判定。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID)
public final class VillagerArmingHandler {
    private static final String TAG_ROLLED = "tacz_enhanced_patch_villager_rolled";

    private VillagerArmingHandler() {
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Villager villager)) {
            return;
        }
        if (ArmedVillager.isArmed(villager)) {
            /* 已持械的村民重新载入时修复手持并重建商品, 保证外观与交易页一致 */
            ArmedVillager.repairHand(villager);
            ArmedVillager.refreshOffers(villager);
            return;
        }
        if (villager.getPersistentData().getBoolean(TAG_ROLLED)) {
            return;
        }
        villager.getPersistentData().putBoolean(TAG_ROLLED, true);
        if (!ServerConfig.gunnerEnabled()) {
            return;
        }
        if (villager.getRandom().nextDouble() >= ServerConfig.villagerArmChance()) {
            return;
        }
        ArmedVillager.arm(villager, villager.getRandom());
    }

    /*
     * 持械村民死亡时把自建库存里的枪械与弹药一并掉出。
     * 村民的枪只挂在手上, 主手掉落概率被清零, 库存因此不会随装备掉落。
     */
    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Villager villager) || !ArmedVillager.isArmed(villager)) {
            return;
        }
        for (ItemStack stack : ArmedVillager.stockOf(villager)) {
            if (stack.isEmpty()) {
                continue;
            }
            ItemEntity drop = new ItemEntity(villager.level(), villager.getX(),
                    villager.getY() + 0.5D, villager.getZ(), stack.copy());
            drop.setDefaultPickUpDelay();
            event.getDrops().add(drop);
        }
    }

    /* 持械村民额外掉落经验, 原版村民本身不给经验 */
    @SubscribeEvent
    public static void onExperienceDrop(LivingExperienceDropEvent event) {
        if (event.getEntity() instanceof Villager villager && ArmedVillager.isArmed(villager)) {
            event.setDroppedExperience(event.getDroppedExperience() + ArmedVillager.VILLAGER_XP);
        }
    }
}
