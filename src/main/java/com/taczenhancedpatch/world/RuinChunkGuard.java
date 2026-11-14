package com.taczenhancedpatch.world;

import com.taczenhancedpatch.TACZEnhancedPatch;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 遗迹生成的新区块限制。
 * 遗迹只在刚刚生成的新区块里落地, 已经有长期区块数据的区块不再生成遗迹;
 * 这样玩家反复开关遗迹生成也不会让遗迹出现在自己已经改造过的地形上。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID)
public final class RuinChunkGuard {
    /* 登记上限, 防止长期运行无限增长 */
    private static final int LIMIT = 262144;
    private static final Set<Long> EXISTING = ConcurrentHashMap.newKeySet();

    private RuinChunkGuard() {
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel().isClientSide() || event.isNewChunk()) {
            return;
        }
        if (EXISTING.size() >= LIMIT) {
            return;
        }
        EXISTING.add(event.getChunk().getPos().toLong());
    }

    /* 该区块是否在本次会话之前就已经存在 */
    public static boolean isExisting(ChunkPos pos) {
        return EXISTING.contains(pos.toLong());
    }

    /* 服务端停止时清空登记, 下次进存档重新按新区块判断 */
    public static void clear() {
        EXISTING.clear();
    }
}
