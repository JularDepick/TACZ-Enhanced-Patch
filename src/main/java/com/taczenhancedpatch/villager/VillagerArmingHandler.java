package com.taczenhancedpatch.villager;

import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.config.ServerConfig;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
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
        if (ArmedVillager.isArmed(villager) || villager.getPersistentData().getBoolean(TAG_ROLLED)) {
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

    /* 持械村民额外掉落经验, 原版村民本身不给经验 */
    @SubscribeEvent
    public static void onExperienceDrop(LivingExperienceDropEvent event) {
        if (event.getEntity() instanceof Villager villager && ArmedVillager.isArmed(villager)) {
            event.setDroppedExperience(event.getDroppedExperience() + ArmedVillager.VILLAGER_XP);
        }
    }
}
