package com.taczenhancedpatch.faction;

import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.config.ServerConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.Containers;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 遗迹旗帜。
 * 旗帜是遗迹的视觉标识与领地中心: 夺旗成功前不可破坏, 爆炸也不会炸掉;
 * 夺旗成功后可破坏并掉落自身, 掉落的旗帜携带战绩, 悬浮提示逐行显示。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID)
public final class RuinBanner {
    /* 与原版染料顺序一致的十六面旗帜 */
    private static final Block[] BANNERS = {
            Blocks.WHITE_BANNER, Blocks.ORANGE_BANNER, Blocks.MAGENTA_BANNER, Blocks.LIGHT_BLUE_BANNER,
            Blocks.YELLOW_BANNER, Blocks.LIME_BANNER, Blocks.PINK_BANNER, Blocks.GRAY_BANNER,
            Blocks.LIGHT_GRAY_BANNER, Blocks.CYAN_BANNER, Blocks.PURPLE_BANNER, Blocks.BLUE_BANNER,
            Blocks.BROWN_BANNER, Blocks.GREEN_BANNER, Blocks.RED_BANNER, Blocks.BLACK_BANNER};
    /* 纪念品数据挂在物品 NBT 上 */
    public static final String TAG_STATS = "tacz_enhanced_patch_ruin_stats";
    private static final String TAG_LINES = "lines";
    private static final String TAG_KILLS = "kills";
    private static final String TAG_XP = "xp";
    private static final String TAG_NAME = "name";

    private RuinBanner() {
    }

    /* 从建筑底面中心向上找第一个空气格, 必要时补一块支撑再放旗帜 */
    public static boolean place(ServerLevel level, RuinSites.Site site, BlockPos center, int baseY, int height,
                                RandomSource random) {
        BlockPos spot = null;
        int limit = Math.min(level.getMaxBuildHeight() - 1, baseY + Math.max(1, height));
        for (int y = baseY; y <= limit; y++) {
            BlockPos candidate = new BlockPos(center.getX(), y, center.getZ());
            if (level.getBlockState(candidate).isAir()) {
                spot = candidate;
                break;
            }
        }
        /* 中心列被整柱占满时退回底面, 保证领地中心一定存在 */
        if (spot == null) {
            spot = new BlockPos(center.getX(), baseY, center.getZ());
        }
        BlockPos below = spot.below();
        if (!level.getBlockState(below).isSolidRender(level, below)) {
            level.setBlock(below, Blocks.STONE_BRICKS.defaultBlockState(), 2);
        }
        DyeColor color = DyeColor.byId(random.nextInt(BANNERS.length));
        BlockState state = BANNERS[color.getId()].defaultBlockState();
        level.setBlock(spot, state, 3);
        site.setBanner(spot, color.getSerializedName());
        return true;
    }

    @Nullable
    public static RuinSites.Site siteAt(ServerLevel level, BlockPos pos) {
        for (RuinSites.Site site : RuinSites.get(level).sites()) {
            if (pos.equals(site.banner())) {
                return site;
            }
        }
        return null;
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!(event.getState().getBlock() instanceof BannerBlock)) {
            return;
        }
        RuinSites.Site site = siteAt(level, event.getPos());
        if (site == null) {
            return;
        }
        event.setCanceled(true);
        if (!site.captured()) {
            return;
        }
        BlockPos pos = event.getPos();
        level.removeBlock(pos, false);
        ItemStack stack = new ItemStack(event.getState().getBlock());
        writeStats(stack, site);
        Containers.dropItemStack(level, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, stack);
    }

    /* 未夺旗的旗帜不会被爆炸炸掉, 保持领地中心稳定 */
    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        event.getAffectedBlocks().removeIf(pos -> {
            RuinSites.Site site = siteAt(level, pos);
            return site != null && !site.captured();
        });
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        CompoundTag tag = event.getItemStack().getTag();
        if (tag == null || !tag.contains(TAG_STATS, Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag stats = tag.getCompound(TAG_STATS);
        event.getToolTip().add(Component.translatable("tacz_enhanced_patch.ruin.banner_header")
                .withStyle(ChatFormatting.GOLD));
        ListTag lines = stats.getList(TAG_LINES, Tag.TAG_COMPOUND);
        for (int i = 0; i < lines.size(); i++) {
            CompoundTag line = lines.getCompound(i);
            event.getToolTip().add(Component.translatable("tacz_enhanced_patch.ruin.banner_line",
                    line.getString(TAG_NAME), line.getInt(TAG_KILLS), line.getInt(TAG_XP))
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    /* 把战绩摘要写进掉落的旗帜 */
    private static void writeStats(ItemStack stack, RuinSites.Site site) {
        RuinStats stats = site.stats();
        if (stats == null) {
            return;
        }
        CompoundTag root = new CompoundTag();
        ListTag lines = new ListTag();
        List<UUID> players = new ArrayList<>(stats.names().keySet());
        for (UUID player : players) {
            CompoundTag line = new CompoundTag();
            line.putString(TAG_NAME, stats.names().getOrDefault(player, player.toString()));
            line.putInt(TAG_KILLS, stats.killCount(player));
            line.putInt(TAG_XP, RuinLoot.experienceFor(stats, player));
            lines.add(line);
        }
        root.put(TAG_LINES, lines);
        stack.addTagElement(TAG_STATS, root);
        if (ServerConfig.debugEvents()) {
            TACZEnhancedPatch.LOGGER.info("[ruin] banner souvenir with {} entries from {}", lines.size(), site.origin());
        }
    }

    /* 供统计使用: 当前站点旗帜是否存在 */
    public static boolean present(RuinSites.Site site) {
        return site.banner() != null;
    }
}
