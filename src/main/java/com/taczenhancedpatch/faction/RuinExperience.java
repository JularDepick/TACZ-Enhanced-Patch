package com.taczenhancedpatch.faction;

import com.taczenhancedpatch.TACZEnhancedPatch;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 夺旗经验的离线暂存。
 * 夺旗成功时不在线的玩家把经验记在存档数据里, 下次登录时补发并提示。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID)
public final class RuinExperience extends SavedData {
    private static final String DATA_NAME = "tacz_enhanced_patch_ruin_xp";
    private static final Map<UUID, Integer> PENDING = new LinkedHashMap<>();

    public static void add(ServerLevel level, UUID player, int amount) {
        PENDING.merge(player, amount, Integer::sum);
        data(level).setDirty();
    }

    public static int take(UUID player) {
        Integer value = PENDING.remove(player);
        return value == null ? 0 : value;
    }

    private static RuinExperience data(ServerLevel level) {
        return level.getServer().overworld().getDataStorage()
                .computeIfAbsent(RuinExperience::load, RuinExperience::new, DATA_NAME);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        /* 先确保存档数据已加载, 否则刚启动时静态表还是空的 */
        data(level);
        int pending = take(player.getUUID());
        if (pending <= 0) {
            return;
        }
        player.giveExperiencePoints(pending);
        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                "tacz_enhanced_patch.ruin.xp_pending", pending));
        data(level).setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Integer> entry : PENDING.entrySet()) {
            CompoundTag item = new CompoundTag();
            item.putUUID("player", entry.getKey());
            item.putInt("amount", entry.getValue());
            list.add(item);
        }
        tag.put("pending", list);
        return tag;
    }

    private static RuinExperience load(CompoundTag tag) {
        ListTag list = tag.getList("pending", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag item = list.getCompound(i);
            PENDING.put(item.getUUID("player"), item.getInt("amount"));
        }
        return new RuinExperience();
    }
}
