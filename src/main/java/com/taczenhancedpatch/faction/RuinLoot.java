package com.taczenhancedpatch.faction;

import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.config.ServerConfig;
import com.taczenhancedpatch.gunner.GunnerArsenal;
import com.taczenhancedpatch.integration.TaczApi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 夺旗战利品的换算与发放。
 * 弹药按数量折算, 武器按分档价值折算, 两者都受快照总量上限约束;
 * 经验按击杀数与伤害占比分配, 离线玩家由 RuinExperience 暂存。
 */
public final class RuinLoot {
    private static final int CHEST_SLOTS = 27;
    private static final int MAX_AMMO_PER_SLOT = 64;

    private RuinLoot() {
    }

    /* 武器价值取分档序号加一, 低阶一, 中阶二, 高阶三 */
    public static int weaponValue(@Nullable GunnerArsenal.Tier tier) {
        return tier == null ? 1 : tier.ordinal() + 1;
    }

    /* 单名玩家的经验, 夺旗发放与旗帜提示共用同一口径 */
    public static int experienceFor(RuinStats stats, UUID player) {
        float total = stats.totalDamage();
        float share = total <= 0.0F ? 0.0F : stats.damage().getOrDefault(player, 0.0F) / total;
        int kills = stats.killCount(player);
        double raw = ServerConfig.ruinXpPerKill() * kills + share * ServerConfig.ruinXpDamagePool();
        return (int) Math.round(raw * ServerConfig.ruinXpScale());
    }

    /* 在核心区生成战利品箱并填充 */
    public static void placeChest(ServerLevel level, RuinSites.Site site, RandomSource random) {
        BlockPos pos = findChestSpot(level, site, random);
        if (pos == null) {
            TACZEnhancedPatch.LOGGER.warn("[ruin] no chest spot at {}", site.origin());
            return;
        }
        level.setBlock(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH), 3);
        if (!(level.getBlockEntity(pos) instanceof ChestBlockEntity chest)) {
            return;
        }
        List<ItemStack> items = buildLoot(site, random);
        int slot = 0;
        for (ItemStack stack : items) {
            if (slot >= CHEST_SLOTS) {
                TACZEnhancedPatch.LOGGER.info("[ruin] loot overflow at {}, {} stacks dropped",
                        site.origin(), items.size() - CHEST_SLOTS);
                break;
            }
            chest.setItem(slot++, stack);
        }
    }

    /* 核心区内找一个无方块的地面位置 */
    @Nullable
    private static BlockPos findChestSpot(ServerLevel level, RuinSites.Site site, RandomSource random) {
        BlockPos banner = site.banner();
        if (banner == null) {
            return null;
        }
        for (int attempt = 0; attempt < 48; attempt++) {
            int radius = 1 + random.nextInt((int) RuinTerritory.CORE_RADIUS);
            int dx = random.nextInt(radius * 2 + 1) - radius;
            int dz = random.nextInt(radius * 2 + 1) - radius;
            BlockPos base = banner.offset(dx, 0, dz);
            for (int dy = 6; dy >= -6; dy--) {
                BlockPos candidate = base.offset(0, dy, 0);
                if (!level.getBlockState(candidate).isAir() || !level.getBlockState(candidate.above()).isAir()) {
                    continue;
                }
                BlockPos below = candidate.below();
                if (level.getBlockState(below).isSolidRender(level, below)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    /* 弹药按口径与武器按价值分别折算, 结果不超过快照上限 */
    private static List<ItemStack> buildLoot(RuinSites.Site site, RandomSource random) {
        List<ItemStack> items = new ArrayList<>();
        RuinStats stats = site.stats();
        if (stats == null) {
            return items;
        }
        double scale = ServerConfig.ruinLootScale();
        Map<String, Integer> killAmmo = new LinkedHashMap<>();
        for (RuinStats.Kill kill : stats.kills()) {
            for (Map.Entry<String, Integer> entry : kill.ammo().entrySet()) {
                killAmmo.merge(entry.getKey(), entry.getValue(), Integer::sum);
            }
        }
        for (Map.Entry<String, Integer> entry : killAmmo.entrySet()) {
            int snapshot = stats.snapshotAmmo().getOrDefault(entry.getKey(), 0);
            int amount = (int) Math.floor(scale * entry.getValue());
            amount = Math.min(amount, (int) Math.floor(scale * snapshot));
            if (amount <= 0) {
                continue;
            }
            ResourceLocation ammoId = ResourceLocation.tryParse(entry.getKey());
            if (ammoId == null) {
                continue;
            }
            int remaining = amount;
            while (remaining > 0 && items.size() < CHEST_SLOTS) {
                int stackSize = Math.min(remaining, MAX_AMMO_PER_SLOT);
                items.add(TaczApi.createAmmo(ammoId, stackSize));
                remaining -= stackSize;
            }
        }

        /* 武器按价值预算随机抽取, 单把价值不超过剩余预算 */
        int killValue = 0;
        for (RuinStats.Kill kill : stats.kills()) {
            killValue += kill.weaponValue();
        }
        int budget = (int) Math.floor(scale * Math.min(killValue, stats.snapshotWeaponValue()));
        List<RuinStats.Kill> pool = new ArrayList<>(stats.kills());
        while (budget > 0 && !pool.isEmpty() && items.size() < CHEST_SLOTS) {
            RuinStats.Kill kill = pool.remove(random.nextInt(pool.size()));
            if (kill.weapon().isEmpty() || kill.weaponValue() > budget) {
                continue;
            }
            ItemStack gun = kill.weapon().copy();
            TaczApi.clearDummyAmmo(gun);
            items.add(gun);
            budget -= kill.weaponValue();
        }
        return items;
    }

    /* 按战绩发放经验, 在线直接给, 离线暂存 */
    public static void grantExperience(ServerLevel level, RuinSites.Site site) {
        RuinStats stats = site.stats();
        if (stats == null) {
            return;
        }
        for (UUID player : stats.names().keySet()) {
            int xp = experienceFor(stats, player);
            if (xp <= 0) {
                continue;
            }
            ServerPlayer online = level.getServer().getPlayerList().getPlayer(player);
            if (online != null) {
                online.giveExperiencePoints(xp);
                online.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                        "tacz_enhanced_patch.ruin.xp", xp));
            } else {
                RuinExperience.add(level, player, xp);
            }
        }
    }
}
