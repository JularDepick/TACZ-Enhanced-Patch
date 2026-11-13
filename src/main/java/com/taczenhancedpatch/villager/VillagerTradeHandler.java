package com.taczenhancedpatch.villager;

import com.taczenhancedpatch.TACZEnhancedPatch;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.TradeWithVillagerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 中立持械村民的交易结算。
 * 每完成一笔自定义商品交易就更新额度与库存, 库存清空即退化, 因此玩家买光最后一类物品会当场失去五折额度。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID)
public final class VillagerTradeHandler {
    private VillagerTradeHandler() {
    }

    /* 右键打开交易页之前重建商品, 防止原版升级或职业变更把自定义商品冲掉 */
    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getTarget() instanceof Villager villager && ArmedVillager.isArmed(villager)) {
            ArmedVillager.refreshOffers(villager);
        }
    }

    @SubscribeEvent
    public static void onTrade(TradeWithVillagerEvent event) {
        if (!(event.getAbstractVillager() instanceof Villager villager)) {
            return;
        }
        if (!ArmedVillager.isArmed(villager)) {
            return;
        }
        MerchantOffer offer = event.getMerchantOffer();
        if (!ArmedVillager.isOurs(offer)) {
            return;
        }
        ItemStack result = offer.getResult();
        String key = ArmedVillager.keyOf(result);
        if (key == null) {
            return;
        }
        ArmedVillager.recordSale(villager, key, result.getCount());
    }
}
