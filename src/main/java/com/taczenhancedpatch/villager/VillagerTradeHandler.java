package com.taczenhancedpatch.villager;

import com.taczenhancedpatch.TACZEnhancedPatch;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.TradeWithVillagerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * 中立持械村民的交易结算。
 * 每完成一笔自定义商品交易就更新额度与库存, 库存清空即退化, 因此玩家买光最后一类物品会当场失去五折额度。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID)
public final class VillagerTradeHandler {
    private static final String TAG_LEVEL = "tacz_enhanced_patch_villager_level";
    /* 交易后下一 tick 复核等级, 升级则关掉交易页, 让玩家重新打开 */
    private static final List<Pending> PENDING = new ArrayList<>();

    private record Pending(Villager villager, net.minecraft.server.level.ServerPlayer player, int ticks) {
    }

    private VillagerTradeHandler() {
    }

    /* 右键打开交易页之前重建商品, 防止原版升级或职业变更把自定义商品冲掉 */
    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getTarget() instanceof Villager villager && ArmedVillager.isArmed(villager)) {
            ArmedVillager.refreshOffers(villager);
            villager.getPersistentData().putInt(TAG_LEVEL, villager.getVillagerData().getLevel());
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
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            PENDING.add(new Pending(villager, player, 0));
        }
    }

    /* 升级会当场重建交易页, 客户端还停在旧列表上, 这里直接把界面关掉 */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) {
            return;
        }
        List<Pending> keep = new ArrayList<>();
        for (Pending pending : PENDING) {
            if (!check(pending)) {
                keep.add(new Pending(pending.villager(), pending.player(), pending.ticks() + 1));
            }
        }
        PENDING.clear();
        PENDING.addAll(keep);
    }

    private static boolean check(Pending pending) {
        Villager villager = pending.villager();
        if (villager.isRemoved() || pending.player().isRemoved() || pending.ticks() > 40) {
            return true;
        }
        int stored = villager.getPersistentData().getInt(TAG_LEVEL);
        int current = villager.getVillagerData().getLevel();
        /* 只有确实升级才关界面, 缺少记录时不误伤 */
        if (stored > 0 && current > stored) {
            villager.getPersistentData().putInt(TAG_LEVEL, current);
            pending.player().closeContainer();
            return true;
        }
        return false;
    }
}
